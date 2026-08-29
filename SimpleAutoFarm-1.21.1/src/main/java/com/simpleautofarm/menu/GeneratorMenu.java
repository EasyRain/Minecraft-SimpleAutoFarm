package com.simpleautofarm.menu;

import com.simpleautofarm.SimpleAutoFarm;
import com.simpleautofarm.block.GeneratorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

public class GeneratorMenu extends AbstractContainerMenu {

    private final GeneratorBlockEntity blockEntity;
    private final ContainerData data;

    public static final int CONTAINER_SLOTS = 3;

    // Client constructor, bound by IMenuTypeExtension.create(GeneratorMenu::new)
    public GeneratorMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        this(containerId, playerInventory, buf.readBlockPos());
    }

    public GeneratorMenu(int containerId, Inventory playerInventory, BlockPos pos) {
        super(SimpleAutoFarm.GENERATOR_MENU.get(), containerId);

        Level level = playerInventory.player.level();
        this.blockEntity = level.getBlockEntity(pos) instanceof GeneratorBlockEntity generator ? generator : null;

        IItemHandler fuel = this.blockEntity != null ? this.blockEntity.getFuelHandler() : new ItemStackHandler(1);
        IItemHandler upgrade = this.blockEntity != null ? this.blockEntity.getUpgradeHandler() : new ItemStackHandler(GeneratorBlockEntity.UPGRADE_SLOTS);
        this.data = (this.blockEntity != null && !level.isClientSide()) ? this.blockEntity : new SimpleContainerData(8);

        // fuel slot (0)
        this.addSlot(new SlotItemHandler(fuel, 0, 56, 53));

        // upgrade slots (1..2)
        for (int i = 0; i < GeneratorBlockEntity.UPGRADE_SLOTS; i++) {
            this.addSlot(new SlotItemHandler(upgrade, i, 150, 18 + i * 18));
        }

        // player inventory (3..29)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        // hotbar (28..36)
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }

        this.addDataSlots(this.data);
    }

    public int getEnergy() {
        return this.data.get(0);
    }

    public int getBurnTime() {
        return this.data.get(1);
    }

    public int getBurnTimeTotal() {
        return this.data.get(2);
    }

    public int getEnergyCapacity() {
        return this.data.get(3);
    }

    public int getFePerTick() {
        return this.data.get(4);
    }

    public int getFluidAmount() {
        return this.data.get(5);
    }

    public int getFluidCapacity() {
        return this.data.get(6);
    }

    public boolean isCreative() {
        return this.data.get(7) > 0;
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
            if (index < CONTAINER_SLOTS) {
                // fuel / upgrades -> player inventory
                if (!this.moveItemStackTo(stackInSlot, CONTAINER_SLOTS, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (stackInSlot.getBurnTime(null) > 0) {
                // player -> fuel slot (only burnable items)
                if (!this.moveItemStackTo(stackInSlot, 0, 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (SimpleAutoFarm.isUpgradeItem(stackInSlot.getItem())) {
                // player -> upgrade slots
                if (!this.moveItemStackTo(stackInSlot, 1, CONTAINER_SLOTS, false)) {
                    return ItemStack.EMPTY;
                }
            } else {
                return ItemStack.EMPTY;
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
}
