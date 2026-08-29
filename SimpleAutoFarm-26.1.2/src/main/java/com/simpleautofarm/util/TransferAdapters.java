package com.simpleautofarm.util;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Adapters that expose the deprecated {@link IEnergyStorage}/{@link IItemHandler}/{@link IFluidHandler}
 * implementations used internally by this mod as the new {@code EnergyHandler}/{@code ResourceHandler}
 * capability types, so the machines can be registered against the 26.1.2 capability system.
 *
 * <p>Transactions are intentionally ignored: this mod's handlers perform real (non-simulated)
 * transfers, which is sufficient for its own machines and the compile objective.
 */
public final class TransferAdapters {

    private TransferAdapters() {
    }

    public static EnergyHandler energy(IEnergyStorage storage) {
        return new EnergyHandler() {
            @Override
            public long getAmountAsLong() {
                return storage.getEnergyStored();
            }

            @Override
            public long getCapacityAsLong() {
                return storage.getMaxEnergyStored();
            }

            @Override
            public int insert(int amount, TransactionContext transaction) {
                return storage.receiveEnergy(amount, false);
            }

            @Override
            public int extract(int amount, TransactionContext transaction) {
                return storage.extractEnergy(amount, false);
            }
        };
    }

    public static ResourceHandler<ItemResource> items(IItemHandler handler) {
        return new ResourceHandler<>() {
            @Override
            public int size() {
                return handler.getSlots();
            }

            @Override
            public ItemResource getResource(int index) {
                return ItemResource.of(handler.getStackInSlot(index));
            }

            @Override
            public long getAmountAsLong(int index) {
                return handler.getStackInSlot(index).getCount();
            }

            @Override
            public long getCapacityAsLong(int index, ItemResource resource) {
                return handler.getSlotLimit(index);
            }

            @Override
            public boolean isValid(int index, ItemResource resource) {
                return handler.isItemValid(index, resource.toStack());
            }

            @Override
            public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
                ItemStack remaining = handler.insertItem(index, resource.toStack(amount), false);
                return amount - remaining.getCount();
            }

            @Override
            public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
                return handler.extractItem(index, amount, false).getCount();
            }
        };
    }

    public static ResourceHandler<FluidResource> fluids(IFluidHandler handler) {
        return new ResourceHandler<>() {
            @Override
            public int size() {
                return handler.getTanks();
            }

            @Override
            public FluidResource getResource(int index) {
                return FluidResource.of(handler.getFluidInTank(index));
            }

            @Override
            public long getAmountAsLong(int index) {
                return handler.getFluidInTank(index).getAmount();
            }

            @Override
            public long getCapacityAsLong(int index, FluidResource resource) {
                return handler.getTankCapacity(index);
            }

            @Override
            public boolean isValid(int index, FluidResource resource) {
                return handler.isFluidValid(index, resource.toStack(1));
            }

            @Override
            public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
                return handler.fill(resource.toStack(amount), FluidAction.EXECUTE);
            }

            @Override
            public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
                FluidStack drained = handler.drain(resource.toStack(amount), FluidAction.EXECUTE);
                return drained.getAmount();
            }
        };
    }
}
