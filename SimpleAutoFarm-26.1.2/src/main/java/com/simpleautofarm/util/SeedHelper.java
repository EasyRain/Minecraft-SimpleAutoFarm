package com.simpleautofarm.util;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BambooSaplingBlock;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BuddingAmethystBlock;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.ChorusFlowerBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.MushroomBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.TallFlowerBlock;

/**
 * Decides which items the farm accepts as plant markers. Covers vanilla crops,
 * pumpkin/melon stems, sugarcane, cactus, mushrooms, saplings, flowers, bushes,
 * bamboo, chorus flowers, budding crystals (amethyst / AE2 / GeOre), and nether wart.
 *
 * <p>Mystical Agriculture crops extend {@link CropBlock}, so they are handled automatically.
 */
public final class SeedHelper {

    private SeedHelper() {
    }

    public static boolean isPlant(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.is(Items.NETHER_WART)) {
            return true;
        }
        return stack.getItem() instanceof BlockItem blockItem && isPlantBlock(blockItem.getBlock());
    }

    public static boolean isPlantBlock(Block block) {
        return block instanceof CropBlock
                || block instanceof StemBlock
                || block instanceof SugarCaneBlock
                || block instanceof CactusBlock
                || block instanceof SaplingBlock
                || block instanceof FlowerBlock
                || block instanceof TallFlowerBlock
                || block instanceof MushroomBlock
                || block instanceof BushBlock
                || block instanceof ChorusFlowerBlock
                || block instanceof BambooSaplingBlock
                || block instanceof BambooStalkBlock
                || isBuddingBlock(block);
    }

    /** A "budding" block grows crystal clusters on its faces (amethyst, AE2, GeOre...). */
    public static boolean isBuddingBlock(Block block) {
        if (block instanceof BuddingAmethystBlock) {
            return true;
        }
        Identifier key = BuiltInRegistries.BLOCK.getKey(block);
        return key != null && key.getPath().contains("budding");
    }
}
