package com.simpleautofarm.menu;

import com.simpleautofarm.SimpleAutoFarm;
import com.simpleautofarm.block.FarmBlockEntity;
import com.simpleautofarm.block.FluidFarmBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * Menu of the simple auto fluid farm. It keeps the farm's sample row, upgrade column and player
 * inventory, but the three product rows are gone: one row of container slots takes their top row
 * (empty buckets, bottles and tank items the machine fills from the tank) and a vertical tank gauge
 * takes the rest, so there is nothing to page through.
 */
public class FluidFarmMenu extends AbstractContainerMenu {

    /** All machine slots: 9 samples + 9 container slots + 3 upgrade slots. */
    public static final int CONTAINER_SLOTS = FarmBlockEntity.INPUT_SLOTS
            + FluidFarmBlockEntity.CONTAINER_SLOTS + FarmBlockEntity.UPGRADE_SLOTS;

    /** Slot index bounds of the three machine partitions (also used by quickMoveStack). */
    private static final int SAMPLE_END = FarmBlockEntity.INPUT_SLOTS;
    private static final int CONTAINER_END = SAMPLE_END + FluidFarmBlockEntity.CONTAINER_SLOTS;
    private static final int UPGRADE_END = CONTAINER_END + FarmBlockEntity.UPGRADE_SLOTS;

    // layout (GUI relative), identical to the farm menu
    private static final int SAMPLE_Y = 18;
    private static final int CONTAINER_Y = 36;
    private static final int UPGRADE_X = 184;
    private static final int UPGRADE_Y = 18;
    private static final int INVENTORY_Y = 120;
    private static final int HOTBAR_Y = 174;

    private final FluidFarmBlockEntity blockEntity;
    private final ContainerData data;

