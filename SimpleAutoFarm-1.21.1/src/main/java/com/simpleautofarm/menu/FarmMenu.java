package com.simpleautofarm.menu;

import com.simpleautofarm.SimpleAutoFarm;
import com.simpleautofarm.block.FarmBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * Menu of the auto farm (also used by the ore farm): the farm layout (input row, three-row product
 * band, three upgrade slots) plus a page toggle. Both pages are product storage: page 0 is the
 * machine's main 27-slot output band, page 1 is the 27-slot overflow band that takes whatever the
 * first one cannot hold. The page only changes which half is visible, never what the machine
 * produces.
 */
public class FarmMenu extends AbstractContainerMenu {

    /** All machine slots: 9 input + 27 output + 3 upgrade + 27 overflow. */
    public static final int CONTAINER_SLOTS = FarmBlockEntity.INPUT_SLOTS + FarmBlockEntity.OUTPUT_SLOTS
            + FarmBlockEntity.UPGRADE_SLOTS + FarmBlockEntity.OUTPUT_SLOTS;

    public static final int PAGE_OUTPUTS = 0;
    public static final int PAGE_OVERFLOW = 1;

    // layout (GUI relative), identical to the mob farm menu
    private static final int INPUT_Y = 18;
    private static final int BAND_Y = 36;
    private static final int UPGRADE_X = 184;
    private static final int UPGRADE_Y = 18;
    private static final int INVENTORY_Y = 120;
    private static final int HOTBAR_Y = 174;

    private final FarmBlockEntity blockEntity;
    private final ContainerData data;
    private int page = PAGE_OUTPUTS;

    // Client constructor, bound by IMenuTypeExtension.create(FarmMenu::new)
    public FarmMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        this(containerId, playerInventory, buf.readBlockPos());
    }

    public FarmMenu(int containerId, Inventory playerInventory, BlockPos pos) {
        // Must be AUTO_FARM_MENU: the open-screen packet carries this menu type, so the client opens
        // the registered FarmScreen. With MOB_FARM_MENU here the farm silently opened MobFarmScreen
        // (the beehive-style 6-row side panel) and every FarmScreen edit looked like it did nothing.
        super(SimpleAutoFarm.AUTO_FARM_MENU.get(), containerId);

        Level level = playerInventory.player.level();
        this.blockEntity = level.getBlockEntity(pos) instanceof FarmBlockEntity farm ? farm : null;

        IItemHandler input;
        IItemHandler output;
        IItemHandler upgrade;
        IItemHandler overflow;
        if (this.blockEntity != null) {
            input = this.blockEntity.getInputHandler();
            output = this.blockEntity.getOutputHandler();
            upgrade = this.blockEntity.getUpgradeHandler();
            overflow = this.blockEntity.getOverflowPageHandler();
        } else {
            input = new ItemStackHandler(FarmBlockEntity.INPUT_SLOTS);
            output = new ItemStackHandler(FarmBlockEntity.OUTPUT_SLOTS);
            upgrade = new ItemStackHandler(FarmBlockEntity.UPGRADE_SLOTS);
            overflow = new ItemStackHandler(FarmBlockEntity.OUTPUT_SLOTS);
        }
        // On the server the block entity is the data source; on the client the values arrive
        // via data-slot packets into a fresh SimpleContainerData.
        this.data = (this.blockEntity != null && !level.isClientSide()) ? this.blockEntity : new SimpleContainerData(7);

        // input row: samples (seeds for the farm, ores for the ore farm)
        for (int i = 0; i < FarmBlockEntity.INPUT_SLOTS; i++) {
            this.addSlot(new SlotItemHandler(input, i, 8 + i * 18, INPUT_Y));
        }
        // product band, shown on the products page (page 0)
        for (int i = 0; i < FarmBlockEntity.OUTPUT_SLOTS; i++) {
            this.addSlot(new PageOutputSlot(PAGE_OUTPUTS, output, i, 8 + (i % 9) * 18, BAND_Y + (i / 9) * 18));
        }
        // upgrade slots, right of the panel
        for (int i = 0; i < FarmBlockEntity.UPGRADE_SLOTS; i++) {
            this.addSlot(new SlotItemHandler(upgrade, i, UPGRADE_X, UPGRADE_Y + i * 18));
        }
        // overflow band: the same three-row band, shown on the overflow page (page 1)
        for (int i = 0; i < FarmBlockEntity.OUTPUT_SLOTS; i++) {
            this.addSlot(new PageOutputSlot(PAGE_OVERFLOW, overflow, i, 8 + (i % 9) * 18, BAND_Y + (i / 9) * 18));
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
        this.applyPage();
    }

    /** 0 = products, 1 = overflow; kept on the menu (client side), no data slot needed. */
    public int getPage() {
        return this.page;
    }

    public boolean isOverflowPage() {
        return this.page == PAGE_OVERFLOW;
    }

    public void togglePage() {
        this.setPage(this.page == PAGE_OUTPUTS ? PAGE_OVERFLOW : PAGE_OUTPUTS);
    }

    public void setPage(int page) {
        this.page = page == PAGE_OVERFLOW ? PAGE_OVERFLOW : PAGE_OUTPUTS;
        this.applyPage();
    }

    /** Moves the slots of the hidden page out of the way so they cannot be seen or clicked. */
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

            final int inputEnd = FarmBlockEntity.INPUT_SLOTS;
            final int upgradeStart = inputEnd + FarmBlockEntity.OUTPUT_SLOTS;
            final int upgradeEnd = upgradeStart + FarmBlockEntity.UPGRADE_SLOTS;
            final int inventoryStart = CONTAINER_SLOTS;
            final int hotbarStart = inventoryStart + 27;

            if (index < CONTAINER_SLOTS) {
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
                if (!this.moveItemStackTo(stackInSlot, upgradeStart, upgradeEnd, false)) {
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
     * True when the machine accepts the item as a sample: spawn eggs, spawner items with a stored
     * mob, mob jars, activated / random entity cards and similar. Falls back to the spawn egg check
     * when the block entity is not available (the server always has it, shift-clicks are handled
     * there).
     */
    private boolean isSample(ItemStack stack) {
        return this.blockEntity != null
                ? this.blockEntity.isValidSample(stack)
                : stack.getItem() instanceof SpawnEggItem;
    }

    /** A band slot that belongs to one page only; the hidden page's slots report inactive. */
    private abstract static class PageSlotBase extends SlotItemHandler {

        private final int page;
        private boolean shown = true;

        PageSlotBase(int page, IItemHandler handler, int index, int x, int y) {
            super(handler, index, x, y);
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

    /** Product band slots (both pages): the player may only take items out, never place them. */
    private static class PageOutputSlot extends PageSlotBase {
        PageOutputSlot(int page, IItemHandler handler, int index, int x, int y) {
            super(page, handler, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }
}
