package com.simpleautofarm.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The simple auto mob farm block: same casing, state machine, energy charging, GUI opening and
 * drop-with-contents behaviour as the crop farm (inherited from {@link FarmBlock}); only the block
 * entity behind it differs.
 */
public class MobFarmBlock extends FarmBlock {

    public MobFarmBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MobFarmBlockEntity(pos, state);
    }

    /**
     * Records who placed the machine: that player is the killer of the synthetic mobs while online,
     * which lets modded loot tables scale with their world tier. A machine placed from a dropped item
     * already carries its original owner in the block entity data, and that one wins.
     */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
                            ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && placer instanceof Player player
                && level.getBlockEntity(pos) instanceof MobFarmBlockEntity mobFarm) {
            mobFarm.setOwnerIfAbsent(player);
        }
    }
}
