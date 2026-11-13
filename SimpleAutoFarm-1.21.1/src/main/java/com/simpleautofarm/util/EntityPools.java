package com.simpleautofarm.util;

import com.simpleautofarm.SimpleAutoFarm;
import com.simpleautofarm.SimpleAutoFarmConfig;
import com.simpleautofarm.item.EntityCardItem;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.world.level.storage.loot.LootParams;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The entity pools behind the random entity cards (animal / monster / boss).
 *
 * <p>Bosses come from NeoForge's own tag first ({@link Tags.EntityTypes#BOSSES}, {@code c:bosses},
 * which already holds the ender dragon and the wither), then this mod's tag
 * ({@link #BOSS_TAG}, where the warden, the elder guardian and pack-added bosses live), and finally a
 * health fallback for untagged modded bosses ({@link MobCategory#MONSTER} with 100+ base health).
 * Entities that NeoForge marks as not capturable ({@link Tags.EntityTypes#CAPTURING_NOT_SUPPORTED})
 * are left out of every pool.
 *
 * <p>The pools are cached in static fields, because the registry does not change while a game runs.
 * An <b>empty</b> result is never cached: early in server start-up a filter can legitimately find
 * nothing, and the next batch then simply tries again instead of staying empty forever. Caches are
 * dropped on a tag reload and when the server stops.
 */
public final class EntityPools {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Boss tag of this mod; {@code data/simpleautofarm/tags/entity_type/bosses.json}. */
    public static final TagKey<EntityType<?>> BOSS_TAG =
            TagKey.create(Registries.ENTITY_TYPE, SimpleAutoFarm.prefix("bosses"));

    /** Categories that count as "animals" for the animal card. */
    private static final Set<MobCategory> ANIMAL_CATEGORIES = EnumSet.of(
            MobCategory.CREATURE, MobCategory.AMBIENT, MobCategory.WATER_CREATURE,
            MobCategory.WATER_AMBIENT, MobCategory.AXOLOTLS);

    /** Living entities that are technically animals but make no sense on a farm card. */
    private static final Set<String> ANIMAL_EXCLUSIONS = Set.of("villager", "wandering_trader");

    /** Health from which an untagged monster counts as a boss. */
    private static final double BOSS_MIN_HEALTH = 100.0;

    private static final Map<EntityCardItem.CardKind, List<EntityType<?>>> POOLS =
            new EnumMap<>(EntityCardItem.CardKind.class);

    /** Pools with "empty loot table" entries filtered out; only filled once the server is ready. */
    private static final Map<EntityCardItem.CardKind, List<EntityType<?>>> DROPPING_POOLS =
            new EnumMap<>(EntityCardItem.CardKind.class);

    /** How often a random card's fallback rolls that mob's loot table (once per type, then cached). */
    private static final int EXPECTED_ROLLS = 64;

    /** Per-type expected payout for a batch that rolled nothing; EMPTY means "this table never drops". */
    private static final Map<EntityType<?>, ItemStack> EXPECTED_FALLBACK = new HashMap<>();

    /** Consecutive empty computations per type; this many in a row are trusted as "really no drops". */
    private static final Map<EntityType<?>, Integer> FALLBACK_EMPTY_TRIES = new HashMap<>();
    private static final int FALLBACK_EMPTY_LIMIT = 3;

    /**
     * Types observed to pay out nothing at all, even after the expected-value fallback. They leave the
     * random pools until a data reload: a bat stays here in vanilla, but one that some mod gives a drop
     * through a global loot modifier never ends up here, because the real drop path does see that.
     */
    private static final Set<EntityType<?>> NO_DROPS = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    /** Per-type "has a real loot table" answer, asked lazily at draw time (never at pool build). */
    private static final Map<EntityType<?>, Boolean> HAS_DROPS = new HashMap<>();

    /** Blacklist snapshot the current pools were built with, so a config edit can drop the caches. */
    private static Blacklist.Entries LAST_BLACKLIST = Blacklist.Entries.of(List.of());

    /** The cached pool of one card kind, built on first use; an empty result is retried next time. */
    public static List<EntityType<?>> pool(EntityCardItem.CardKind kind) {
        refreshBlacklist();
        List<EntityType<?>> cached = POOLS.get(kind);
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }
        List<EntityType<?>> built = build(kind);
        if (!built.isEmpty()) {
            POOLS.put(kind, built);
        }
        return built;
    }

    /**
     * A random mob of that kind for one batch, or {@code null} when nothing is left.
     *
     * <p>The loot-table filter runs here, not while the pool is built: by now the server's data packs
     * are loaded, so "empty table" really means "this mob drops nothing" instead of "not ready yet".
     */
    /**
     * Rebuilds the caches when the blacklist config changed. Called from both {@link #pick} and {@link #pool},
     * because a draw goes through the dropping-pool cache and would otherwise skip this check entirely.
     */
    private static void refreshBlacklist() {
        Blacklist.Entries snapshot = Blacklist.Entries.of(
                SimpleAutoFarmConfig.RANDOM_MOB_CARD_BLACKLIST.get());
        if (!snapshot.equals(LAST_BLACKLIST)) {
            LAST_BLACKLIST = snapshot;
            clear();
        }
    }

    /** The kind's pool without mobs that have no loot table, cached once the data packs are ready. */
    private static List<EntityType<?>> droppingPool(EntityCardItem.CardKind kind, ServerLevel level) {
        List<EntityType<?>> cached = DROPPING_POOLS.get(kind);
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }
        List<EntityType<?>> filtered = new ArrayList<>();
        int noDrops = 0;
        for (EntityType<?> type : pool(kind)) {
            if (NO_DROPS.contains(type)) {
                noDrops++;
            } else if (hasDrops(type, level)) {
                filtered.add(type);
            } else {
                noDrops++;
            }
        }
        if (!filtered.isEmpty()) {
            DROPPING_POOLS.put(kind, filtered);
            LOGGER.info("Entity card pool {}: {} droppers (no drops={})", kind, filtered.size(), noDrops);
        }
        return filtered;
    }

    @Nullable
    public static EntityType<?> pick(EntityCardItem.CardKind kind, ServerLevel level) {
        refreshBlacklist();
        List<EntityType<?>> candidates = droppingPool(kind, level);
        return candidates.isEmpty() ? null : candidates.get(level.random.nextInt(candidates.size()));
    }

    /**
     * Guaranteed payout for a random card whose batch came back empty: the expected drop of that mob.
     *
     * <p>The mobs are <b>not</b> filtered by "always drops" — slimes, bats and every pure-chance mob stay
     * in the pools, and this method only steps in when a batch really produced nothing. It rolls the
     * mob's loot table {@link #EXPECTED_ROLLS} times, merges equal stacks, and returns the single most
     * expected item in its average amount (at least one), which is what the Probability-Card idea in
     * other mods amounts to. The result is cached per entity type, so a batch costs nothing after the
     * first time.
     */
    public static List<ItemStack> fallbackDrops(EntityType<?> type, ServerLevel level,
                                              @Nullable net.minecraft.world.entity.player.Player killer) {
        ItemStack cached = EXPECTED_FALLBACK.get(type);
        if (cached == null) {
            cached = computeFallback(type, level, killer);
            if (!cached.isEmpty()) {
                EXPECTED_FALLBACK.put(type, cached);
                LOGGER.debug("Entity card fallback cache: {} type(s), last={}", EXPECTED_FALLBACK.size(), type);
            } else {
                // An empty result is not trusted straight away: right after a reload the loot tables may not
                // be ready yet, and caching that would keep the machine empty for that mob forever.
                int tries = FALLBACK_EMPTY_TRIES.merge(type, 1, Integer::sum);
                if (tries >= FALLBACK_EMPTY_LIMIT) {
                    EXPECTED_FALLBACK.put(type, ItemStack.EMPTY);
                    FALLBACK_EMPTY_TRIES.remove(type);
                    LOGGER.info("{}: no drops in {} rolls, cached as no-drop", type, EXPECTED_ROLLS);
                }
            }
        }
        return cached.isEmpty() ? List.of() : List.of(cached.copy());
    }

    /** Rolls the table a few dozen times and keeps the most expected item; EMPTY when nothing dropped. */
    private static ItemStack computeFallback(EntityType<?> type, ServerLevel level,
                                         @Nullable net.minecraft.world.entity.player.Player killer) {
        try {
            ResourceKey<LootTable> key = type.getDefaultLootTable();
            if (key == null) {
                return ItemStack.EMPTY;
            }
            LootTable table = level.getServer().reloadableRegistries().getLootTable(key);
            if (table == LootTable.EMPTY || !(type.create(level) instanceof Entity entity)) {
                return ItemStack.EMPTY;
            }
            try {
                // The killer is passed exactly like the death path does, so "killed_by_player" tables
                // (the phantom's membrane, for example) can actually drop during the sample rolls.
                DamageSource source = killer == null
                        ? level.damageSources().generic()
                        : level.damageSources().playerAttack(killer);
                LootParams.Builder builder = new LootParams.Builder(level)
                        .withParameter(LootContextParams.THIS_ENTITY, entity)
                        .withParameter(LootContextParams.ORIGIN, entity.position())
                        .withParameter(LootContextParams.DAMAGE_SOURCE, source)
                        .withLuck(0.0F);
                if (killer != null) {
                    builder.withOptionalParameter(LootContextParams.ATTACKING_ENTITY, killer)
                            .withOptionalParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, killer)
                            .withOptionalParameter(LootContextParams.LAST_DAMAGE_PLAYER, killer);
                }
                LootParams params = builder.create(LootContextParamSets.ENTITY);
                List<ItemStack> merged = new ArrayList<>();
                for (int roll = 0; roll < EXPECTED_ROLLS; roll++) {
                    for (ItemStack rolled : table.getRandomItems(params)) {
                        mergeInto(merged, rolled);
                    }
                }
                ItemStack expected = mostExpected(merged);
                if (!expected.isEmpty()) {
                    return expected;
                }
                // Every roll came back empty (a "0 or 1" count, or a player-only table with no killer):
                // still hand out one of the table's own items instead of wasting the batch.
                return forceOneDrop(table, type);
            } finally {
                entity.discard();
            }
        } catch (RuntimeException exception) {
            return ItemStack.EMPTY;
        }
    }

    /**
     * One item of the table, used when every sample roll came back empty: the table's own JSON is scanned for
     * the first item entry and one of it is produced. Returns EMPTY for tables without item entries, which
     * are the genuinely drop-less mobs.
     */
    private static ItemStack forceOneDrop(LootTable table, EntityType<?> type) {
        try {
            String json = LootTable.DIRECT_CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, table)
                    .result()
                    .map(Object::toString)
                    .orElse("");
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("\"name\"\\s*:\\s*\"([^\"]+)\"")
                    .matcher(json);
            while (matcher.find()) {
                String name = matcher.group(1);
                if (name.startsWith("#")) {
                    continue;
                }
                ResourceLocation id = ResourceLocation.tryParse(name);
                if (id == null) {
                    continue;
                }
                net.minecraft.world.item.Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
                if (item != null) {
                    LOGGER.debug("fallback table had only chance-zero rolls, forcing 1x {}", item);
                    return new ItemStack(item);
                }
            }
        } catch (RuntimeException | LinkageError failure) {
            LOGGER.debug("{}: could not derive a forced drop ({})", type, failure.toString());
        }
        return ItemStack.EMPTY;
    }

    /** Adds a rolled stack to the running totals of one batch's expected-value roll. */
    private static void mergeInto(List<ItemStack> merged, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        for (ItemStack existing : merged) {
            if (ItemStack.isSameItemSameComponents(existing, stack)) {
                existing.grow(stack.getCount());
                return;
            }
        }
        merged.add(stack.copy());
    }

    /** The item with the largest rolled total, in its average per-batch amount (at least one). */
    private static ItemStack mostExpected(List<ItemStack> merged) {
        ItemStack best = null;
        for (ItemStack candidate : merged) {
            if (candidate.isEmpty()) {
                continue;
            }
            if (best == null || candidate.getCount() > best.getCount()) {
                best = candidate;
            }
        }
        if (best == null) {
            return ItemStack.EMPTY;
        }
        int average = (int) Math.max(1L, Math.round(best.getCount() / (double) EXPECTED_ROLLS));
        return best.copyWithCount(Math.min(average, best.getMaxStackSize()));
    }

    /** Whether the entity type's loot table really holds entries; cached per type. */
    private static boolean hasDrops(EntityType<?> type, ServerLevel level) {
        Boolean cached = HAS_DROPS.get(type);
        if (cached != null) {
            return cached;
        }
        boolean drops;
        try {
            ResourceKey<LootTable> key = type.getDefaultLootTable();
            drops = key != null
                    && level.getServer().reloadableRegistries().getLootTable(key) != LootTable.EMPTY;
        } catch (RuntimeException exception) {
            // An unreadable table is treated as "no drops": the batch then simply draws again.
            drops = false;
        }
        HAS_DROPS.put(type, drops);
        return drops;
    }

    /** Tags can change on a datapack reload, so the pools are rebuilt afterwards. */
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        clear();
    }

    /** Remembers that this type never pays out, so the random cards stop drawing it. */
    public static void markNoDrops(EntityType<?> type) {
        if (NO_DROPS.add(type)) {
            LOGGER.info("Entity card pool: {} never drops anything, removed from the random pools", type);
            DROPPING_POOLS.clear();
        }
    }

    /** Drops every cached pool; called when the server stops. */
    public static void onServerStopping(ServerStoppingEvent event) {
        clear();
    }

    private static void clear() {
        POOLS.clear();
        DROPPING_POOLS.clear();
        HAS_DROPS.clear();
        FALLBACK_EMPTY_TRIES.clear();
        EXPECTED_FALLBACK.clear();
        NO_DROPS.clear();
    }

    /**
     * Scans the entity registry for one card kind and logs why entries were dropped, so an empty pool
     * can be diagnosed from the log alone.
     */
    private static List<EntityType<?>> build(EntityCardItem.CardKind kind) {
        List<EntityType<?>> result = new ArrayList<>();
        int scanned = 0;
        int noAttributes = 0;
        int artifacts = 0;
        int notCapturable = 0;
        int otherKind = 0;
        Blacklist.Entries blacklist = LAST_BLACKLIST;
        int blacklisted = 0;
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            scanned++;
            ResourceLocation blacklistId = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            if (blacklistId != null && blacklist.blocks(blacklistId.toString())
                    && type != EntityType.PLAYER && DefaultAttributes.hasSupplier(type)) {
                blacklisted++;
                continue;
            }
            if (type == EntityType.PLAYER || !DefaultAttributes.hasSupplier(type)) {
                // hasSupplier is the reliable "this is a living mob" signal: the vanilla EntityType
                // has no per-type base class (getBaseClass() answers Entity.class for everything).
                noAttributes++;
                continue;
            }
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            if (id == null) {
                otherKind++;
                continue;
            }
            String path = id.getPath();
            if (path.equals("armor_stand") || path.equals("marker")) {
                artifacts++;
                continue;
            }
            if (type.is(Tags.EntityTypes.CAPTURING_NOT_SUPPORTED)) {
                notCapturable++;
                continue;
            }
            if (!matches(kind, type)) {
                otherKind++;
                continue;
            }
            result.add(type);
        }
        LOGGER.debug("Entity card pool {} filter: scanned={}, no attributes/player={}, artifacts={}, "
                        + "not capturable={}, other kinds={}, accepted={}",
                kind, scanned, noAttributes, artifacts, notCapturable, otherKind, result.size());
        LOGGER.info("Entity card pool {}: {} mobs (blacklisted {}) (scanned={}, rejected: no attributes={}, "
                        + "artifacts={}, not capturable={}, other kinds={})",
                kind, result.size(), blacklisted, scanned, noAttributes, artifacts, notCapturable, otherKind);
        return List.copyOf(result);
    }

    /** Tag-driven boss check with a health fallback for modded bosses nobody tagged. */
    public static boolean isBoss(EntityType<?> type) {
        if (type.is(Tags.EntityTypes.BOSSES) || type.is(BOSS_TAG)) {
            return true;
        }
        return type.getCategory() == MobCategory.MONSTER
                && EntityCardItem.baseMaxHealth(type, null) >= BOSS_MIN_HEALTH;
    }

    /** Category, tag and health rules of one card kind. */
    private static boolean matches(EntityCardItem.CardKind kind, EntityType<?> type) {
        boolean boss = isBoss(type);
        MobCategory category = type.getCategory();
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        String path = id == null ? "" : id.getPath();
        return switch (kind) {
            case BOSS -> boss;
            case MONSTER -> !boss && category == MobCategory.MONSTER;
            case ANIMAL -> !boss && !ANIMAL_EXCLUSIONS.contains(path)
                    && ANIMAL_CATEGORIES.contains(category);
            case BOUND -> false;
        };
    }

    private EntityPools() {
    }
}
