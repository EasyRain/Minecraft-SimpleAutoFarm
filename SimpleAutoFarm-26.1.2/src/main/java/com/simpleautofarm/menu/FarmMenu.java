package com.simpleautofarm.menu;

import com.simpleautofarm.SimpleAutoFarm;
import com.simpleautofarm.block.FarmBlockEntity;
import com.simpleautofarm.util.SeedHelper;
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

public class FarmMenu extends AbstractContainerMenu {

    /** Total number of machine slots (9 input + 27 output). */
    public static final int CONTAINER_SLOTS = FarmBlockEntity.INPUT_SLOTS + FarmBlockEntity.OUTPUT_SLOTS + FarmBlockEntity.UPGRADE_SLOTS;

    private final FarmBlockEntity blockEntity;

    /** Which product page is shown: 0 = main band, 1 = overflow. */
    private int page;
    /** True when this machine actually has an overflow page (the beehive does not). */
    private final boolean overflowPage;
    private final ContainerData data;

    // Client constructor, bound by IMenuTypeExtension.create(FarmMenu::new)
    public FarmMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        this(containerId, playerInventory, buf.readBlockPos());
    }

    public FarmMenu(int containerId, Inventory playerInventory, BlockPos pos) {
        super(SimpleAutoFarm.AUTO_FARM_MENU.get(), containerId);

        Level level = playerInventory.player.level();
        this.blockEntity = level.getBlockEntity(pos) instanceof FarmBlockEntity farm ? farm : null;

        IItemHandler input;
        IItemHandler output;
        IItemHandler upgrade;
        if (this.blockEntity != null) {
            input = this.blockEntity.getInputHandler();
            output = this.blockEntity.getOutputHandler();
            upgrade = this.blockEntity.getUpgradeHandler();
        } else {
            input = new ItemStackHandler(FarmBlockEntity.INPUT_SLOTS);
            output = new ItemStackHandler(FarmBlockEntity.OUTPUT_SLOTS);
            upgrade = new ItemStackHandler(FarmBlockEntity.UPGRADE_SLOTS);
        }
        // On the server the block entity is the data source; on the client the values arrive
        // via data-slot packets into a fresh SimpleContainerData.
        this.data = (this.blockEntity != null && !level.isClientSide()) ? this.blockEntity : new SimpleContainerData(7);

        // input row (slots 0..8)
        for (int i = 0; i < FarmBlockEntity.INPUT_SLOTS; i++) {
            this.addSlot(new SlotItemHandler(input, i, 8 + i * 18, 18));
        }
        // output rows (slots 9..35): three rows of nine
        for (int i = 0; i < FarmBlockEntity.OUTPUT_SLOTS; i++) {
            int row = i / 9;
            int col = i % 9;
            this.addSlot(new PageOutputSlot(0, output, i, 8 + col * 18, 36 + row * 18));
        }
        // second product page: the same three-row band, only shown on page 1
        IItemHandler overflow = this.blockEntity == null ? null : this.blockEntity.getOverflowPageHandler();
        this.overflowPage = overflow != null;
        if (overflow != null) {
            for (int i = 0; i < FarmBlockEntity.OUTPUT_SLOTS; i++) {
                int row = i / 9;
                int col = i % 9;
                this.addSlot(new PageOutputSlot(1, overflow, i, 8 + col * 18, 36 + row * 18));
            }
        }
        // upgrade slots (36..38), right of the panel
        for (int i = 0; i < FarmBlockEntity.UPGRADE_SLOTS; i++) {
            this.addSlot(new SlotItemHandler(upgrade, i, 184, 18 + i * 18));
        }
        // player inventory (slots 39..65)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 120 + row * 18));
            }
        }
        // hotbar (slots 63..71)
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 174));
        }

        this.addDataSlots(this.data);
        this.applyPage();
    }

    /** Whether this machine has a second product page (the beehive does not). */
    public boolean hasOverflowPage() {
        return this.overflowPage;
    }

    public boolean isOverflowPage() {
        return this.page == 1;
    }

    public void togglePage() {
        this.page = this.page == 0 ? 1 : 0;
        this.applyPage();
    }

    /** Hides the other page's slots so they cannot be seen or clicked. */
    private void applyPage() {
        for (Slot slot : this.slots) {
            if (slot instanceof PageSlotBase pageSlot) {
                pageSlot.setShown(pageSlot.page == this.page);
            }
        }
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

            final int machineEnd = this.slots.size() - 36;
            final int inputEnd = FarmBlockEntity.INPUT_SLOTS;
            final int upgradeStart = FarmBlockEntity.INPUT_SLOTS + FarmBlockEntity.OUTPUT_SLOTS;
            final int inventoryStart = machineEnd;
            final int hotbarStart = inventoryStart + 27;

            if (index < machineEnd) {
                // machine -> player inventory (hotbar last, vanilla order)
                if (!this.moveItemStackTo(stackInSlot, inventoryStart, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (isSample(stackInSlot)) {
                // player -> input row (one sample per slot)
                if (!this.moveItemStackTo(stackInSlot, 0, inputEnd, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (SimpleAutoFarm.isUpgradeItem(stackInSlot.getItem())) {
                // player -> upgrade slots
                if (!this.moveItemStackTo(stackInSlot, upgradeStart, machineEnd, false)) {
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

    /**
     * Whether the machine behind this menu accepts the stack as a sample. The machine decides
     * (plants for the crop farm, ores for the ore farm) so shift-click and the slot rule agree.
     */
    private boolean isSample(ItemStack stack) {
        return this.blockEntity != null ? this.blockEntity.isValidSample(stack) : SeedHelper.isPlant(stack);
    }

    /** A product-band slot of one page; the hidden page's slots report inactive. */
    private static class PageSlotBase extends SlotItemHandler {

        private final int page;
        private boolean shown = true;

        PageSlotBase(int page, IItemHandler itemHandler, int index, int x, int y) {
            super(itemHandler, index, x, y);
            this.page = page;
        }

        void setShown(boolean shown) {
            this.shown = shown;
        }

        @Override
        public boolean isActive() {
            return this.shown;
        }
    }

    /** Page output slots: the player may only take items out, never place them. */
    private static class PageOutputSlot extends PageSlotBase {

        PageOutputSlot(int page, IItemHandler itemHandler, int index, int x, int y) {
            super(page, itemHandler, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }

    /** Output slots: the player may only take items out, never place them. */
    private static class OutputSlot extends SlotItemHandler {
        public OutputSlot(IItemHandler itemHandler, int index, int x, int y) {
            super(itemHandler, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }
}
