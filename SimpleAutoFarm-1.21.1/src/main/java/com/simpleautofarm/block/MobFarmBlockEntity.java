package com.simpleautofarm.block;

import com.mojang.logging.LogUtils;
import com.simpleautofarm.SimpleAutoFarm;
import com.simpleautofarm.SimpleAutoFarmConfig;
import com.simpleautofarm.event.SyntheticDeathDrops;
import com.simpleautofarm.item.EntityCardItem;
import com.simpleautofarm.menu.MobFarmMenu;
import com.simpleautofarm.util.EntityPools;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.CombinedInvWrapper;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Simple Auto Mob Farm: the crop farm's machinery (FE energy, speed/efficiency/yield/fortune/
 * creative upgrades, deep stacks, auto-eject, AE2 output) fed with mob samples instead of seeds.
 *
 * <p>Samples are anything that names a mob: a spawn egg, or any modded mob container — a spawner
 * item carrying a stored block entity (ApothicSpawners and similar), an Ars Nouveau mob jar, and so
 * on. The lookup is a generic NBT scan ({@link #resolveSampleEntity(ItemStack)}), so modded containers
 * work without a compile-time dependency and the code ports unchanged to another Minecraft version.
 * Samples are only markers and are never consumed.
 *
 * <p>A finished batch spawns nothing into the world: the sample's entity type is instantiated as a
 * throwaway object and <b>killed</b> at the machine, so the whole death pipeline runs — vanilla loot
 * tables, global loot modifiers, {@code dropCustomDeathLoot} (other mods' extra drop tables, e.g.
 * Apotheosis affix gear) and mod death events. That is what makes modded drops appear instead of only
 * the vanilla table. The throwaway entity is never added to the level ({@code addFreshEntity} is never
 * called), so it does not tick, save or take chunk space.
 *
 * <p>The batch always completes, so the machine never stalls: the products are merged per item and
 * pushed into the main band and then the overflow page, and whatever still does not fit is simply
 * destroyed (usually only unstackable affix gear when both pages are full).
 */
public class MobFarmBlockEntity extends FarmBlockEntity {

    /** Overflow page: same size as the main product band. */
    public static final int OVERFLOW_SLOTS = OUTPUT_SLOTS;

    /** Attribute modifier id of the temporary luck granted by the fortune upgrade. */
    private static final ResourceLocation FORTUNE_LUCK_ID = SimpleAutoFarm.prefix("mob_farm_fortune");

    /** Draws per random-card sample: one more try when a draw really produced no items. */
    private static final int RANDOM_CARD_ATTEMPTS = 2;

    /** Upper bound of the sample resolution cache (nine input slots; the rest is hysteresis). */
    private static final int MAX_SAMPLE_CACHE = 64;
    /** NBT scan budget: modded container data is shallow, but never trust an item's tag to be sane. */
    private static final int SCAN_MAX_DEPTH = 6;
    private static final int SCAN_MAX_NODES = 512;
    /** Keys that may carry a mob id inside a container item's data. */
    private static final Pattern ENTITY_KEY = Pattern.compile("(?i).*(entity|mob|id|type).*");

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * {@code LivingEntity#dead} is the only field that keeps a died entity from dying again, and
     * NeoForge's {@code revive()} only clears the removal flag. One reflective handle (resolved once,
     * with a graceful fallback to a fresh instance) lets the machine recycle its throwaway mob.
     */
    @Nullable
    private static final Field DEAD_FIELD = resolveDeadField();

    /** One reused throwaway mob per egg type; bounded by the input row and dropped on unload. */
    private final Map<EntityType<?>, LivingEntity> deathCache =
            new LinkedHashMap<>(16, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<EntityType<?>, LivingEntity> eldest) {
                    return size() > INPUT_SLOTS;
                }
            };

    /** Sample item -> mob resolution cache, so a batch never scans the same stack twice. */
    private final Map<Integer, Optional<EntityType<?>>> sampleCache =
            new LinkedHashMap<>(16, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Integer, Optional<EntityType<?>>> eldest) {
                    return size() > MAX_SAMPLE_CACHE;
                }
            };

    /** The overflow page's storage; also the {@link #getOverflowOutput()} of this machine. */
    private final ItemStackHandler overflowHandler = createOutputHandler(OVERFLOW_SLOTS);

    /**
     * Who placed the machine. It acts as the killer while online, because modded loot tables (e.g.
     * Apotheosis) scale their drops with that player's world tier; it also survives being broken and
     * placed again, since it is saved with the block entity data of the dropped item.
     */
    @Nullable
    private UUID owner;

    public MobFarmBlockEntity(BlockPos pos, BlockState state) {
        super(SimpleAutoFarm.MOB_FARM_BLOCK_ENTITY.get(), pos, state);
    }

    /**
     * Whether the item stands for a mob the machine can kill: a spawn egg, or any container item
     * whose own NBT names a living entity (spawner items, Ars Nouveau mob jars, ...). Runs on the
     * client too (the menu validates cursor stacks), so it never needs a server-side registry.
     */
    @Override
    public boolean isValidSample(ItemStack stack) {
        // The random cards always fit (they draw their mob per batch); everything else must resolve.
        EntityCardItem.CardKind kind = EntityCardItem.kindOf(stack);
        if (kind != null && kind != EntityCardItem.CardKind.BOUND) {
            return true;
        }
        return resolveSampleEntity(stack) != null;
    }

    /** Killed mobs drop their items themselves, so the base class must not look for item products. */
    @Override
    protected List<ItemStack> computeProducts(Level level, ItemStack seed) {
        return List.of();
    }

    /** An empty product list still finishes the batch, which is what runs the deaths below. */
    @Override
    protected boolean produceWithoutItems() {
        return true;
    }

    /**
     * Kills one throwaway mob per occupied sample slot, then stores the drops. The batch always
     * completes: items go to the main band, then the overflow page, and the rest is destroyed.
     */
    @Override
    protected void onBatchProduced(Level level, int planted) {
        if (!(level instanceof ServerLevel serverLevel) || planted <= 0) {
            return;
        }
        // One killer decision per batch: if the death path fails once, the rest of this batch uses
        // the loot-table path instead of failing nine times. The next batch tries again.
        Player killer;
        try {
            killer = resolveKiller(serverLevel);
        } catch (RuntimeException | LinkageError failure) {
            // A protection mod may refuse the fake player; the machine then just rolls the loot tables, so
            // the basic drops keep coming instead of the whole batch failing.
            LOGGER.debug("Mob farm at {}: killer unavailable ({})", worldPosition, failure.toString());
            killer = null;
        }
        DamageSource source = killer == null ? null : serverLevel.damageSources().playerAttack(killer);
        // Mutable so a failed synthetic death switches the rest of this batch to the plain roll.
        boolean[] simulate = {source != null && SimpleAutoFarmConfig.MOB_FARM_SIMULATE_DEATH.get()};
        if (killer == null) {
            LOGGER.debug("Mob farm at {}: no player and no fake player, rolling the loot table without a killer",
                    worldPosition);
        }
        List<ItemStack> drops = new ArrayList<>();
        IItemHandler input = getInputHandler();
        for (int i = 0; i < INPUT_SLOTS; i++) {
            ItemStack sample = input.getStackInSlot(i);
            EntityCardItem.CardKind kind = EntityCardItem.kindOf(sample);
            if (kind != null && kind != EntityCardItem.CardKind.BOUND) {
                // A random card draws a fresh mob of its category on every batch; nothing is stored.
                drops.addAll(randomCardDrops(serverLevel, kind, killer, source, simulate));
                continue;
            }
            EntityType<?> type = resolveSampleEntity(sample);
            if (type != null) {
                drops.addAll(produceOnce(serverLevel, type, killer, source, simulate));
            }
        }
        if (!drops.isEmpty()) {
            storeDrops(level, merge(drops));
        }
    }

    /**
     * Draws a mob for a random card and, when that draw really produced nothing (a lucky roll on a
     * low-chance table), draws again a couple of times. The loot-table filter in {@link EntityPools}
     * already removes mobs without any table, so this only covers genuinely unlucky rolls.
     */
    private List<ItemStack> randomCardDrops(ServerLevel level, EntityCardItem.CardKind kind,
                                            @Nullable Player killer, @Nullable DamageSource source,
                                            boolean[] simulate) {
        EntityType<?> last = null;
        for (int attempt = 1; attempt <= RANDOM_CARD_ATTEMPTS + 1; attempt++) {
            EntityType<?> type = EntityPools.pick(kind, level);
            if (type == null) {
                return List.of();
            }
            last = type;
            List<ItemStack> drops = produceOnce(level, type, killer, source, simulate);
            if (!drops.isEmpty()) {
                return drops;
            }
            LOGGER.debug("Random card roll produced nothing (kind={}, type={}, attempt={}/{})",
                    kind, type, attempt, RANDOM_CARD_ATTEMPTS);
        }
        if (last != null) {
            // Nothing at all, not even the expected drop: remember the mob and try one more for this batch.
            EntityPools.markNoDrops(last);
            EntityType<?> replacement = EntityPools.pick(kind, level);
            if (replacement != null && replacement != last) {
                return produceOnce(level, replacement, killer, source, simulate);
            }
        }
        return List.of();
    }

    /** Kills one synthetic mob of {@code type} and returns its drops; never throws. */
    private List<ItemStack> dropsOf(ServerLevel level, EntityType<?> type, @Nullable Player killer,
                                    @Nullable DamageSource source, boolean[] simulate) {
        LivingEntity mob = obtainMob(level, type);
        if (mob == null) {
            return List.of();
        }
        if (simulate[0] && source != null) {
            try {
                return simulateDeath(level, mob, killer, source);
            } catch (Exception | LinkageError failure) {
                // A mod may deny the synthetic death (fake-player blocking and the like). Give up on
                // the death path for the rest of this batch only; the next batch retries.
                LOGGER.debug("Mob farm at {}: synthetic death failed ({}), falling back to the loot table",
                        worldPosition, failure.toString());
                simulate[0] = false;
            }
        }
        try {
            return rollLootTable(level, mob, killer);
        } catch (Exception | LinkageError failure) {
            LOGGER.debug("Mob farm at {}: loot table roll failed ({})", worldPosition, failure.toString());
            return List.of();
        }
    }

    /** Merges equal drops, scales them with the yield / fortune upgrades and stores what fits. */
    private void storeDrops(Level level, List<ItemStack> drops) {
        IItemHandler output = getOutputHandler();
        IItemHandler overflow = getOverflowHandler();
        for (ItemStack drop : drops) {
            long count = (long) drop.getCount() * getProductionMultiplier() * rollFortune(level);
            int stackCount = count > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) count;
            ItemStack remainder = ItemHandlerHelper.insertItemStacked(output, drop.copyWithCount(stackCount), false);
            if (!remainder.isEmpty()) {
                remainder = ItemHandlerHelper.insertItemStacked(overflow, remainder, false);
            }
            // Whatever still does not fit is discarded on purpose: the machine keeps producing and
            // never stalls, only the excess of this batch is lost.
        }
    }

    // ---------- sample resolution ----------

    /**
     * The mob a sample stands for: a spawn egg directly, otherwise the entity id found in the item's
     * own NBT. Generic NBT scan so modded containers (spawner items carrying a whole block entity,
     * Ars Nouveau mob jars, ...) work without a compile-time dependency.
     */
    @Nullable
    public EntityType<?> resolveSampleEntity(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        LOGGER.info("sample resolve: item={} card={}", BuiltInRegistries.ITEM.getKey(stack.getItem()),
                EntityCardItem.kindOf(stack));
        // An activated entity card is a sample in itself; a blank, unfinished or random card is not.
        // The random cards are drawn per batch in onBatchProduced, so nothing random happens here.
        EntityCardItem.CardKind kind = EntityCardItem.kindOf(stack);
        if (kind != null) {
            return kind == EntityCardItem.CardKind.BOUND && EntityCardItem.isActivated(stack)
                    ? EntityCardItem.getActiveSampleType(stack) : null;
        }
        if (stack.getItem() instanceof SpawnEggItem egg) {
            EntityType<?> type;
            try {
                type = egg.getType(stack);
            } catch (Exception exception) {
                LOGGER.info("sample resolve: spawn egg getType threw {}", exception.toString());
                return null;
            }
            LOGGER.info("sample resolve: spawn egg type={} usable={}", type, isUsableSample(type));
            return isUsableSample(type) ? type : null;
        }
        int key = ItemStack.hashItemAndComponents(stack);
        Optional<EntityType<?>> cached = this.sampleCache.get(key);
        if (cached != null) {
            return cached.orElse(null);
        }
        EntityType<?> resolved = scanForEntityType(stack);
        LOGGER.info("sample resolve: scan result for {} => {}", BuiltInRegistries.ITEM.getKey(stack.getItem()),
                resolved);
        this.sampleCache.put(key, Optional.ofNullable(resolved));
        return resolved;
    }

    /** Dumps the whole stack (item + components) and walks it for an entity id. */
    @Nullable
    private EntityType<?> scanForEntityType(ItemStack stack) {
        CompoundTag data;
        try {
            HolderLookup.Provider provider = this.level != null ? this.level.registryAccess() : RegistryAccess.EMPTY;
            Tag encoded = ItemStack.CODEC
                    .encodeStart(provider.createSerializationContext(NbtOps.INSTANCE), stack)
                    .getOrThrow();
            if (!(encoded instanceof CompoundTag compound)) {
                return null;
            }
            data = compound;
        } catch (Exception | LinkageError failure) {
            // A component that cannot be encoded is simply not a sample.
            LOGGER.info("sample resolve: encode failed for {}: {}", BuiltInRegistries.ITEM.getKey(stack.getItem()),
                    failure.toString());
            return null;
        }
        Scan scan = new Scan();
        walkValue(data, null, 0, scan);
        return scan.best;
    }

    /** Depth- and node-bounded walk; only string leaves under an entity-ish key are inspected. */
    private static void walkValue(@Nullable Tag tag, @Nullable String key, int depth, Scan scan) {
        if (tag == null || depth > SCAN_MAX_DEPTH) {
            return;
        }
        if (tag instanceof StringTag string) {
            if (key != null) {
                scan.offer(key, string.getAsString());
            }
            return;
        }
        if (tag instanceof CompoundTag compound) {
            for (String childKey : compound.getAllKeys()) {
                if (++scan.visited > SCAN_MAX_NODES) {
                    return;
                }
                walkValue(compound.get(childKey), childKey, depth + 1, scan);
            }
            return;
        }
        if (tag instanceof ListTag list) {
            for (Tag child : list) {
                if (++scan.visited > SCAN_MAX_NODES) {
                    return;
                }
                walkValue(child, key, depth + 1, scan);
            }
        }
    }

    /** Keeps the best scoring entity id seen during one scan. */
    private static final class Scan {

        private int visited;
        private int seen;
        private int bestScore;
        @Nullable
        private EntityType<?> best;

        void offer(String key, String value) {
            if (this.seen < 5) {
                this.seen++;
                LOGGER.info("sample resolve: candidate key={} value={}", key, value);
            }
            int score = scoreOf(key);
            if (score <= 0 || score < this.bestScore || (score == this.bestScore && this.best != null)) {
                return;
            }
            ResourceLocation id = ResourceLocation.tryParse(value);
            if (id == null) {
                return;
            }
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
            if (!isUsableSample(type)) {
                return;
            }
            this.best = type;
            this.bestScore = score;
        }

        /** {@code EntityId} beats {@code entity} beats any other entity-ish key. */
        private static int scoreOf(String key) {
            if (!ENTITY_KEY.matcher(key).matches()) {
                return 0;
            }
            String lower = key.toLowerCase(Locale.ROOT);
            if (lower.contains("entityid")) {
                return 3;
            }
            if (lower.contains("entity")) {
                return 2;
            }
            return 1;
        }
    }

    /** Rejects entities that are not mobs (players, markers, armour stands, non-living types). */
    private static boolean isUsableSample(@Nullable EntityType<?> type) {
        if (type == null || type == EntityType.PLAYER) {
            return false;
        }
        // 1.21.1's EntityType#getBaseClass() answers Entity.class for every type (the per-type base class is
        // gone), so a LivingEntity check rejected every mob. DefaultAttributes.hasSupplier is the reliable
        // "this type is a living entity" signal and is what the entity pools use too.
        if (!net.minecraft.world.entity.ai.attributes.DefaultAttributes.hasSupplier(type)) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        if (id == null) {
            return false;
        }
        String path = id.getPath();
        return !path.equals("marker") && !path.equals("armor_stand");
    }

    // ---------- synthetic death ----------

    /**
     * The reusable mob of this egg type: created once, then revived by clearing the death flag and
     * the combat/pose state. A mob that cannot be revived is replaced by a fresh one.
     */
    @Nullable
    private LivingEntity obtainMob(ServerLevel level, EntityType<?> type) {
        LivingEntity cached = this.deathCache.get(type);
        if (cached != null && cached.level() == level && revive(cached)) {
            return cached;
        }
        this.deathCache.remove(type);
        LivingEntity fresh;
        try {
            Entity entity = type.create(level);
            if (!(entity instanceof LivingEntity living)) {
                return null;
            }
            fresh = living;
        } catch (Exception exception) {
            return null;
        }
        prepareMob(level, fresh);
        this.deathCache.put(type, fresh);
        return fresh;
    }

    /** One-time setup: keep the throwaway mob out of sight and let spawn-time data attach. */
    private void prepareMob(ServerLevel level, LivingEntity mob) {
        mob.setSilent(true);
        mob.setInvisible(true);
        mob.setNoGravity(true);
        if (mob instanceof Mob spawned) {
            try {
                // Lets spawn-time data attach (equipment, modded bonus loot tables, Apotheosis affixes).
                spawned.finalizeSpawn(level, level.getCurrentDifficultyAt(worldPosition),
                        MobSpawnType.MOB_SUMMONED, null);
            } catch (Exception ignored) {
                // Some entity types need extra spawn context; their default loot is still rolled.
            }
        }
    }

    /** Puts a died mob back into a usable state without constructing a new one. */
    private static boolean revive(LivingEntity mob) {
        if (DEAD_FIELD == null) {
            return false;
        }
        try {
            DEAD_FIELD.setBoolean(mob, false);
        } catch (IllegalAccessException exception) {
            return false;
        }
        mob.deathTime = 0;
        mob.hurtTime = 0;
        mob.invulnerableTime = 0;
        mob.setPose(Pose.STANDING);
        mob.setHealth(mob.getMaxHealth());
        mob.captureDrops(null);
        mob.setLastHurtByMob(null);
        mob.setLastHurtByPlayer(null);
        return true;
    }

    @Nullable
    private static Field resolveDeadField() {
        try {
            Field field = LivingEntity.class.getDeclaredField("dead");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return null;
        }
    }

    /**
     * One sample's payout for this batch: the real death drops first, then the mob's expected drop when
     * the death produced nothing at all, and finally an empty batch for a mob that truly pays nothing.
     * Directed samples (spawn eggs, modded containers, bound cards) use exactly the same rule as the
     * random cards, so no sample ever wastes a batch on a plain bad roll.
     */
    private List<ItemStack> produceOnce(ServerLevel level, EntityType<?> type, @Nullable Player killer,
                                        @Nullable DamageSource source, boolean[] simulate) {
        List<ItemStack> drops = dropsOf(level, type, killer, source, simulate);
        if (!drops.isEmpty()) {
            return drops;
        }
        List<ItemStack> fallback = EntityPools.fallbackDrops(type, level);
        if (!fallback.isEmpty()) {
            LOGGER.debug("Random card fallback used for {}", type);
            return fallback;
        }
        LOGGER.debug("Mob farm at {}: {} drops nothing at all", worldPosition, type);
        return List.of();
    }

    /**
     * Kills the throwaway mob at the machine. Everything a real death does happens here except that
     * the mob is not part of the level: {@code die} runs the loot table with the killer's luck, the
     * global loot modifiers, {@code dropCustomDeathLoot} (modded extra drops) and the death/drops
     * events. Its items are captured through {@link SyntheticDeathDrops}.
     */
    private List<ItemStack> simulateDeath(ServerLevel level, LivingEntity mob, Player killer, DamageSource source) {
        mob.moveTo(Vec3.atCenterOf(worldPosition), 0.0F, 0.0F);
        mob.setHealth(mob.getMaxHealth());
        mob.setLastHurtByMob(killer);
        // setLastHurtByPlayer stamps itself with the entity's age, so make it look "recently hit":
        // the loot pipeline only treats this as a player kill while that timestamp is non-zero.
        mob.tickCount = 1;
        mob.setLastHurtByPlayer(killer);

        AttributeInstance luck = killer.getAttribute(Attributes.LUCK);
        if (luck != null) {
            // The fortune upgrade feeds the same luck the loot context reads for a real kill.
            luck.addTransientModifier(new AttributeModifier(FORTUNE_LUCK_ID, rollFortune(level),
                    AttributeModifier.Operation.ADD_VALUE));
        }
        Set<Entity> before = newSnapshot(level);
        List<ItemStack> dropped = new ArrayList<>();
        SyntheticDeathDrops.begin();
        SyntheticDeathDrops.mark(mob);
        try {
            mob.die(source);
        } finally {
            dropped.addAll(SyntheticDeathDrops.end());
            if (luck != null) {
                luck.removeModifier(FORTUNE_LUCK_ID);
            }
            mob.captureDrops(null);
            clearEquipment(mob);
        }
        // A mod may spawn death drops straight into the level instead of using the drops event;
        // pick those up too, and remove the experience orbs (this machine stores items only).
        dropped.addAll(collectLevelDrops(level, before));
        return dropped;
    }

    /** Keeps a reused mob from carrying half-dropped gear into the next batch. */
    private static void clearEquipment(LivingEntity mob) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (!mob.getItemBySlot(slot).isEmpty()) {
                mob.setItemSlot(slot, ItemStack.EMPTY);
            }
        }
    }

    /** The vanilla loot-table roll, kept as the fallback path and for the disabled simulation. */
    /**
     * The vanilla loot-table roll. Also the fallback when no killer is available: the killer-only
     * context parameters are then left out entirely, so plain drops still appear (only the
     * {@code killed_by_player} style conditions are lost).
     */
    private List<ItemStack> rollLootTable(ServerLevel level, LivingEntity mob, @Nullable Player killer) {
        ResourceKey<LootTable> key = mob.getLootTable();
        LootTable lootTable = level.getServer().reloadableRegistries().getLootTable(key);
        if (lootTable == LootTable.EMPTY) {
            return List.of();
        }
        DamageSource source = killer == null
                ? level.damageSources().generic()
                : level.damageSources().playerAttack(killer);
        LootParams.Builder builder = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, mob)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(worldPosition))
                // DAMAGE_SOURCE is required by the ENTITY parameter set, so it is always present.
                .withParameter(LootContextParams.DAMAGE_SOURCE, source)
                .withLuck(rollFortune(level));
        if (killer != null) {
            builder.withOptionalParameter(LootContextParams.ATTACKING_ENTITY, killer)
                    .withOptionalParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, killer)
                    .withOptionalParameter(LootContextParams.LAST_DAMAGE_PLAYER, killer);
        }
        return lootTable.getRandomItems(builder.create(LootContextParamSets.ENTITY));
    }

    /** Nearest online player in the level, regardless of distance; {@code null} when nobody is on. */
    @Nullable
    private Player nearestPlayer(ServerLevel level) {
        Vec3 center = Vec3.atCenterOf(worldPosition);
        Player nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (ServerPlayer player : level.players()) {
            double distance = player.distanceToSqr(center);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = player;
            }
        }
        return nearest;
    }

    /**
     * Who "kills" the synthetic mobs, in order: the player who placed the machine (while online,
     * wherever they are — modded loot uses their world tier), any other online player, then the
     * shared fake player while the config allows it, and finally {@code null}. The two real-player
     * steps can be switched off, because a real kill also counts for that player's statistics and
     * advancements. A {@code null} killer is not an error: the machine then rolls the plain loot
     * table, so basic drops keep coming. The failure is never cached — the next batch tries the full
     * chain again.
     */
    @Nullable
    private Player resolveKiller(ServerLevel level) {
        if (SimpleAutoFarmConfig.MOB_FARM_USE_REAL_PLAYER_KILLER.get()) {
            UUID recorded = this.owner;
            if (recorded != null) {
                ServerPlayer online = level.getServer().getPlayerList().getPlayer(recorded);
                if (online != null) {
                    return online;
                }
            }
            Player nearest = nearestPlayer(level);
            if (nearest != null) {
                return nearest;
            }
        }
        if (SimpleAutoFarmConfig.MOB_FARM_ALLOW_FAKE_PLAYER.get()) {
            try {
                return FakePlayerFactory.getMinecraft(level);
            } catch (Exception | LinkageError failure) {
                // Some mods (FTB Chunks and friends) block fake players outright.
                LOGGER.debug("Mob farm at {}: fake player unavailable ({})", worldPosition, failure.toString());
            }
        }
        return null;
    }

    /** Records the placer as the machine's owner, unless the placed item already carried one. */
    public void setOwnerIfAbsent(Player player) {
        if (this.owner == null) {
            this.owner = player.getUUID();
            setChanged();
        }
    }

    /** UUID of the player who placed this machine, or {@code null} for old / command-placed ones. */
    @Nullable
    public UUID getOwner() {
        return this.owner;
    }

    /** Entities (and drops) around the machine that the synthetic death may have touched. */
    private AABB dropArea() {
        return new AABB(worldPosition).inflate(2.0);
    }

    private Set<Entity> newSnapshot(ServerLevel level) {
        Set<Entity> found = Collections.newSetFromMap(new IdentityHashMap<>());
        found.addAll(level.getEntitiesOfClass(Entity.class, dropArea()));
        return found;
    }

    /** Picks up drops a mod added to the level directly, and removes the synthetic experience orbs. */
    private List<ItemStack> collectLevelDrops(ServerLevel level, Set<Entity> before) {
        List<ItemStack> collected = new ArrayList<>();
        for (Entity entity : level.getEntitiesOfClass(Entity.class, dropArea())) {
            if (before.contains(entity)) {
                continue;
            }
            if (entity instanceof ItemEntity item) {
                if (!item.getItem().isEmpty()) {
                    collected.add(item.getItem().copy());
                }
                entity.discard();
            } else if (entity instanceof ExperienceOrb) {
                entity.discard();
            }
        }
        return collected;
    }

    /** Merges equal stacks so one mob cannot fill a slot row with dozens of single items. */
    private static List<ItemStack> merge(List<ItemStack> stacks) {
        List<ItemStack> merged = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) {
                continue;
            }
            boolean added = false;
            for (ItemStack existing : merged) {
                if (ItemStack.isSameItemSameComponents(existing, stack)) {
                    existing.grow(stack.getCount());
                    added = true;
                    break;
                }
            }
            if (!added) {
                merged.add(stack.copy());
            }
        }
        return merged;
    }

    // ---------- overflow page ----------

    /** The second product page behind the main output band. */
    @Override
    protected IItemHandler getOverflowOutput() {
        return overflowHandler;
    }

    public IItemHandler getOverflowHandler() {
        return overflowHandler;
    }

    // ---------- lifecycle ----------

    @Override
    public void setRemoved() {
        this.deathCache.clear();
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        this.deathCache.clear();
        super.onChunkUnloaded();
    }

    // ---------- MenuProvider ----------

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.simpleautofarm.mob_farm");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new MobFarmMenu(containerId, playerInventory, this.worldPosition);
    }

    // ---------- persistence ----------

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Overflow")) {
            overflowHandler.deserializeNBT(registries, tag.getCompound("Overflow"));
        }
        if (tag.hasUUID("Owner")) {
            UUID stored = tag.getUUID("Owner");
            // getUUID answers a zero UUID for a broken tag; treat that as "no owner".
            if (stored.getMostSignificantBits() != 0L || stored.getLeastSignificantBits() != 0L) {
                this.owner = stored;
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Overflow", overflowHandler.serializeNBT(registries));
        if (this.owner != null) {
            tag.putUUID("Owner", this.owner);
        }
    }

    @Override
    public void dropContents(Level level) {
        super.dropContents(level);
        for (int i = 0; i < OVERFLOW_SLOTS; i++) {
            ItemStack stack = overflowHandler.getStackInSlot(i);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), stack);
            }
        }
    }
}
