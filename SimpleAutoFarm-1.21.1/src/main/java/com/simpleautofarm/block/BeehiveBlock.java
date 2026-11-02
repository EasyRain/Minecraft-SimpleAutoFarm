package com.simpleautofarm.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidActionResult;
import net.neoforged.neoforge.fluids.FluidUtil;
import org.jetbrains.annotations.Nullable;

/**
 * The simple auto beehive block: same casing, state machine, energy charging, GUI opening and
 * drop-with-contents behaviour as the crop farm (inherited from {@link FarmBlock}); only the
 * block entity behind it differs.
 */
public class BeehiveBlock extends FarmBlock {

    public BeehiveBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BeehiveBlockEntity(pos, state);
    }

    /**
     * Right-clicking with a bucket, bottle or any other fluid container fills it straight from the
     * honey tank (the same interaction the water source block offers). Anything else — FE-charging
     * items, or an empty hand opening the GUI — keeps the shared farm behaviour.
     */
    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                           Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty() || stack.getCapability(Capabilities.FluidHandler.ITEM) == null) {
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        }
        if (level.isClientSide()) {
            return ItemInteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof BeehiveBlockEntity beehive) {
            FluidActionResult filled = FluidUtil.tryFillContainer(stack, beehive.getFluidHandler(),
                    Integer.MAX_VALUE, player, true);
            if (filled.isSuccess()) {
                player.setItemInHand(hand, filled.getResult());
                return ItemInteractionResult.SUCCESS;
            }
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }
}
