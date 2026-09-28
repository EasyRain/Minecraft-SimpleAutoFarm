package com.simpleautofarm.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The ore farm block: identical shape, state machine, energy charging and drop behaviour as the
 * crop farm (inherited from {@link FarmBlock}); only the block entity behind it differs.
 */
public class OreFarmBlock extends FarmBlock {

    public OreFarmBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new OreFarmBlockEntity(pos, state);
    }
}
