package com.simpleautofarm.util;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BambooSaplingBlock;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.BigDripleafBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.CaveVinesBlock;
import net.minecraft.world.level.block.CaveVinesPlantBlock;
import net.minecraft.world.level.block.ChorusFlowerBlock;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.GlowLichenBlock;
import net.minecraft.world.level.block.KelpBlock;
import net.minecraft.world.level.block.KelpPlantBlock;
import net.minecraft.world.level.block.MushroomBlock;
import net.minecraft.world.level.block.PitcherCropBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.SmallDripleafBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.TallFlowerBlock;

/**
 * Decides which items the crop farm accepts as plant markers. Covers vanilla crops,
 * pumpkin/melon stems, sugarcane, cactus, cocoa, mushrooms, saplings, flowers, bushes,
 * bamboo, chorus flowers, and nether wart — <b>plants only</b>.
 *
 * <p>Not every plantable vanilla block is a {@link CropBlock} or {@link BushBlock}: cocoa
 * ({@link CocoaBlock}), kelp ({@link KelpBlock}), glow berries ({@link CaveVinesBlock}),
 * dripleaves ({@link BigDripleafBlock} / {@link SmallDripleafBlock}), pitcher crops
 * ({@link PitcherCropBlock}) and glow lichen ({@link GlowLichenBlock}) all extend something else,
 * so they are listed explicitly.
 *
 * <p>Mystical Agriculture crops extend {@link CropBlock}, so they are handled automatically.
 *
 * <p>Non-plant samples (budding crystals such as amethyst / AE2 certus quartz / GeOre) belong to the
 * ore farm — see {@link CrystalHelper}, which the ore farm uses for both acceptance and products.
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
                || block instanceof CocoaBlock
                || block instanceof KelpBlock
                || block instanceof KelpPlantBlock
                || block instanceof CaveVinesBlock
                || block instanceof CaveVinesPlantBlock
                || block instanceof BigDripleafBlock
                || block instanceof SmallDripleafBlock
                || block instanceof PitcherCropBlock
                || block instanceof GlowLichenBlock
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
                || block instanceof BambooStalkBlock;
    }
}
