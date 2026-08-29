package com.simpleautofarm.block;

import com.simpleautofarm.SimpleAutoFarm;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * The Pro generator: identical to the regular generator except its base energy values are 10x
 * and it burns lava fluid as fuel (item fuels are auto-converted into lava).
 *
 * <p>Lava is consumed by amount: 1 mB per second (0.05 mB/tick) at base, scaled by the fuel
 * consumption multiplier from upgrades. 1000 mB therefore burns for 1000 seconds.
 */
public class GeneratorProBlockEntity extends GeneratorBlockEntity {

    /** Lava consumed per real tick at base (1 mB/second). */
    private static final double LAVA_MB_PER_TICK = 0.05;
    /** Burn-time ticks equivalent to 1 mB of lava (1 mB = 1 second = 20 ticks). */
    private static final int TICKS_PER_MB = 20;
    /** Internal lava tank capacity: 16 buckets. */
    private static final int FLUID_CAPACITY = 16_000;

    private final FluidTank fluidTank = new FluidTank(FLUID_CAPACITY, fluid -> fluid.is(Fluids.LAVA)) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };

    /** Fractional lava consumption accumulated toward the next whole mB drain. */
    private double lavaBurnBuffer;

    public GeneratorProBlockEntity(BlockPos pos, BlockState blockState) {
        super(SimpleAutoFarm.GENERATOR_PRO_BLOCK_ENTITY.get(), pos, blockState);
    }

    @Override
    protected int getBaseFePerTick() {
        return 2_500;
    }

    @Override
    protected int getBaseEnergyCapacity() {
        return 100_000;
    }

    @Override
    protected int getBaseMaxExtract() {
        return 10_000;
    }

    @Override
    protected int getBasePushRate() {
        return 5_000;
    }

    @Override
    public int getFluidAmount() {
        return fluidTank.getFluidAmount();
    }

    @Override
    public int getFluidCapacity() {
        return fluidTank.getCapacity();
    }

    @Override
    protected void tickServer(Level level) {
        energyStorage.clampEnergy();
        boolean changed = false;

        if (hasCreativeUpgrade()) {
            // creative: produces energy every tick with no lava/fuel required
            if (energyStorage.getEnergyStored() < energyStorage.getMaxEnergyStored()) {
                energyStorage.addEnergy(getFePerTick());
                burnTime = 1;
                burnTimeTotal = 1;
                changed = true;
            }
        } else {
            // 1) auto-convert any item fuel in the fuel slot into lava
            if (convertItemFuelToLava()) {
                changed = true;
            }

            // 2) burn lava continuously by amount (pauses when the buffer is full)
            boolean burning = false;
            if (energyStorage.getEnergyStored() < energyStorage.getMaxEnergyStored() && fluidTank.getFluidAmount() > 0) {
                energyStorage.addEnergy(getFePerTick());
                lavaBurnBuffer += LAVA_MB_PER_TICK * getFuelUsePerTick();
                int drain = (int) Math.floor(lavaBurnBuffer);
                if (drain > 0) {
                    int actually = Math.min(drain, fluidTank.getFluidAmount());
                    fluidTank.drain(actually, FluidAction.EXECUTE);
                    lavaBurnBuffer -= actually;
                }
                burning = true;
                changed = true;
            }
            burnTime = burning ? 1 : 0;
            burnTimeTotal = burning ? 1 : 0;
        }

        // 3) push energy to adjacent machines
        if (energyStorage.getEnergyStored() > 0 && pushEnergy(level) > 0) {
            changed = true;
        }

        if (changed) {
            setChanged();
        }
    }

    /** Converts the first burnable item in the fuel slot into its lava equivalent (burnTicks / 20 mB). */
    private boolean convertItemFuelToLava() {
        IItemHandler fuel = getFuelHandler();
        ItemStack fuelStack = fuel.getStackInSlot(0);
        if (fuelStack.isEmpty()) {
            return false;
        }
        int burnTicks = fuelStack.getBurnTime(null);
        if (burnTicks <= 0) {
            return false;
        }
        int lavaMb = burnTicks / TICKS_PER_MB;
        if (lavaMb <= 0) {
            return false;
        }
        if (fluidTank.getCapacity() - fluidTank.getFluidAmount() < lavaMb) {
            return false; // not enough space; wait rather than waste the item
        }
        fluidTank.fill(new FluidStack(Fluids.LAVA, lavaMb), FluidAction.EXECUTE);
        fuel.extractItem(0, 1, false);
        return true;
    }

    public FluidTank getFluidTank() {
        return fluidTank;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.simpleautofarm.generator_pro");
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Fluid")) {
            fluidTank.readFromNBT(registries, tag.getCompound("Fluid"));
        }
        lavaBurnBuffer = tag.getDouble("LavaBurnBuffer");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Fluid", fluidTank.writeToNBT(registries, new CompoundTag()));
        tag.putDouble("LavaBurnBuffer", lavaBurnBuffer);
    }
}
