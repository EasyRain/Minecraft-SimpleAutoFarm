package com.simpleautofarm.util;

import com.mojang.logging.LogUtils;
import com.simpleautofarm.SimpleAutoFarmConfig;
import com.simpleautofarm.item.MaterialCardItem;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.StemBlock;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The item pools behind the random material cards.
 *
 * <p>Each pool is built from the machine's own sample rule (so a card can never draw something the
 * machine would reject) and then split by category: crops are the blocks that grow on farmland, trees are
 * saplings, plants are every other plant, and ores are whatever the ore farm accepts. The pools are lazy,
 * an empty result is never cached (early start-up can legitimately find nothing) and everything is dropped
 * on a tag reload or when the server stops.
 */
public final class MaterialPools {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Cap on "draw again" attempts when a drawn sample yields no products at all. */
    public static final int PICK_ATTEMPTS = 3;

    private static final Map<MaterialCardItem.Kind, List<ItemStack>> POOLS =
            new EnumMap<>(MaterialCardItem.Kind.class);

    /** Blacklist snapshot the current pools were built with, so a config edit can drop the caches. */
    private static Blacklist.Entries LAST_BLACKLIST = Blacklist.Entries.of(List.of());

    /** A random sample of that category, or {@code null} when the pool is empty. */
    @Nullable
    public static ItemStack pick(MaterialCardItem.Kind kind, long randomSeed) {
        List<ItemStack> pool = pool(kind);
        if (pool.isEmpty()) {
            return null;
        }
        int index = (int) Math.floorMod(randomSeed, pool.size());
        return pool.get(index).copy();
    }

    /** One category's pool, built on first use. */
    public static List<ItemStack> pool(MaterialCardItem.Kind kind) {
        // Same idea as the entity pools: rebuild when the blacklist snapshot changed.
        Blacklist.Entries blacklist = Blacklist.Entries.of(
                SimpleAutoFarmConfig.RANDOM_MATERIAL_CARD_BLACKLIST.get());
        if (!blacklist.equals(LAST_BLACKLIST)) {
            LAST_BLACKLIST = blacklist;
            clear();
        }
        List<ItemStack> cached = POOLS.get(kind);
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }
        List<ItemStack> built = build(kind);
        if (!built.isEmpty()) {
            POOLS.put(kind, built);
        }
        return built;
    }

    /** Scans the item registry once per category and logs how many entries it accepted. */
    private static List<ItemStack> build(MaterialCardItem.Kind kind) {
        List<ItemStack> result = new ArrayList<>();
        Blacklist.Entries blacklist = LAST_BLACKLIST;
        int blacklisted = 0;
        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack stack = new ItemStack(item);
            if (!isCandidate(kind, stack)) {
                continue;
            }
            if (blacklist.blocks(BuiltInRegistries.ITEM.getKey(item).toString())) {
                blacklisted++;
                continue;
            }
            result.add(stack);
        }
        LOGGER.info("Material card pool {}: {} items (blacklisted {})", kind, result.size(), blacklisted);
        return List.copyOf(result);
    }

    /** Category rules: crops/trees/plants come from the crop farm's rule, ores from the ore farm's. */
    private static boolean isCandidate(MaterialCardItem.Kind kind, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return switch (kind) {
            case ORE -> OreHelper.isOre(stack) || CrystalHelper.isBudding(stack);
            case CROP -> SeedHelper.isPlant(stack) && isCrop(stack);
            case TREE -> isTree(stack);
            case PLANT -> SeedHelper.isPlant(stack) && !isCrop(stack) && !isTree(stack);
        };
    }

    /** Grows on farmland: a crop block, a melon/pumpkin stem, or anything in {@code minecraft:crops}. */
    private static boolean isCrop(ItemStack stack) {
        return stack.getItem() instanceof BlockItem blockItem
                && (blockItem.getBlock() instanceof CropBlock || blockItem.getBlock() instanceof StemBlock
                || blockItem.getBlock().defaultBlockState().is(BlockTags.CROPS));
    }

    /** A sapling by block class or by the vanilla {@code minecraft:saplings} item tag. */
    private static boolean isTree(ItemStack stack) {
        if (stack.is(ItemTags.SAPLINGS)) {
            return true;
        }
        return stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof SaplingBlock;
    }

    public static void onTagsUpdated(TagsUpdatedEvent event) {
        clear();
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        clear();
    }

    private static void clear() {
        POOLS.clear();
    }

    private MaterialPools() {
    }
}
