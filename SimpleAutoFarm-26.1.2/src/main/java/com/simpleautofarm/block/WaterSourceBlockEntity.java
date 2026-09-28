package com.simpleautofarm.block;

import com.simpleautofarm.SimpleAutoFarm;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * The water source's logic. Three jobs, all of them free (no energy, nothing is consumed):
 * <ol>
 *   <li><b>Farmland</b>: vanilla farmland only stays moist while water is within 4 blocks (±4 horizontally,
 *       the level below the water or the same level), so every second this block re-moistens every
 *       farmland block in that same range — including modded farmland that extends {@link FarmlandBlock}.</li>
 *   <li><b>Push</b>: every tick it offers water to the fluid handler of all six neighbours (handlers take
 *       whatever fits, so the effective rate is "as much as they accept per tick").</li>
 *   <li><b>Pipes</b>: the exposed {@link IFluidHandler} is always full; draining yields water without ever
 *       emptying the block, and anything pushed in is accepted and discarded.</li>
 * </ol>
 */
public class WaterSourceBlockEntity extends BlockEntity {

    /** The block holds water up to the int limit — and never consumes it. */
    public static final int WATER_CAPACITY = Integer.MAX_VALUE;
    /** Vanilla water's farmland range: ±4 blocks horizontally, at the water's level or one below. */
    private static final int HYDRATION_RADIUS = 4;
    /** Re-moisten once a second (vanilla farmland would otherwise dry out and eventually turn to dirt). */
    private static final int MOISTEN_INTERVAL = 20;

    private final IFluidHandler fluidHandler = new IFluidHandler() {
        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return new FluidStack(Fluids.WATER, WATER_CAPACITY);
        }

        @Override
        public int getTankCapacity(int tank) {
            return WATER_CAPACITY;
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return stack.is(Fluids.WATER);
        }

        /** Accept whatever is pushed in and discard it: this block is a source, never a sink. */
        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return resource.is(Fluids.WATER) ? resource.getAmount() : 0;
        }

        /** Draining never empties the block. */
        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            if (!resource.is(Fluids.WATER)) {
                return FluidStack.EMPTY;
            }
            return new FluidStack(Fluids.WATER, resource.getAmount());
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return maxDrain <= 0 ? FluidStack.EMPTY : new FluidStack(Fluids.WATER, maxDrain);
        }
    };

    public WaterSourceBlockEntity(BlockPos pos, BlockState state) {
        super(SimpleAutoFarm.WATER_SOURCE_BLOCK_ENTITY.get(), pos, state);
    }

    public IFluidHandler getFluidHandler() {
        return fluidHandler;
    }

    public static void serverTick(Level level, BlockPos pos, WaterSourceBlockEntity source) {
        source.pushWater(level, pos);
        if (Math.floorMod(level.getGameTime() + pos.asLong(), MOISTEN_INTERVAL) == 0) {
            source.hydrateFarmland(level, pos);
        }
    }

    /** Offer water to every neighbour's fluid handler; each one takes whatever fits. */
    private void pushWater(Level level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockPos neighbour = pos.relative(direction);
            var capability = level.getCapability(Capabilities.Fluid.BLOCK, neighbour, direction.getOpposite());
            if (capability == null) {
                continue;
            }
            IFluidHandler.of(capability).fill(new FluidStack(Fluids.WATER, WATER_CAPACITY), IFluidHandler.FluidAction.EXECUTE);
        }
    }

    /** Keep every farmland block in vanilla water's range at full moisture. */
    private void hydrateFarmland(Level level, BlockPos pos) {
        for (int dx = -HYDRATION_RADIUS; dx <= HYDRATION_RADIUS; dx++) {
            for (int dz = -HYDRATION_RADIUS; dz <= HYDRATION_RADIUS; dz++) {
                for (int dy = -1; dy <= 0; dy++) {
                    BlockPos target = pos.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(target);
                    if (state.getBlock() instanceof FarmlandBlock
                            && state.hasProperty(FarmlandBlock.MOISTURE)
                            && state.getValue(FarmlandBlock.MOISTURE) < FarmlandBlock.MAX_MOISTURE) {
                        level.setBlock(target, state.setValue(FarmlandBlock.MOISTURE, FarmlandBlock.MAX_MOISTURE), 2);
                    }
                }
            }
        }
    }
}