    // Client constructor, bound by IMenuTypeExtension.create(FluidFarmMenu::new)
    public FluidFarmMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        this(containerId, playerInventory, buf.readBlockPos());
    }

    public FluidFarmMenu(int containerId, Inventory playerInventory, BlockPos pos) {
        super(SimpleAutoFarm.FLUID_FARM_MENU.get(), containerId);

        Level level = playerInventory.player.level();
        this.blockEntity = level.getBlockEntity(pos) instanceof FluidFarmBlockEntity fluidFarm ? fluidFarm : null;

        IItemHandler samples;
        IItemHandler containers;
        IItemHandler upgrades;
        if (this.blockEntity != null) {
            samples = this.blockEntity.getInputHandler();
            containers = this.blockEntity.getContainerHandler();
            upgrades = this.blockEntity.getUpgradeHandler();
        } else {
            samples = new ItemStackHandler(FarmBlockEntity.INPUT_SLOTS);
            containers = new ItemStackHandler(FluidFarmBlockEntity.CONTAINER_SLOTS);
            upgrades = new ItemStackHandler(FarmBlockEntity.UPGRADE_SLOTS);
        }
        // On the server the block entity is the data source; on the client the values arrive
        // via data-slot packets into a fresh SimpleContainerData.
        this.data = (this.blockEntity != null && !level.isClientSide()) ? this.blockEntity : new SimpleContainerData(10);

        // sample row: filled fluid containers used as the production marker
        for (int i = 0; i < FarmBlockEntity.INPUT_SLOTS; i++) {
            this.addSlot(new SampleSlot(samples, i, 8 + i * 18, SAMPLE_Y));
        }
        // container row: empty containers the machine fills from the tank
        for (int i = 0; i < FluidFarmBlockEntity.CONTAINER_SLOTS; i++) {
            this.addSlot(new ContainerSlot(containers, i, 8 + i * 18, CONTAINER_Y));
        }
        // upgrade slots, right of the panel
        for (int i = 0; i < FarmBlockEntity.UPGRADE_SLOTS; i++) {
            this.addSlot(new SlotItemHandler(upgrades, i, UPGRADE_X, UPGRADE_Y + i * 18));
        }
        // player inventory
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, INVENTORY_Y + row * 18));
            }
        }
        // hotbar
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, HOTBAR_Y));
        }

        this.addDataSlots(this.data);
    }

    public int getEnergy() {
        return this.data.get(0);
    }

    public int getProgress() {
        return this.data.get(1);
    }

    public boolean isAutoEject() {
        return this.data.get(2) > 0;
    }

    public int getEnergyCapacity() {
        return this.data.get(3);
    }

    public int getProductionTicks() {
        return this.data.get(4);
    }

    public int getEnergyConsumptionPerTick() {
        return this.data.get(5);
    }

    public boolean isAeConnected() {
        return this.data.get(6) > 0;
    }

    /** Fluid stored in the tank, in mB. */
    public int getFluidAmount() {
        return this.data.get(7);
    }

    /** Current tank size in mB; grows with the yield upgrade. */
    public int getFluidCapacity() {
        return this.data.get(8);
    }

    /** The tank's content (fluid identity plus amount), or an empty stack while it is dry. */
    public FluidStack getFluidStack() {
        int amount = this.getFluidAmount();
        int id = this.data.get(9);
        if (amount <= 0 || id < 0) {
            return FluidStack.EMPTY;
        }
        Fluid fluid = BuiltInRegistries.FLUID.byId(id);
        return fluid == null ? FluidStack.EMPTY : new FluidStack(fluid, amount);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == 0 && this.blockEntity != null) {
            this.blockEntity.toggleAutoEject();
            return true;
        }
        return false;
    }

    @Override
    public boolean stillValid(Player player) {
        if (this.blockEntity == null) {
            return false;
        }
        BlockPos pos = this.blockEntity.getBlockPos();
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stackInSlot = slot.getItem();
            result = stackInSlot.copy();

            final int inventoryStart = CONTAINER_SLOTS;
            final int hotbarStart = inventoryStart + 27;

            if (index < CONTAINER_SLOTS) {
                // machine -> player inventory (hotbar last, vanilla order)
                if (!this.moveItemStackTo(stackInSlot, inventoryStart, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (this.isSample(stackInSlot)) {
                // player -> sample row (one marker per slot); a filled container is a sample first
                if (!this.moveItemStackTo(stackInSlot, 0, SAMPLE_END, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (FluidFarmBlockEntity.isFluidContainer(stackInSlot)) {
                // player -> container row (the machine fills it in place)
                if (!this.moveItemStackTo(stackInSlot, SAMPLE_END, CONTAINER_END, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (SimpleAutoFarm.isUpgradeItem(stackInSlot.getItem())) {
                // player -> upgrade slots
                if (!this.moveItemStackTo(stackInSlot, CONTAINER_END, UPGRADE_END, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (index < hotbarStart) {
                // anything else: main inventory -> hotbar
                if (!this.moveItemStackTo(stackInSlot, hotbarStart, this.slots.size(), false)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // anything else: hotbar -> main inventory
                if (!this.moveItemStackTo(stackInSlot, inventoryStart, hotbarStart, false)) {
                    return ItemStack.EMPTY;
                }
            }

            if (stackInSlot.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }

            if (stackInSlot.getCount() == result.getCount()) {
                return ItemStack.EMPTY;
            }
            slot.onTake(player, stackInSlot);
        }
        return result;
    }

    /** Whether the machine accepts the stack as a sample; the block entity owns that rule. */
    private boolean isSample(ItemStack stack) {
        return this.blockEntity != null
                ? this.blockEntity.isValidSample(stack)
                : FluidFarmBlockEntity.isFluidContainer(stack);
    }

    /** Sample slots: only fluid containers the machine accepts as a marker (the block entity decides). */
    private class SampleSlot extends SlotItemHandler {
        SampleSlot(IItemHandler handler, int index, int x, int y) {
            super(handler, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return isSample(stack);
        }
    }

    /** Container slots: only items that carry a fluid item handler can be filled or collected. */
    private static class ContainerSlot extends SlotItemHandler {
        ContainerSlot(IItemHandler handler, int index, int x, int y) {
            super(handler, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return FluidFarmBlockEntity.isFluidContainer(stack);
        }
    }
}
