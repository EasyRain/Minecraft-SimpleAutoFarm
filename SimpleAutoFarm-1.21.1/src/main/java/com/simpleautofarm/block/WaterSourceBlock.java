package com.simpleautofarm.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidActionResult;
import net.neoforged.neoforge.fluids.FluidUtil;
import org.jetbrains.annotations.Nullable;

/**
 * Simple Water Source: a glass box holding one water block.
 *
 * <p>It behaves like vanilla water for farmland (it keeps the farmland in range hydrated), holds water
 * up to the int limit without ever consuming it, pushes water into all six neighbours every tick and
 * exposes a fluid handler so pipes / machines can pull from it. Right-clicking with a bucket, bottle
 * or any other fluid container fills it (and anything pushed in is voided).
 */
public class WaterSourceBlock extends Block implements EntityBlock {

    public WaterSourceBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WaterSourceBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (lvl, pos, st, blockEntity) -> WaterSourceBlockEntity.serverTick(lvl, pos, (WaterSourceBlockEntity) blockEntity);
    }

    /** Fill the held container from the endless supply, otherwise void whatever fluid it carries. */
    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.getCapability(Capabilities.FluidHandler.ITEM) == null) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide()) {
            return ItemInteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof WaterSourceBlockEntity source) {
            FluidActionResult filled = FluidUtil.tryFillContainer(stack, source.getFluidHandler(), Integer.MAX_VALUE, player, true);
            if (filled.isSuccess()) {
                player.setItemInHand(hand, filled.getResult());
                splash(level, pos);
                return ItemInteractionResult.SUCCESS;
            }
            FluidActionResult emptied = FluidUtil.tryEmptyContainer(stack, source.getFluidHandler(), Integer.MAX_VALUE, player, true);
            if (emptied.isSuccess()) {
                player.setItemInHand(hand, emptied.getResult());
                splash(level, pos);
                return ItemInteractionResult.SUCCESS;
            }
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** Splash particles + water sound, the same feedback the sink gives. */
    private static void splash(Level level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.WATER_AMBIENT, SoundSource.BLOCKS, 0.5F, 1.0F);
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.SPLASH, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5,
                    8, 0.3, 0.2, 0.3, 0.0);
        }
    }
}
