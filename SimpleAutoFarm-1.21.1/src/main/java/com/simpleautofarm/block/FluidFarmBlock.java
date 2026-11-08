package com.simpleautofarm.block;

import net.minecraft.core.BlockPos;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidActionResult;
import net.neoforged.neoforge.fluids.FluidUtil;
import org.jetbrains.annotations.Nullable;

/**
 * The simple auto fluid farm block: same casing, state machine, energy charging, GUI opening and
 * drop-with-contents behaviour as the crop farm (inherited from {@link FarmBlock}); only the block
 * entity behind it differs.
 *
 * <p>{@link #FILL} is the cosmetic state of the tank: the blockstate swaps between an empty shell and
 * a model with a static fluid cube inside, exactly like the water source block does it, so the middle
 * of the machine shows what the samples are producing without a block entity renderer.
 */
public class FluidFarmBlock extends FarmBlock {

    /** Fluid displayed inside the casing; only the category is encoded, the colour comes from the tank. */
    public enum Fill implements StringRepresentable {
        EMPTY("empty"),
        WATER("water"),
        LAVA("lava"),
        OTHER("other");

        private final String name;

        Fill(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }

    public static final EnumProperty<Fill> FILL = EnumProperty.create("fill", Fill.class);

    public FluidFarmBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FILL);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FluidFarmBlockEntity(pos, state);
    }

    /**
     * Runs the shared farm ticker, then fills the container row from the tank of each column and
     * refreshes the displayed fluid, so the model follows the samples without the machine having to
     * push block updates from inside the batch code.
     */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        BlockEntityTicker<T> base = super.getTicker(level, state, type);
        if (base == null) {
            return null;
        }
        return (lvl, pos, st, blockEntity) -> {
            base.tick(lvl, pos, st, blockEntity);
            if (blockEntity instanceof FluidFarmBlockEntity fluidFarm) {
                fluidFarm.fillContainers();
                fluidFarm.refreshDisplay();
            }
        };
    }

    /**
     * Right-clicking with a bucket, bottle or any other fluid container fills it straight from the
     * first tank that can serve it (the same interaction the beehive offers for its honey). The nine
     * tanks are tried in slot order, so a mixed machine hands out whichever fluid matches the
     * container first. Anything else — FE-charging items, or an empty hand opening the GUI — keeps
     * the shared farm behaviour.
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
        if (level.getBlockEntity(pos) instanceof FluidFarmBlockEntity fluidFarm) {
            for (int tank = 0; tank < FluidFarmBlockEntity.TANK_SLOTS; tank++) {
                FluidActionResult filled = FluidUtil.tryFillContainer(stack, fluidFarm.getTankHandler(tank),
                        Integer.MAX_VALUE, player, true);
                if (filled.isSuccess()) {
                    player.setItemInHand(hand, filled.getResult());
                    return ItemInteractionResult.SUCCESS;
                }
            }
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }
}
