package com.simpleautofarm.util;

import com.simpleautofarm.SimpleAutoFarm;
import com.simpleautofarm.item.EntityCardItem;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
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

    /**
     * How often an entity's loot table is rolled while checking it. A "guaranteed" table yields at
     * least one item on every one of these rolls; the check runs once per type while a pool is built,
     * never per batch.
     */
    private static final int GUARANTEE_ROLLS = 64;

    /** Health from which an untagged monster counts as a boss. */
    private static final double BOSS_MIN_HEALTH = 100.0;

    private static final Map<EntityCardItem.CardKind, List<EntityType<?>>> POOLS =
            new EnumMap<>(EntityCardItem.CardKind.class);

    /** Pools with "empty loot table" entries filtered out; only filled once the server is ready. */
    private static final Map<EntityCardItem.CardKind, List<EntityType<?>>> DROPPING_POOLS =
            new EnumMap<>(EntityCardItem.CardKind.class);

    /** Pools restricted to mobs that always drop, used by the random cards. */
    private static final Map<EntityCardItem.CardKind, List<EntityType<?>>> GUARANTEED_POOLS =
            new EnumMap<>(EntityCardItem.CardKind.class);

    /** Per-type "has a real loot table" answer, asked lazily at draw time (never at pool build). */
    private static final Map<EntityType<?>, Boolean> HAS_DROPS = new HashMap<>();

    /**
     * Per-type "drops something every time" answer, filled while a pool is built. Only the random cards
     * use it: they must pay out on every batch, so their pool holds guaranteed droppers only.
     */
    private static final Map<EntityType<?>, Boolean> GUARANTEED = new HashMap<>();

    /** The cached pool of one card kind, built on first use; an empty result is retried next time. */
    public static List<EntityType<?>> pool(EntityCardItem.CardKind kind) {
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
    @Nullable
    public static EntityType<?> pick(EntityCardItem.CardKind kind, ServerLevel level) {
        List<EntityType<?>> candidates = guaranteedPool(kind, level);
        return candidates.isEmpty() ? null : candidates.get(level.random.nextInt(candidates.size()));
    }

    /**
     * The random cards' pool: only mobs whose loot table pays out on <b>every</b> roll, so a batch can
     * never come back empty and the random card still draws a different mob each time. When a pack has
     * no such mob at all the plain dropping pool is used, and the machine's retry is the last resort.
     */
    private static List<EntityType<?>> guaranteedPool(EntityCardItem.CardKind kind, ServerLevel level) {
        List<EntityType<?>> cached = GUARANTEED_POOLS.get(kind);
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }
        List<EntityType<?>> filtered = new ArrayList<>();
        for (EntityType<?> type : droppingPool(kind, level)) {
            if (isGuaranteedDropper(type, level)) {
                filtered.add(type);
            }
        }
        if (filtered.isEmpty()) {
            LOGGER.warn("Entity card pool {}: no mob drops on every roll, falling back to the plain pool", kind);
            return droppingPool(kind, level);
        }
        GUARANTEED_POOLS.put(kind, filtered);
        LOGGER.info("Entity card pool {}: {} guaranteed dropper(s) of {} candidate(s)", kind,
                filtered.size(), droppingPool(kind, level).size());
        return filtered;
    }

    /**
     * Structural half of the check: the table has a pool without conditions and an entry that is neither
     * {@code minecraft:empty} nor gated by its own conditions, so there is a drop path that cannot fail.
     * Reading the table's own JSON keeps this independent of the loot internals (their pools/entries are
     * not public API), and it rejects "chance only" tables before they are ever rolled.
     */
    private static boolean hasUnconditionalEntry(LootTable table) {
        try {
            return LootTable.DIRECT_CODEC.encodeStart(JsonOps.INSTANCE, table)
                    .result()
                    .filter(JsonElement::isJsonObject)
                    .map(JsonElement::getAsJsonObject)
                    .map(root -> root.getAsJsonArray("pools"))
                    .filter(pools -> pools != null && !pools.isEmpty())
                    .map(EntityPools::anyPoolAlwaysDrops)
                    .orElse(false);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    /** Any pool with no conditions whose entries contain one unconditional, non-empty item. */
    private static boolean anyPoolAlwaysDrops(JsonArray pools) {
        for (JsonElement poolElement : pools) {
            if (!poolElement.isJsonObject()) {
                continue;
            }
            JsonObject pool = poolElement.getAsJsonObject();
            if (pool.has("conditions") && !pool.getAsJsonArray("conditions").isEmpty()) {
                continue;
            }
            JsonArray entries = pool.getAsJsonArray("entries");
            if (entries == null) {
                continue;
            }
            for (JsonElement entryElement : entries) {
                if (!entryElement.isJsonObject()) {
                    continue;
                }
                JsonObject entry = entryElement.getAsJsonObject();
                String type = entry.has("type") ? entry.get("type").getAsString() : "minecraft:item";
                if (type.endsWith("empty")) {
                    continue;
                }
                if (entry.has("conditions") && !entry.getAsJsonArray("conditions").isEmpty()) {
                    continue;
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Behavioural half of the check: rolling the table a few dozen times must also always pay out. A
     * structural entry can still be clamped to zero by a {@code set_count} function, which only rolling
     * reveals.
     */
    private static boolean isGuaranteedDropper(EntityType<?> type, ServerLevel level) {
        Boolean cached = GUARANTEED.get(type);
        if (cached != null) {
            return cached;
        }
        boolean guaranteed = false;
        try {
            ResourceKey<LootTable> key = type.getDefaultLootTable();
            if (key != null) {
                LootTable table = level.getServer().reloadableRegistries().getLootTable(key);
                if (table != LootTable.EMPTY && hasUnconditionalEntry(table)
                        && type.create(level) instanceof Entity entity) {
                    try {
                        LootParams params = new LootParams.Builder(level)
                                .withParameter(LootContextParams.THIS_ENTITY, entity)
                                .withParameter(LootContextParams.ORIGIN, entity.position())
                                .withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().generic())
                                .withLuck(0.0F)
                                .create(LootContextParamSets.ENTITY);
                        guaranteed = true;
                        for (int roll = 0; roll < GUARANTEE_ROLLS; roll++) {
                            if (table.getRandomItems(params).isEmpty()) {
                                guaranteed = false;
                                break;
                            }
                        }
                    } finally {
                        entity.discard();
                    }
                }
            }
        } catch (RuntimeException exception) {
            guaranteed = false;
        }
        GUARANTEED.put(type, guaranteed);
        return guaranteed;
    }

    /** The kind's pool without mobs that have no loot table, cached once the data packs are ready. */
    private static List<EntityType<?>> droppingPool(EntityCardItem.CardKind kind, ServerLevel level) {
        List<EntityType<?>> cached = DROPPING_POOLS.get(kind);
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }
        List<EntityType<?>> filtered = new ArrayList<>();
        for (EntityType<?> type : pool(kind)) {
            if (hasDrops(type, level)) {
                filtered.add(type);
            }
        }
        if (!filtered.isEmpty()) {
            DROPPING_POOLS.put(kind, filtered);
        }
        return filtered;
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

    /** Drops every cached pool; called when the server stops. */
    public static void onServerStopping(ServerStoppingEvent event) {
        clear();
    }

    private static void clear() {
        POOLS.clear();
        DROPPING_POOLS.clear();
        GUARANTEED_POOLS.clear();
        HAS_DROPS.clear();
        GUARANTEED.clear();
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
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            scanned++;
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
        LOGGER.info("Entity card pool {}: scanned={} accepted={} (rejected: no attributes={}, artifacts={}, "
                        + "not capturable={}, other kinds={})",
                kind, scanned, result.size(), noAttributes, artifacts, notCapturable, otherKind);
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
