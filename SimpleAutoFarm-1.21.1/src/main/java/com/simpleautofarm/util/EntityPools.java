package com.simpleautofarm.util;

import com.simpleautofarm.SimpleAutoFarm;
import com.simpleautofarm.item.EntityCardItem;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
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

    /** A random mob of that kind for one batch, or {@code null} when the pool is empty. */
    @Nullable
    public static EntityType<?> pick(EntityCardItem.CardKind kind, ServerLevel level) {
        List<EntityType<?>> pool = pool(kind);
        return pool.isEmpty() ? null : pool.get(level.random.nextInt(pool.size()));
    }

    /** Tags can change on a datapack reload, so the pools are rebuilt afterwards. */
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        POOLS.clear();
    }

    /** Drops every cached pool; called when the server stops. */
    public static void onServerStopping(ServerStoppingEvent event) {
        POOLS.clear();
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
