package com.simpleautofarm.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import org.jetbrains.annotations.Nullable;

public class FarmBlock extends Block implements EntityBlock {

    public FarmBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FarmBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (lvl, pos, st, blockEntity) -> FarmBlockEntity.serverTick(lvl, pos, st, (FarmBlockEntity) blockEntity);
    }

    /**
     * Right-clicking with any item that carries FE energy charges the machine.
     */
    @Override
    public InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty()) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        var itemEnergyCapability = stack.getCapability(Capabilities.Energy.ITEM, ItemAccess.forStack(stack));
        IEnergyStorage itemEnergy = itemEnergyCapability != null ? IEnergyStorage.of(itemEnergyCapability) : null;
        if (itemEnergy != null && itemEnergy.getEnergyStored() > 0) {
            if (!level.isClientSide() && level.getBlockEntity(pos) instanceof FarmBlockEntity farm) {
                farm.chargeFromItem(itemEnergy);
                player.getInventory().setChanged();
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    /**
     * Right-clicking with an empty hand opens the GUI.
     */
    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof FarmBlockEntity farm) {
            serverPlayer.openMenu(farm, pos);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Drops the machine itself with its inventory and energy preserved inside the item's
     * {@code block_entity_data} component, instead of scattering the contents on the ground.
     */
    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity, ItemStack tool) {
        player.awardStat(Stats.BLOCK_MINED.get(this));
        player.causeFoodExhaustion(0.005F);
        if (!level.isClientSide() && blockEntity instanceof FarmBlockEntity farm) {
            ItemStack drop = new ItemStack(this);
            TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
            farm.saveCustomOnly(out);
            BlockItem.setBlockEntityData(drop, farm.getType(), out);
            Block.popResource(level, pos, drop);
        }
    }
}
