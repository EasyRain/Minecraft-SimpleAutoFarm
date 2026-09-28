package com.simpleautofarm.block;

import com.simpleautofarm.SimpleAutoFarm;
import com.simpleautofarm.util.CrystalHelper;
import com.simpleautofarm.util.OreHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Simple Auto Ore Farm: the crop farm's machinery (FE energy, speed/efficiency/yield/creative
 * upgrades, auto-eject, AE2 output, deep stacks) with ore handling instead of growth.
 *
 * <p>Samples are the {@code c:ores} / {@code c:raw_materials} items (recognition mirrors
 * useless_mod's ore generator, see {@link OreHelper}) plus the <b>budding crystal blocks</b>
 * (amethyst / AE2 / GeOre) that used to live on the crop farm — see {@link CrystalHelper}.
 * Ores and raw materials are duplicated one-for-one; a budding crystal yields the drops of the
 * cluster it would grow (budding amethyst -> amethyst shards). Yield upgrades multiply the result.
 */
public class OreFarmBlockEntity extends FarmBlockEntity {

    public OreFarmBlockEntity(BlockPos pos, BlockState state) {
        super(SimpleAutoFarm.ORE_FARM_BLOCK_ENTITY.get(), pos, state);
    }

    @Override
    public boolean isValidSample(ItemStack stack) {
        return OreHelper.isOre(stack) || CrystalHelper.isBudding(stack);
    }

    @Override
    protected List<ItemStack> computeProducts(Level level, ItemStack sample) {
        if (sample.getItem() instanceof BlockItem blockItem
                && CrystalHelper.isBuddingBlock(blockItem.getBlock())) {
            // Crystal mother rocks produce the drops of the cluster they grow.
            List<ItemStack> drops = CrystalHelper.clusterDrops(level, this.worldPosition, this, blockItem.getBlock());
            if (!drops.isEmpty()) {
                return drops;
            }
        }
        // The base class multiplies the returned count by the yield-upgrade multiplier.
        return List.of(sample.copy());
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.simpleautofarm.ore_farm");
    }
}
