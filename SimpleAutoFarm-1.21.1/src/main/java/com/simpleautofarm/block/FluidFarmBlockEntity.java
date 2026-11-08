package com.simpleautofarm.block;

import com.simpleautofarm.SimpleAutoFarm;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Simple Auto Fluid Farm: the crop farm's machinery (FE energy, speed/efficiency/yield/fortune/
 * creative upgrades, auto-eject, AE2 output) fed with fluid containers instead of seeds.
 *
 * <p>Samples are container items that hold a non-empty fluid (a water bucket, a lava bucket, a
 * filled bottle, a filled tank item...). Like the ore farm's ores, a sample is only a marker: it is
 * never consumed and defines <b>which</b> fluid the machine produces. Every finished batch adds
 * {@link #FLUID_PER_SAMPLE} mB per occupied input slot to an output-only tank that pipes, AE2 and
 * right-clicking with an empty container can drain. No item is ever produced.
 */
public class FluidFarmBlockEntity extends FarmBlockEntity {

    /** Fluid a single sample adds per finished batch, before the yield / fortune upgrades. */
    public static final int FLUID_PER_SAMPLE = 250;

    /** Base tank size in mB: 16 buckets, scaled by the yield upgrade like the item output is. */
    public static final int BASE_CAPACITY = 16_000;

    /** Capacity cap: the creative upgrade's unbounded multiplier would be unreadable in the title. */
    private static final int MAX_CAPACITY_MULTIPLIER = 64;

    /** Sync tag key: registry name of the fluid the casing should display (client-side colour). */
    private static final String TAG_DISPLAY_FLUID = "DisplayFluid";

    /** Saved tag key: registry name of the fluid the tank holds, so a sample swap cannot relabel it. */
    private static final String TAG_TANK_FLUID = "TankFluid";

    /** Product tank: filled while the machine runs, drained by pipes, never filled from the outside. */
    private final ProductTank tank = new ProductTank();

    /** Fluid the casing shows; on the client this comes from the block entity sync tag. */
    private FluidStack displayFluid = FluidStack.EMPTY;

    /**
     * Fluid the tank actually holds. Stored separately from the samples so swapping a sample while
     * the tank still has content cannot turn the stored water into lava (and vice versa): the tank is
     * drained first, then the samples decide what is produced next.
     */
    @Nullable
    private Fluid tankFluid;

    /** Last fluid pushed to the clients, so a changed sample refreshes the colour exactly once. */
    @Nullable
    private Fluid lastDisplayFluid;

    public FluidFarmBlockEntity(BlockPos pos, BlockState state) {
        super(SimpleAutoFarm.FLUID_FARM_BLOCK_ENTITY.get(), pos, state);
    }

    /** Only fluid containers with at least one non-empty tank belong in the input slots. */
    @Override
    public boolean isValidSample(ItemStack stack) {
        return sampleFluid(stack) != null;
    }

    /** The fluid farm produces no items at all: every product goes into the internal tank. */
    @Override
    protected List<ItemStack> computeProducts(Level level, ItemStack seed) {
        return List.of();
    }

    /** An empty product list still finishes a batch here, otherwise this machine would never run. */
    @Override
    protected boolean produceWithoutItems() {
        return true;
    }

    /**
     * One batch adds {@link #FLUID_PER_SAMPLE} mB per occupied input slot of the fluid that the first
     * sample holds, multiplied by the yield and fortune upgrades exactly like the item output is.
     */
    @Override
    protected void onBatchProduced(Level level, int planted) {
        if (planted <= 0) {
            return;
        }
        FluidStack fluid = firstSampleFluid();
        if (fluid == null) {
            return;
        }
        long amount = (long) FLUID_PER_SAMPLE * planted * getProductionMultiplier() * rollFortune(level);
        tank.add(fluid.getFluid(), (int) Math.min(Integer.MAX_VALUE, amount));
    }

    /** Fluid currently in the tank, in mB. */
    public int getFluidAmount() {
        return tank.amount;
    }

    /** Current tank size in mB: {@link #BASE_CAPACITY} times the yield multiplier. */
    public int getTankCapacity() {
        long capacity = (long) BASE_CAPACITY * Math.min(getProductionMultiplier(), MAX_CAPACITY_MULTIPLIER);
        return (int) Math.min(Integer.MAX_VALUE, capacity);
    }

    /** The product tank as pipes see it: drain from any side, never fill. */
    public IFluidHandler getFluidHandler() {
        return tank;
    }

    /** Fluid the casing currently displays (client side: whatever the last sync tag carried). */
    public FluidStack getDisplayFluid() {
        return displayFluid;
    }

    /**
     * Keeps the blockstate's {@code fill} variant and the synced display fluid in line with the
     * samples, so the model shows an empty cavity, water, lava or a fluid-tinted cube. Runs from the
     * block's ticker (server only) and is staggered because it asks item capabilities for their
     * content; the variant is only written when it actually changes.
     */
    public void refreshDisplay() {
        if (level == null || level.isClientSide()) {
            return;
        }
        BlockState state = getBlockState();
        if (!state.hasProperty(FluidFarmBlock.FILL)) {
            return;
        }
        if (Math.floorMod(level.getGameTime() + worldPosition.asLong(), 5) != 0) {
            return;
        }
        // An empty tank shows the empty shell: the cube only appears once a batch has actually
        // produced fluid, and it disappears again when pipes drain the tank completely.
        FluidStack held = tank.getFluidInTank(0);
        FluidStack shown = held.isEmpty() ? null : held;
        FluidFarmBlock.Fill fill = fillOf(shown);
        Fluid fluid = shown == null ? null : shown.getFluid();
        if (state.getValue(FluidFarmBlock.FILL) != fill) {
            level.setBlock(worldPosition, state.setValue(FluidFarmBlock.FILL, fill), Block.UPDATE_CLIENTS);
        } else if (fluid != lastDisplayFluid) {
            // Same variant, different fluid (honey -> milk): resend the colour to the clients.
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
        lastDisplayFluid = fluid;
        displayFluid = shown == null ? FluidStack.EMPTY : shown;
    }

    /** Which cosmetic variant a sample fluid maps to. */
    private static FluidFarmBlock.Fill fillOf(@Nullable FluidStack stack) {
        if (stack == null || stack.isEmpty()) {
            return FluidFarmBlock.Fill.EMPTY;
        }
        Fluid fluid = stack.getFluid();
        if (fluid.isSame(Fluids.WATER)) {
            return FluidFarmBlock.Fill.WATER;
        }
        if (fluid.isSame(Fluids.LAVA)) {
            return FluidFarmBlock.Fill.LAVA;
        }
        return FluidFarmBlock.Fill.OTHER;
    }

    // ---------- client sync (only the fluid the casing paints, never the inventory) ----------

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        FluidStack held = tank.getFluidInTank(0);
        if (!held.isEmpty()) {
            tag.putString(TAG_DISPLAY_FLUID, BuiltInRegistries.FLUID.getKey(held.getFluid()).toString());
        }
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /**
     * Auto-eject takes the fluid along, in the same order the items go: the connected ME network
     * first (AE2 stores fluids through {@code AEFluidKey}), then every neighbouring fluid handler,
     * each one taking what it accepts.
     */
    @Override
    protected boolean ejectByproducts(Level level) {
        if (tank.amount <= 0) {
            return false;
        }
        FluidStack held = tank.getFluidInTank(0);
        if (held.isEmpty()) {
            return false;
        }
        boolean moved = false;
        long inserted = insertFluidIntoAe(held.copy());
        if (inserted > 0) {
            tank.drain((int) Math.min(inserted, tank.amount), IFluidHandler.FluidAction.EXECUTE);
            moved = true;
        }
        for (Direction direction : Direction.values()) {
            if (tank.amount <= 0) {
                break;
            }
            IFluidHandler target = level.getCapability(Capabilities.FluidHandler.BLOCK,
                    worldPosition.relative(direction), direction.getOpposite());
            if (target == null) {
                continue;
            }
            int accepted = target.fill(held.copyWithAmount(tank.amount),
                    IFluidHandler.FluidAction.EXECUTE);
            if (accepted > 0) {
                tank.drain(accepted, IFluidHandler.FluidAction.EXECUTE);
                moved = true;
            }
        }
        return moved;
    }

    /** The fluid the machine is currently set to produce: tank 0 of the first filled sample. */
    @Nullable
    private FluidStack firstSampleFluid() {
        IItemHandler input = getInputHandler();
        for (int i = 0; i < INPUT_SLOTS; i++) {
            FluidStack fluid = sampleFluid(input.getStackInSlot(i));
            if (fluid != null) {
                return fluid;
            }
        }
        return null;
    }

    /**
     * Reads the fluid a container item holds, or {@code null} when it holds none. Item capabilities
     * are looked up through the stack so modded tanks and filled bottles work as well as buckets.
     */
    @Nullable
    private static FluidStack sampleFluid(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        IFluidHandlerItem handler = stack.getCapability(Capabilities.FluidHandler.ITEM);
        if (handler == null || handler.getTanks() <= 0) {
            return null;
        }
        FluidStack fluid = handler.getFluidInTank(0);
        return fluid.isEmpty() ? null : fluid.copy();
    }

    // ---------- product tank ----------

    /**
     * Output-only tank for whatever fluid the samples hold. The machine fills it from the inside, so
     * {@link #fill} refuses everything an outside pipe offers and {@link #drain} hands fluid out.
     * The fluid identity follows the first filled input sample, and the capacity follows the yield
     * upgrade, hence the overridden capacity instead of a fixed size.
     */
    private final class ProductTank implements IFluidHandler {

        /** Stored amount in mB; the fluid is resolved from the samples whenever it is needed. */
        private int amount;

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            if (tankFluid == null || amount <= 0) {
                return FluidStack.EMPTY;
            }
            return new FluidStack(tankFluid, amount);
        }

        @Override
        public int getTankCapacity(int tank) {
            return FluidFarmBlockEntity.this.getTankCapacity();
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return tankFluid != null && !stack.isEmpty() && stack.is(tankFluid);
        }

        /** Outside automation may only take fluid out: filling is reserved for the machine. */
        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return 0;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            if (resource.isEmpty() || tankFluid == null || !resource.is(tankFluid)) {
                return FluidStack.EMPTY;
            }
            return drain(resource.getAmount(), action);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            if (tankFluid == null || maxDrain <= 0 || amount <= 0) {
                return FluidStack.EMPTY;
            }
            int drained = Math.min(maxDrain, amount);
            if (action.execute()) {
                amount -= drained;
                if (amount <= 0) {
                    amount = 0;
                    tankFluid = null;
                }
                setChanged();
            }
            return new FluidStack(tankFluid == null ? Fluids.EMPTY : tankFluid, drained);
        }

        /** Internal fill path used by the machine; clamps to the current capacity. */
        private void add(Fluid fluid, int toAdd) {
            if (toAdd <= 0) {
                return;
            }
            if (tankFluid != null && amount > 0 && !fluid.isSame(tankFluid)) {
                // Another fluid is already stored: drain the tank before switching samples.
                return;
            }
            int space = FluidFarmBlockEntity.this.getTankCapacity() - amount;
            int added = Math.min(space, toAdd);
            if (added <= 0) {
                return;
            }
            tankFluid = fluid;
            amount += added;
            setChanged();
        }

        /** Removes fluid that no longer fits after the yield upgrade was pulled out. */
        private void clampToCapacity() {
            int capacity = FluidFarmBlockEntity.this.getTankCapacity();
            if (amount > capacity) {
                amount = capacity;
                setChanged();
            }
            if (amount <= 0) {
                amount = 0;
                tankFluid = null;
            }
        }
    }

    // ---------- MenuProvider ----------

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.simpleautofarm.fluid_farm");
    }

    // ---------- persistence ----------

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        tank.amount = tag.getInt("FluidAmount");
        ResourceLocation tankId = tag.contains(TAG_TANK_FLUID)
                ? ResourceLocation.tryParse(tag.getString(TAG_TANK_FLUID)) : null;
        Fluid stored = tankId == null ? null : BuiltInRegistries.FLUID.get(tankId);
        tankFluid = stored == null || stored == Fluids.EMPTY ? null : stored;
        // The yield upgrade that set the old capacity may be gone by now.
        tank.clampToCapacity();
        if (tag.contains(TAG_DISPLAY_FLUID)) {
            ResourceLocation id = ResourceLocation.tryParse(tag.getString(TAG_DISPLAY_FLUID));
            Fluid fluid = id == null ? null : BuiltInRegistries.FLUID.get(id);
            displayFluid = fluid == null || fluid == Fluids.EMPTY
                    ? FluidStack.EMPTY : new FluidStack(fluid, 1000);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("FluidAmount", tank.amount);
        if (tankFluid != null && tank.amount > 0) {
            tag.putString(TAG_TANK_FLUID, BuiltInRegistries.FLUID.getKey(tankFluid).toString());
        }
    }
}
