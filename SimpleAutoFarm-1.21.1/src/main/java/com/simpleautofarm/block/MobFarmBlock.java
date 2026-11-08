package com.simpleautofarm.block;

import net.minecraft.core.BlockPos;
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
}
