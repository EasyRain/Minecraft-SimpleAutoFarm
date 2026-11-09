package com.simpleautofarm.block;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.simpleautofarm.SimpleAutoFarm;
import com.simpleautofarm.SimpleAutoFarmConfig;
import com.simpleautofarm.event.SyntheticDeathDrops;
import com.simpleautofarm.item.EntityCardItem;
import com.simpleautofarm.menu.MobFarmMenu;
import com.simpleautofarm.util.EntityPools;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.CombinedInvWrapper;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Simple Auto Mob Farm: the crop farm's machinery (FE energy, speed/efficiency/yield/fortune/creative
 * upgrades, deep stacks, auto-eject, AE2 output) fed with mob samples instead of seeds.
 *
 * <p>Samples are anything that names a mob: a spawn egg, or any modded mob container — a spawner item
 * carrying a stored block entity (ApothicSpawners and similar), an Ars Nouveau mob jar, and so on. The
 * lookup is a generic NBT scan ({@link #resolveSampleEntity(ItemStack)}), so modded containers work
 * without a compile-time dependency. Samples are only markers and are never consumed.
 *
 * <p>A finished batch spawns nothing into the world: the sample's entity type is instantiated as a
 * throwaway object and <b>killed</b> at the machine, so the whole death pipeline runs — vanilla loot
 * tables, global loot modifiers, {@code dropCustomDeathLoot} (other mods' extra drop tables) and mod
 * death events. The throwaway entity is never added to the level, so it does not tick, save or take
 * chunk space. Its items are captured through {@link SyntheticDeathDrops}.
 *
 * <p>The batch always completes, so the machine never stalls: products are merged per item and pushed
 * into the main band and then the overflow page, and whatever still does not fit is destroyed.
 */
public class MobFarmBlockEntity extends FarmBlockEntity {

    /** Overflow page: same size as the main product band. */
    public static final int OVERFLOW_SLOTS = OUTPUT_SLOTS;

    /** Upper bound of the sample resolution cache. */
    private static final int MAX_SAMPLE_CACHE = 64;
    /** NBT scan budget: modded container data is shallow, but never trust an item's tag to be sane. */
    private static final int SCAN_MAX_DEPTH = 6;
    private static final int SCAN_MAX_NODES = 512;
    /** Keys that may carry a mob id inside a container item's data. */
    private static final Pattern ENTITY_KEY = Pattern.compile("(?i).*(entity|mob|id|type).*");

    private static final Logger LOGGER = LogUtils.getLogger();

    /** The overflow page's storage; also the {@link #getOverflowOutput()} of this machine. */
    private final ItemStackHandler overflowHandler = createOutputHandler(OVERFLOW_SLOTS);

    /**
     * Who placed the machine; acts as the killer when the real-player killer is enabled. Saved with the
     * block entity data of the dropped item, so breaking and replacing keeps it.
     */
    @Nullable
    private UUID owner;

    /** Sample item -> mob resolution cache; positive and negative answers are both kept. */
    private final Map<Integer, Optional<EntityType<?>>> sampleCache =
            new LinkedHashMap<>(16, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Integer, Optional<EntityType<?>>> eldest) {
                    return size() > MAX_SAMPLE_CACHE;
                }
            };

    public MobFarmBlockEntity(BlockPos pos, BlockState state) {
        super(SimpleAutoFarm.MOB_FARM_BLOCK_ENTITY.get(), pos, state);
    }

    /**
     * Whether the item stands for a mob the machine can kill: a spawn egg, or any container item whose
     * own NBT names a living entity (spawner items, mob jars, ...). Runs on the client too, so it never
     * needs a server-side registry.
     */
    @Override
    public boolean isValidSample(ItemStack stack) {
        // The random cards always fit (they draw their mob per batch), everything else must resolve.
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
        // One killer decision per batch: if the death path fails once, the rest of this batch uses the
        // loot-table path instead of failing nine times. The next batch tries again.
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
                drops.addAll(randomCardDrops(serverLevel, kind, killer, source, simulate));
                continue;
            }
            EntityType<?> type = resolveSampleEntity(sample);
            if (type != null) {
                drops.addAll(produceOnce(serverLevel, type, killer, source, simulate));
            }
        }
        // A last sweep catches experience orbs that appeared after the individual deaths.
        for (Entity entity : serverLevel.getEntitiesOfClass(Entity.class, dropArea())) {
            if (entity instanceof ExperienceOrb orb) {
                orb.discard();
            }
        }
        if (!drops.isEmpty()) {
            storeDrops(level, merge(drops));
        }
    }

    /**
     * Draws a mob for a random card; when a draw produced nothing at all it draws once more, so only a
     * genuinely unlucky roll leaves the batch empty.
     */
    private List<ItemStack> randomCardDrops(ServerLevel level, EntityCardItem.CardKind kind,
                                            @Nullable Player killer, @Nullable DamageSource source,
                                            boolean[] simulate) {
        EntityType<?> last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            EntityType<?> type = EntityPools.pick(kind, level);
            if (type == null) {
                return List.of();
            }
            last = type;
            List<ItemStack> drops = produceOnce(level, type, killer, source, simulate);
            if (!drops.isEmpty()) {
                return drops;
            }
            LOGGER.debug("Random card roll produced nothing (kind={}, type={}, attempt={})", kind, type, attempt);
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

    /** Merges equal drops, scales them with the yield / fortune upgrades and stores what fits. */
    private void storeDrops(Level level, List<ItemStack> drops) {
        IItemHandler output = getOutputHandler();
        for (ItemStack drop : drops) {
            long count = (long) drop.getCount() * getProductionMultiplier() * rollFortune(level);
            int stackCount = count > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) count;
            ItemStack remainder = ItemHandlerHelper.insertItemStacked(output, drop.copyWithCount(stackCount), false);
            if (!remainder.isEmpty()) {
                remainder = ItemHandlerHelper.insertItemStacked(this.overflowHandler, remainder, false);
            }
            // Whatever still does not fit is discarded on purpose: the machine keeps producing and never
            // stalls, only the excess of this batch is lost.
        }
    }

    // ---------- sample resolution ----------

    /**
     * The mob a sample stands for: a spawn egg directly, otherwise the entity id found in the item's own
     * NBT. Generic NBT scan so modded containers (spawner items carrying a whole block entity, mob jars,
     * ...) work without a compile-time dependency.
     */
    @Nullable
    public EntityType<?> resolveSampleEntity(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        // An activated entity card is a sample in itself; blank, unfinished and random cards are not.
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
            } catch (RuntimeException exception) {
                return null;
            }
            return isUsableSample(type) ? type : null;
        }
        int key = ItemStack.hashItemAndComponents(stack);
        Optional<EntityType<?>> cached = this.sampleCache.get(key);
        if (cached != null) {
            return cached.orElse(null);
        }
        EntityType<?> resolved = scanForEntityType(stack);
        this.sampleCache.put(key, Optional.ofNullable(resolved));
        return resolved;
    }

    /**
     * Scans the item's own components for an entity id. 26.1.2 encodes each component with its own
     * codec, which keeps the scan independent of the registry-aware item codec (the container
     * components this is after are plain NBT and encode without any registry lookup).
     */
    @Nullable
    private static EntityType<?> scanForEntityType(ItemStack stack) {
        Scan scan = new Scan();
        for (DataComponentType<?> componentType : stack.getComponents().keySet()) {
            encodeAndWalk(stack, componentType, scan);
        }
        return scan.best;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void encodeAndWalk(ItemStack stack, DataComponentType<?> componentType, Scan scan) {
        try {
            Object value = stack.get((DataComponentType) componentType);
            if (value == null) {
                return;
            }
            Tag encoded = (Tag) ((Codec) componentType.codec()).encodeStart(NbtOps.INSTANCE, value).getOrThrow();
            walkValue(encoded, null, 0, scan);
        } catch (RuntimeException | LinkageError ignored) {
            // A component that cannot be encoded is simply not a sample.
        }
    }

    /** Depth- and node-bounded walk; only string leaves under an entity-ish key are inspected. */
    private static void walkValue(@Nullable Tag tag, @Nullable String key, int depth, Scan scan) {
        if (tag == null || depth > SCAN_MAX_DEPTH) {
            return;
        }
        if (tag instanceof StringTag string) {
            if (key != null) {
                scan.offer(key, string.value());
            }
            return;
        }
        if (tag instanceof CompoundTag compound) {
            for (String childKey : compound.keySet()) {
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
        private int bestScore;
        @Nullable
        private EntityType<?> best;

        void offer(String key, String value) {
            int score = scoreOf(key);
            if (score <= 0 || score < this.bestScore || (score == this.bestScore && this.best != null)) {
                return;
            }
            Identifier id = Identifier.tryParse(value);
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
        // EntityType#getBaseClass() answers Entity.class for every type on 26.1.2, so a LivingEntity check
        // here rejected every mob. DefaultAttributes.hasSupplier is the reliable "living entity" signal,
        // the same one the entity pools use.
        if (!net.minecraft.world.entity.ai.attributes.DefaultAttributes.hasSupplier(type)) {
            return false;
        }
        Identifier id = EntityType.getKey(type);
        String path = id.getPath();
        return !path.equals("marker") && !path.equals("armor_stand");
    }

    // ---------- synthetic death ----------

    /** Kills one synthetic mob of {@code type} and returns its drops; never throws. */
    private List<ItemStack> dropsOf(ServerLevel level, EntityType<?> type, @Nullable Player killer,
                                    @Nullable DamageSource source, boolean[] simulate) {
        LivingEntity mob;
        try {
            Entity entity = type.create(level, EntitySpawnReason.MOB_SUMMONED);
            if (!(entity instanceof LivingEntity living)) {
                return List.of();
            }
            mob = living;
        } catch (RuntimeException exception) {
            return List.of();
        }
        try {
            if (simulate[0] && source != null) {
                try {
                    return simulateDeath(level, mob, killer, source);
                } catch (RuntimeException | LinkageError failure) {
                    // A mod may deny the synthetic death. Give up on the death path for the rest of this
                    // batch only; the next batch retries.
                    LOGGER.debug("Mob farm at {}: synthetic death failed ({}), falling back to the loot table",
                            worldPosition, failure.toString());
                    simulate[0] = false;
                }
            }
            return rollLootTable(level, type, killer, source);
        } catch (RuntimeException | LinkageError failure) {
            LOGGER.debug("Mob farm at {}: loot table roll failed ({})", worldPosition, failure.toString());
            return List.of();
        } finally {
            mob.discard();
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
        List<ItemStack> fallback = EntityPools.fallbackDrops(type, level, killer);
        if (!fallback.isEmpty()) {
            LOGGER.debug("Random card fallback used for {}", type);
            return fallback;
        }
        LOGGER.debug("Mob farm at {}: {} drops nothing at all", worldPosition, type);
        return List.of();
    }

    /**
     * Kills the throwaway mob at the machine. Everything a real death does happens here except that the
     * mob is not part of the level: {@code die} runs the loot table, the global loot modifiers,
     * {@code dropCustomDeathLoot} and the death/drops events, whose items are captured through
     * {@link SyntheticDeathDrops}.
     */
    private List<ItemStack> simulateDeath(ServerLevel level, LivingEntity mob, @Nullable Player killer,
                                          DamageSource source) {
        mob.setPos(Vec3.atCenterOf(worldPosition));
        mob.setHealth(mob.getMaxHealth());
        if (mob instanceof Mob spawned) {
            try {
                // Lets spawn-time data attach (equipment, modded bonus loot tables).
                spawned.finalizeSpawn(level, level.getCurrentDifficultyAt(worldPosition),
                        EntitySpawnReason.MOB_SUMMONED, null);
            } catch (RuntimeException ignored) {
                // Some entity types need extra spawn context; their default loot is still rolled.
            }
        }
        mob.tickCount = 1;
        if (killer != null) {
            mob.setLastHurtByMob(killer);
            // The loot pipeline only treats this as a player kill while the timestamp is non-zero.
            mob.setLastHurtByPlayer(killer, 1);
        }
        Set<Entity> before = snapshot(level);
        List<ItemStack> dropped = new ArrayList<>();
        SyntheticDeathDrops.begin();
        SyntheticDeathDrops.mark(mob);
        try {
            mob.die(source);
        } finally {
            dropped.addAll(SyntheticDeathDrops.end());
            mob.captureDrops(null);
        }
        // Anything a mod spawned straight into the level is collected too, and the experience orbs of
        // the synthetic death are removed: the machine stores items only.
        dropped.addAll(collectLevelDrops(level, before));
        return dropped;
    }

    /** Every entity inside the two-block area around the machine. */
    private AABB dropArea() {
        Vec3 center = Vec3.atCenterOf(worldPosition);
        return new AABB(center, center).inflate(2.0);
    }

    /** Identity snapshot so only entities created by this death are collected. */
    private Set<Entity> snapshot(ServerLevel level) {
        Set<Entity> found = Collections.newSetFromMap(new IdentityHashMap<>());
        found.addAll(level.getEntitiesOfClass(Entity.class, dropArea()));
        return found;
    }

    /**
     * Picks up drops a mod added to the level directly (instead of through {@code LivingDropsEvent}) and
     * discards every experience orb around the machine, so a batch never sprays XP on the floor.
     */
    private List<ItemStack> collectLevelDrops(ServerLevel level, Set<Entity> before) {
        List<ItemStack> collected = new ArrayList<>();
        for (Entity entity : level.getEntitiesOfClass(Entity.class, dropArea())) {
            if (entity instanceof ExperienceOrb orb) {
                orb.discard();
                continue;
            }
            if (entity instanceof ItemEntity item && !before.contains(entity)) {
                if (!item.getItem().isEmpty()) {
                    collected.add(item.getItem().copy());
                }
                item.discard();
            }
        }
        return collected;
    }

    /** The vanilla loot-table roll, used when no killer is available or the death path failed. */
    private List<ItemStack> rollLootTable(ServerLevel level, EntityType<?> type, @Nullable Player killer,
                                          @Nullable DamageSource source) {
        Optional<ResourceKey<LootTable>> key = type.getDefaultLootTable();
        if (key.isEmpty()) {
            return List.of();
        }
        LootTable lootTable = level.getServer().reloadableRegistries().getLootTable(key.get());
        if (lootTable == LootTable.EMPTY) {
            return List.of();
        }
        DamageSource damageSource = source != null
                ? source : level.damageSources().generic();
        LootParams.Builder builder = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, createForLoot(level, type))
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(worldPosition))
                .withParameter(LootContextParams.DAMAGE_SOURCE, damageSource)
                .withLuck(rollFortune(level));
        if (killer != null) {
            builder.withOptionalParameter(LootContextParams.ATTACKING_ENTITY, killer)
                    .withOptionalParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, killer)
                    .withOptionalParameter(LootContextParams.LAST_DAMAGE_PLAYER, killer);
        }
        return lootTable.getRandomItems(builder.create(LootContextParamSets.ENTITY));
    }

    /** A throwaway entity instance for the loot context (never added to the level). */
    @Nullable
    private static Entity createForLoot(ServerLevel level, EntityType<?> type) {
        try {
            return type.create(level, EntitySpawnReason.MOB_SUMMONED);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    /**
     * Who "kills" the synthetic mobs. The placer or any online player only when the real-player killer
     * is enabled (a real kill counts for that player's statistics), then the fake player while allowed,
     * and finally {@code null} — the machine then rolls the plain loot table, so basic drops keep
     * coming. The failure is never cached: the next batch tries the full chain again.
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
            if (nearest != null) {
                return nearest;
            }
        }
        if (SimpleAutoFarmConfig.MOB_FARM_ALLOW_FAKE_PLAYER.get()) {
            try {
                return FakePlayerFactory.getMinecraft(level);
            } catch (RuntimeException | LinkageError failure) {
                LOGGER.debug("Mob farm at {}: fake player unavailable ({})", worldPosition, failure.toString());
            }
        }
        return null;
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
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.overflowHandler.deserialize(input.childOrEmpty("Overflow"));
        String ownerId = input.getStringOr("Owner", "");
        if (!ownerId.isEmpty()) {
            try {
                UUID stored = UUID.fromString(ownerId);
                if (stored.getMostSignificantBits() != 0L || stored.getLeastSignificantBits() != 0L) {
                    this.owner = stored;
                }
            } catch (IllegalArgumentException ignored) {
                // A broken owner tag simply means "no owner".
            }
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        this.overflowHandler.serialize(output.child("Overflow"));
        if (this.owner != null) {
            output.putString("Owner", this.owner.toString());
        }
    }

    @Override
    public void dropContents(Level level) {
        super.dropContents(level);
        for (int i = 0; i < OVERFLOW_SLOTS; i++) {
            ItemStack stack = this.overflowHandler.getStackInSlot(i);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), stack);
            }
        }
    }
}
