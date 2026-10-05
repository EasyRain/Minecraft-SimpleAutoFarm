package com.simpleautofarm.menu;

import com.simpleautofarm.SimpleAutoFarm;
import com.simpleautofarm.block.BeehiveBlockEntity;
import com.simpleautofarm.block.FarmBlockEntity;
import com.simpleautofarm.pb.PbCompat;
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

/**
 * Menu of the simple auto beehive: the farm layout (input row, three-row product band,
 * three upgrade slots) plus one extra slot for Productive Bees' Block Upgrade and a
 * page toggle.  The three-row band is shared: page 0 shows the products, page 1 shows
 * the 27 bee-food storage slots (filtered storage, the machine never eats it).
 */
public class BeehiveMenu extends AbstractContainerMenu {

    /** All machine slots: 9 input + 27 output + 3 upgrade + 27 food + 1 comb upgrade. */
    public static final int CONTAINER_SLOTS = FarmBlockEntity.INPUT_SLOTS + FarmBlockEntity.OUTPUT_SLOTS
            + FarmBlockEntity.UPGRADE_SLOTS + BeehiveBlockEntity.FOOD_SLOTS
            + BeehiveBlockEntity.COMB_UPGRADE_SLOTS;

    /** The comb-block upgrade is added last of the machine's own slots. */
    private static final int COMB_SLOT_INDEX = CONTAINER_SLOTS - 1;

    /**
     * True while no Block Upgrade is installed. The screen shows its "what goes here" hint only
     * then, so an installed upgrade keeps its own tooltip.
     */
    public boolean isCombUpgradeEmpty() {
        return this.getSlot(COMB_SLOT_INDEX).getItem().isEmpty();
    }

    public static final int PAGE_OUTPUTS = 0;
    public static final int PAGE_FOOD = 1;

    // layout (GUI relative), identical to the farm menu
    private static final int INPUT_Y = 18;
    private static final int BAND_Y = 36;          // the shared three-row band
    private static final int UPGRADE_X = 184;
    private static final int UPGRADE_Y = 18;
    private static final int COMB_Y = 72;
    private static final int INVENTORY_Y = 120;
    private static final int HOTBAR_Y = 174;

    private final BeehiveBlockEntity blockEntity;
    private final ContainerData data;
    private int page = PAGE_OUTPUTS;

    // Client constructor, bound by IMenuTypeExtension.create(BeehiveMenu::new)
    public BeehiveMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        this(containerId, playerInventory, buf.readBlockPos());
    }

    public BeehiveMenu(int containerId, Inventory playerInventory, BlockPos pos) {
        super(SimpleAutoFarm.AUTO_BEEHIVE_MENU.get(), containerId);

        Level level = playerInventory.player.level();
        this.blockEntity = level.getBlockEntity(pos) instanceof BeehiveBlockEntity beehive ? beehive : null;

        IItemHandler input;
        IItemHandler output;
        IItemHandler upgrade;
        IItemHandler food;
        IItemHandler comb;
        if (this.blockEntity != null) {
            input = this.blockEntity.getInputHandler();
            output = this.blockEntity.getOutputHandler();
            upgrade = this.blockEntity.getUpgradeHandler();
            food = this.blockEntity.getFoodHandler();
            comb = this.blockEntity.getCombUpgradeHandler();
        } else {
            input = new ItemStackHandler(FarmBlockEntity.INPUT_SLOTS);
            output = new ItemStackHandler(FarmBlockEntity.OUTPUT_SLOTS);
            upgrade = new ItemStackHandler(FarmBlockEntity.UPGRADE_SLOTS);
            food = new ItemStackHandler(BeehiveBlockEntity.FOOD_SLOTS);
            comb = new ItemStackHandler(BeehiveBlockEntity.COMB_UPGRADE_SLOTS);
        }
        // On the server the block entity is the data source; on the client the values arrive
        // via data-slot packets into a fresh SimpleContainerData.
        this.data = (this.blockEntity != null && !level.isClientSide()) ? this.blockEntity : new SimpleContainerData(10);

        // input row: bee cages
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
        // bee food storage: the same three-row band, shown on the food page (page 1).
        // Filtered storage only - the machine never consumes it.
        for (int i = 0; i < BeehiveBlockEntity.FOOD_SLOTS; i++) {
            this.addSlot(new PageFoodSlot(PAGE_FOOD, food, i, 8 + (i % 9) * 18, BAND_Y + (i / 9) * 18));
        }
        // the extra slot for Productive Bees' Block Upgrade
        this.addSlot(new SlotItemHandler(comb, 0, UPGRADE_X, COMB_Y));
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

    /** 0 = products, 1 = bee food; kept on the menu (client side), no data slot needed. */
    public int getPage() {
        return this.page;
    }

    public boolean isFoodPage() {
        return this.page == PAGE_FOOD;
    }

    public void togglePage() {
        this.setPage(this.page == PAGE_OUTPUTS ? PAGE_FOOD : PAGE_OUTPUTS);
    }

    public void setPage(int page) {
        this.page = page == PAGE_FOOD ? PAGE_FOOD : PAGE_OUTPUTS;
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

    /** Whether Productive Bees is installed; the screen shows a hint when it is not. */
    public boolean isResourceBeesLoaded() {
        return this.data.get(7) > 0;
    }

    /** Honey stored in the beehive's tank, in mB. */
    public int getHoneyAmount() {
        return this.data.get(8);
    }

    /** Current honey tank size in mB; grows with the yield upgrade. */
    public int getHoneyCapacity() {
        return this.data.get(9);
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
            final int foodStart = upgradeEnd;
            final int foodEnd = foodStart + BeehiveBlockEntity.FOOD_SLOTS;
            final int combStart = foodEnd;
            final int inventoryStart = CONTAINER_SLOTS;
            final int hotbarStart = inventoryStart + 27;

            if (index < CONTAINER_SLOTS) {
                // machine -> player inventory (hotbar last, vanilla order)
                if (!this.moveItemStackTo(stackInSlot, inventoryStart, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (PbCompat.isCombBlockUpgrade(stackInSlot)) {
                // player -> the comb-block upgrade slot
                if (!this.moveItemStackTo(stackInSlot, combStart, CONTAINER_SLOTS, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (PbCompat.isBeeCage(stackInSlot)) {
                // player -> input row (one cage per slot)
                if (!this.moveItemStackTo(stackInSlot, 0, inputEnd, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (PbCompat.isFoodStorageItem(stackInSlot)) {
                // player -> bee food storage.  Routing hint only: the slots take any item (a feeder
                // holds anything), but shift-click should not dump arbitrary junk into them.
                if (!this.moveItemStackTo(stackInSlot, foodStart, foodEnd, false)) {
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

    /** Product band slots: the player may only take items out, never place them. */
    private static class PageOutputSlot extends PageSlotBase {
        PageOutputSlot(int page, IItemHandler handler, int index, int x, int y) {
            super(page, handler, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }

    /** Bee-food band slots: plain (filtered by the block entity) storage. */
    private static class PageFoodSlot extends PageSlotBase {
        PageFoodSlot(int page, IItemHandler handler, int index, int x, int y) {
            super(page, handler, index, x, y);
        }
    }
}
