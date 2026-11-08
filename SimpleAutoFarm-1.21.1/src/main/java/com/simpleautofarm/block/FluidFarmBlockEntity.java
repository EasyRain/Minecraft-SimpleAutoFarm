package com.simpleautofarm.block;

import com.simpleautofarm.SimpleAutoFarm;
import com.simpleautofarm.menu.FluidFarmMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidActionResult;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.CombinedInvWrapper;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple Auto Fluid Farm: the crop farm's machinery (FE energy, speed/efficiency/yield/fortune/
 * creative upgrades, auto-eject, AE2 output) fed with fluid containers instead of seeds.
 *
 * <p>Samples are container items that hold a non-empty fluid (a water bucket, a lava bucket, a
 * filled bottle, a filled tank item...). Like the ore farm's ores, a sample is only a marker: it is
 * never consumed and defines <b>which</b> fluid the machine produces. The machine runs nine of them
 * in parallel, so every sample slot owns one product tank and the container slot below it: slot i
 * fills tank i and is the only slot that can draw from tank i. Every finished batch adds
 * {@link #FLUID_PER_SAMPLE} mB per occupied sample slot to that slot's tank, and a tank can be
 * drained by pipes, AE2 or right-clicking it with an empty container.
 *
 * <p>The container row below the samples is the "bottling" half of the machine: empty buckets,
 * bottles and tank items put there are filled straight from the tank of their own column
 * ({@link #fillContainers()}) and stay in their slot as the filled item until the player collects
 * them.
 */
public class FluidFarmBlockEntity extends FarmBlockEntity {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Fluid a single sample adds per finished batch, before the yield / fortune upgrades. */
    public static final int FLUID_PER_SAMPLE = 250;

    /** Base tank size in mB: 16 buckets, scaled by the yield upgrade like the item output is. */
    public static final int BASE_CAPACITY = 16_000;

    /** One product tank per sample slot: the nine columns run independently. */
    public static final int TANK_SLOTS = FarmBlockEntity.INPUT_SLOTS;

    /** Container row below the samples: empty buckets, bottles and tank items go here to be filled. */
    public static final int CONTAINER_SLOTS = 9;

    /**
     * Menu data slots: the seven farm values first, then nine amounts, then nine capacities (one pair
     * per tank, so the screen can render nine bars from the data slots alone).
     */
    public static final int DATA_AMOUNT_BASE = 7;
    public static final int DATA_CAPACITY_BASE = DATA_AMOUNT_BASE + TANK_SLOTS;
    public static final int DATA_SIZE = DATA_CAPACITY_BASE + TANK_SLOTS;

    /** Ticks between two filling passes; staggered by position like the display refresh. */
    private static final int FILL_INTERVAL = 10;

    /** Capacity cap: the creative upgrade's unbounded multiplier would be unreadable in the title. */
    private static final int MAX_CAPACITY_MULTIPLIER = 64;

    /** Sync tag key: registry name of the fluid the casing should display (client-side colour). */
    private static final String TAG_DISPLAY_FLUID = "DisplayFluid";

    /** Sync tag key: one registry name per tank, empty for a dry tank (the GUI needs the identity). */
    private static final String TAG_TANK_FLUIDS = "TankFluids";

    /** Saved tag key: the nine tanks, one compound each with the fluid id and the amount. */
    private static final String TAG_TANKS = "Tanks";

    /** Saved keys of the single-tank version, read once so an old save keeps its content. */
    private static final String TAG_LEGACY_AMOUNT = "FluidAmount";
    private static final String TAG_LEGACY_FLUID = "TankFluid";

    /** The nine product tanks: filled while the machine runs, drained by pipes, never filled outside. */
    private final SlotTank[] tanks = new SlotTank[TANK_SLOTS];

    /** The nine tanks as one handler for pipes, AE2 and the block's bucket interaction. */
    private final IFluidHandler productHandler = new ProductHandler();

    /** Fluid the casing shows; on the client this comes from the block entity sync tag. */
    private FluidStack displayFluid = FluidStack.EMPTY;

    /** Tank identities the clients were last sent, so a changed tank refreshes the GUI exactly once. */
    private final Fluid[] syncedTankFluids = new Fluid[TANK_SLOTS];

    /**
     * The container row: one item per slot, because a filled container replaces the empty one in
     * place and a stack would lose the rest of the group.
     */
    private final ItemStackHandler containerHandler = new ItemStackHandler(CONTAINER_SLOTS) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return isFluidContainer(stack);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    /**
     * The machine as automation sees it: the nine container slots only. Pipes may feed empty buckets in
     * and pull the filled ones out, while the samples and the upgrade cards stay invisible.
     */
    private final IItemHandler automationHandler = new ContainerAutomationHandler();

    public FluidFarmBlockEntity(BlockPos pos, BlockState state) {
        super(SimpleAutoFarm.FLUID_FARM_BLOCK_ENTITY.get(), pos, state);
        for (int i = 0; i < TANK_SLOTS; i++) {
            this.tanks[i] = new SlotTank();
        }
    }

    /** Container slots as the menu sees them. */
    public IItemHandler getContainerHandler() {
        return containerHandler;
    }

    @Override
    public IItemHandler getItemHandler() {
        return automationHandler;
    }

    /** Exposes only the container row, with insert and extract both allowed. */
    private final class ContainerAutomationHandler implements IItemHandler {

        @Override
        public int getSlots() {
            return containerHandler.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return containerHandler.getStackInSlot(slot);
        }

        @Override
        public int getSlotLimit(int slot) {
            return containerHandler.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return isFluidContainer(stack);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return containerHandler.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return containerHandler.extractItem(slot, amount, simulate);
        }
    }

    /** Anything an item fluid handler can fill: empty bucket, empty bottle, empty tank item... */
    public static boolean isFluidContainer(ItemStack stack) {
        return !stack.isEmpty() && stack.getCapability(Capabilities.FluidHandler.ITEM) != null;
    }

    /**
     * Fills every container slot from the tank of its own column: the empty container is swapped for
     * its filled result in the same slot, so the player only has to collect it. Runs from the block
     * ticker, staggered like {@link #refreshDisplay()}; a column with an empty tank is skipped, and a
     * container whose slot has no sample simply keeps sitting there.
     */
    public void fillContainers() {
        if (level == null || level.isClientSide() || !hasAnyFluid()) {
            return;
        }
        if (Math.floorMod(level.getGameTime() + worldPosition.asLong(), FILL_INTERVAL) != 0) {
            return;
        }
        for (int i = 0; i < CONTAINER_SLOTS && i < TANK_SLOTS; i++) {
            ItemStack container = containerHandler.getStackInSlot(i);
            if (container.isEmpty() || tanks[i].amount <= 0) {
                continue;
            }
            // tryFillContainer works on a copy and drains the tank only on success; its result is
            // the filled container item, which replaces the empty one in place. A successful action
            // may carry an empty result (consumable containers), which clears the slot.
            FluidActionResult filled = FluidUtil.tryFillContainer(container, tanks[i],
                    Integer.MAX_VALUE, null, true);
            if (filled.isSuccess()) {
                containerHandler.setStackInSlot(i, filled.getResult());
            }
        }
    }

    /** Only fluid containers with at least one non-empty tank belong in the input slots. */
    @Override
    public boolean isValidSample(ItemStack stack) {
        return sampleFluid(stack) != null;
    }

    /** The fluid farm produces no items at all: every product goes into the internal tanks. */
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
     * One batch fills the tank of every occupied sample slot with {@link #FLUID_PER_SAMPLE} mB of
     * that slot's own sample fluid, multiplied by the yield and fortune upgrades exactly like the
     * item output is. The nine columns are independent, so nine different fluids run in parallel.
     * {@code planted} is not needed: the slots are walked anyway, because each one has its own fluid.
     */
    @Override
    protected void onBatchProduced(Level level, int planted) {
        IItemHandler input = getInputHandler();
        int stored = 0;
        for (int i = 0; i < TANK_SLOTS; i++) {
            ItemStack sample = input.getStackInSlot(i);
            if (sample.isEmpty()) {
                continue;
            }
            FluidStack fluid = sampleFluid(sample);
            if (fluid == null) {
                LOGGER.debug("Fluid farm at {}: slot {} holds {} but no fluid could be read from it",
                        worldPosition, i, sample.getHoverName().getString());
                continue;
            }
            long amount = (long) FLUID_PER_SAMPLE * getProductionMultiplier() * rollFortune(level);
            int perSlot = (int) Math.min(Integer.MAX_VALUE, amount);
            if (!tanks[i].add(fluid.getFluid(), perSlot)) {
                // Slot i only ever feeds tank i: a tank holding another fluid blocks just this slot.
                LOGGER.debug("Fluid farm at {}: slot {} is blocked (tank holds another fluid)", worldPosition, i);
                continue;
            }
            stored++;
        }
        LOGGER.debug("Fluid farm at {}: batch done, planted={} stored={}", worldPosition, planted, stored);
        if (stored == 0 && planted > 0) {
            LOGGER.debug("Fluid farm at {}: no slot could store fluid this batch (planted={})",
                    worldPosition, planted);
        }
    }


    /** Index of a tank, for log messages. */
    private int indexOf(SlotTank tank) {
        for (int i = 0; i < tanks.length; i++) {
            if (tanks[i] == tank) {
                return i;
            }
        }
        return -1;
    }

    /** Fluid held by tank {@code tank}, in mB. */
    public int getFluidAmount(int tank) {
        return tanks[tank].amount;
    }

    /**
     * Size of every tank in mB: {@link #BASE_CAPACITY} times the yield multiplier. The creative
     * upgrade reports the int ceiling instead, the same way Cooking for Blockheads' sink does, so
     * pipes and Jade see an effectively endless tank.
     */
    public int getTankCapacity() {
        if (hasCreativeUpgrade()) {
            return Integer.MAX_VALUE;
        }
        long capacity = (long) BASE_CAPACITY * Math.min(getProductionMultiplier(), MAX_CAPACITY_MULTIPLIER);
        return (int) Math.min(Integer.MAX_VALUE, capacity);
    }

    /** Trims every tank to the current capacity after an upgrade changed it. */
    private void clampTanksToCapacity() {
        for (SlotTank tank : tanks) {
            tank.clampToCapacity();
        }
    }

    /**
     * Fluid one tank holds, or {@code null} while it is dry. On the client the amount arrives through
     * the menu's data slots, so only the identity (from the sync tag) is known there; that is exactly
     * what the GUI needs to pick the still texture and tint of a bar.
     */
    @Nullable
    public Fluid getTankFluidType(int tank) {
        return tanks[tank].fluid;
    }

    /**
     * One tank as a single-tank handler, so a container slot can only be filled from its own tank.
     * The block's bucket interaction walks these in order and stops at the first one that fits.
     */
    public IFluidHandler getTankHandler(int tank) {
        return tanks[tank];
    }

    /** All nine tanks as pipes see them: any tank can be drained, none can be filled from outside. */
    public IFluidHandler getFluidHandler() {
        return productHandler;
    }

    /** Fluid the casing currently displays (client side: whatever the last sync tag carried). */
    public FluidStack getDisplayFluid() {
        return displayFluid;
    }

    /** True while at least one tank holds fluid. */
    private boolean hasAnyFluid() {
        for (SlotTank tank : tanks) {
            if (tank.amount > 0) {
                return true;
            }
        }
        return false;
    }

    /** Content of the first non-empty tank; the casing model can only show a single fluid cube. */
    private FluidStack firstSampleFluid() {
        IItemHandler input = getInputHandler();
        for (int slot = 0; slot < INPUT_SLOTS; slot++) {
            FluidStack fluid = sampleFluid(input.getStackInSlot(slot));
            if (fluid != null) {
                return fluid;
            }
        }
        return FluidStack.EMPTY;
    }

    /**
     * Set whenever the machine changed, so a sample swap re-renders right away: without it the block model
     * would lag up to five ticks behind the marker the player just placed or took out.
     */
    private boolean displayDirty = true;

    @Override
    public void setChanged() {
        super.setChanged();
        this.displayDirty = true;
    }

    /**
     * Keeps the blockstate's {@code fill} variant and the synced tank contents in line with the
     * tanks, so the model shows an empty cavity, water, lava or a fluid-tinted cube and the client
     * knows which fluid each of the nine bars paints. Runs from the block's ticker (server only) and
     * is staggered because it asks item capabilities for their content; a block update is only sent
     * when the variant or one of the nine tank identities actually changed.
     */
    public void refreshDisplay() {
        if (level == null || level.isClientSide()) {
            return;
        }
        // Pulling the creative upgrade drops the capacity from the int ceiling back to the regular
        // size, so every tank has to be trimmed to what now fits (otherwise the tooltip shows more
        // fluid than the tank can hold).
        clampTanksToCapacity();
        BlockState state = getBlockState();
        if (!state.hasProperty(FluidFarmBlock.FILL)) {
            return;
        }
        // A sample change marks the machine dirty, so swapping a marker re-renders immediately instead of
        // waiting for the next throttled check; everything else is still checked every five ticks only.
        if (!this.displayDirty
                && Math.floorMod(level.getGameTime() + worldPosition.asLong(), 5) != 0) {
            return;
        }
        this.displayDirty = false;
        // The shell only shows the fluid the samples stand for, never what the tanks hold: pipes draining a
        // tank would otherwise make the model flicker between the fluid cube and the empty casing.
        FluidStack held = firstSampleFluid();
        // Assign before the block update goes out: the update tag carries displayFluid, so clearing the
        // last sample has to be visible to the client in this very packet (otherwise the renderer keeps
        // drawing the previous fluid).
        displayFluid = held;
        FluidFarmBlock.Fill fill = fillOf(held);
        BlockState shown = state;
        if (state.getValue(FluidFarmBlock.FILL) != fill) {
            shown = state.setValue(FluidFarmBlock.FILL, fill);
            level.setBlock(worldPosition, shown, Block.UPDATE_CLIENTS);
        }
        if (shown != state || tankIdentitiesChanged()) {
            // Same variant, different tank content (honey -> milk, or a different column changed):
            // resend the nine identities so the bars get the right texture and colour.
            level.sendBlockUpdated(worldPosition, shown, shown, Block.UPDATE_CLIENTS);
        }
    }

    /** True when a tank holds a different fluid than the one the clients were last told about. */
    private boolean tankIdentitiesChanged() {
        for (int i = 0; i < TANK_SLOTS; i++) {
            if (tanks[i].fluid != syncedTankFluids[i]) {
                return true;
            }
        }
        return false;
    }

    /** Which cosmetic variant a sample fluid maps to. */
    private static FluidFarmBlock.Fill fillOf(FluidStack stack) {
        if (stack.isEmpty()) {
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

    // ---------- client sync (the nine tank identities and the casing colour, never the inventory) ----------

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        ListTag fluids = new ListTag();
        for (int i = 0; i < TANK_SLOTS; i++) {
            Fluid held = tanks[i].amount > 0 ? tanks[i].fluid : null;
            fluids.add(StringTag.valueOf(held == null ? "" : BuiltInRegistries.FLUID.getKey(held).toString()));
            // Remember what the clients are about to know, so refreshDisplay does not resend it.
            syncedTankFluids[i] = held;
        }
        tag.put(TAG_TANK_FLUIDS, fluids);
        FluidStack shown = firstSampleFluid();
        // Always write the key: omitting it on "no sample" would leave the client's copy untouched, so
        // the renderer would keep drawing the last fluid after the samples were pulled out.
        tag.putString(TAG_DISPLAY_FLUID,
                shown.isEmpty() ? "" : BuiltInRegistries.FLUID.getKey(shown.getFluid()).toString());
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
     * each one taking what it accepts. Every tank is pushed on its own, so a mixed set of columns
     * does not block the others.
     */
    @Override
    protected boolean ejectByproducts(Level level) {
        if (!hasAnyFluid()) {
            return false;
        }
        // One capability lookup per side instead of one per tank and side: the neighbours are the
        // expensive part, the nine tanks are not.
        List<IFluidHandler> targets = new ArrayList<>(Direction.values().length);
        for (Direction direction : Direction.values()) {
            IFluidHandler target = level.getCapability(Capabilities.FluidHandler.BLOCK,
                    worldPosition.relative(direction), direction.getOpposite());
            if (target != null) {
                targets.add(target);
            }
        }
        boolean moved = false;
        for (SlotTank tank : tanks) {
            if (tank.amount <= 0) {
                continue;
            }
            FluidStack held = tank.getFluidInTank(0);
            if (held.isEmpty()) {
                continue;
            }
            long inserted = insertFluidIntoAe(held.copy());
            if (inserted > 0) {
                tank.drain((int) Math.min(inserted, tank.amount), IFluidHandler.FluidAction.EXECUTE);
                moved = true;
            }
            for (IFluidHandler target : targets) {
                if (tank.amount <= 0) {
                    break;
                }
                int accepted = target.fill(tank.getFluidInTank(0).copyWithAmount(tank.amount),
                        IFluidHandler.FluidAction.EXECUTE);
                if (accepted > 0) {
                    tank.drain(accepted, IFluidHandler.FluidAction.EXECUTE);
                    moved = true;
                }
            }
        }
        return moved;
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

    // ---------- product tanks ----------

    /**
     * One of the nine product tanks: a fluid identity plus an amount, exposed as a single-tank
     * handler. The machine fills it from the inside, so {@link #fill} refuses everything an outside
     * pipe offers and {@link #drain} hands fluid out. The fluid identity is stored separately from
     * the samples, so swapping a sample cannot relabel fluid that is still inside (the tank has to be
     * drained before it accepts another fluid).
     */
    private final class SlotTank implements IFluidHandler {

        /** Stored fluid, {@code null} while the tank is dry. */
        @Nullable
        private Fluid fluid;

        /** Stored amount in mB. */
        private int amount;

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            if (fluid == null) {
                return FluidStack.EMPTY;
            }
            if (isCreative()) {
                return new FluidStack(fluid, Integer.MAX_VALUE);
            }
            return amount <= 0 ? FluidStack.EMPTY : new FluidStack(fluid, amount);
        }

        @Override
        public int getTankCapacity(int tank) {
            return FluidFarmBlockEntity.this.getTankCapacity();
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return fluid != null && !stack.isEmpty() && stack.is(fluid);
        }

        /** Outside automation may only take fluid out: filling is reserved for the machine. */
        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return 0;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            if (resource.isEmpty() || fluid == null || !resource.is(fluid)) {
                return FluidStack.EMPTY;
            }
            return drain(resource.getAmount(), action);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            if (fluid == null || maxDrain <= 0 || amount <= 0) {
                return FluidStack.EMPTY;
            }
            if (isCreative()) {
                // Creative upgrade: endless supply, nothing is consumed (int ceiling like the sink).
                return new FluidStack(fluid, maxDrain);
            }
            int drained = Math.min(maxDrain, amount);
            // Resolve the fluid before the tank is emptied: an empty target returned after a drain
            // that emptied the tank makes FluidUtil / pipes believe nothing moved and voids the fluid.
            FluidStack result = new FluidStack(fluid, drained);
            if (action.execute()) {
                amount -= drained;
                if (amount <= 0) {
                    amount = 0;
                    fluid = null;
                }
                setChanged();
            }
            return result;
        }

        /** True while the creative upgrade is installed: every tank then reports the int ceiling. */
        private boolean isCreative() {
            return FluidFarmBlockEntity.this.hasCreativeUpgrade();
        }

        /** Internal fill path used by the machine; clamps to the current capacity. */
        private boolean add(Fluid toAdd, int toAddAmount) {
            if (toAddAmount <= 0) {
                return false;
            }
            if (fluid != null && amount > 0 && !toAdd.isSame(fluid)) {
                // Another fluid is already stored: drain the tank before switching samples. This is logged
                // because a silent rejection used to look like "the machine produces nothing".
                LOGGER.debug("Fluid farm at {}: tank {} holds {} ({} mB), refusing {}",
                        worldPosition, indexOf(this), fluid, amount, toAdd);
                return false;
            }
            int space = FluidFarmBlockEntity.this.getTankCapacity() - amount;
            int added = Math.min(space, toAddAmount);
            if (added <= 0) {
                LOGGER.debug("Fluid farm at {}: tank {} is full ({} mB), refusing {}",
                        worldPosition, indexOf(this), amount, toAdd);
                return false;
            }
            fluid = toAdd;
            amount += added;
            setChanged();
            return true;
        }

        /** Forgets the content; used while reloading, before the saved values are read. */
        private void clear() {
            fluid = null;
            amount = 0;
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
                fluid = null;
            }
        }
    }

    /**
     * The nine tanks as one handler: {@link #getTanks()} is nine, every other call works on a single
     * slot, and {@link #drain} hands out the first tank that can serve the request. Filling stays
     * refused, exactly like on a single tank.
     */
    private final class ProductHandler implements IFluidHandler {

        @Override
        public int getTanks() {
            return TANK_SLOTS;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return tank >= 0 && tank < TANK_SLOTS ? tanks[tank].getFluidInTank(0) : FluidStack.EMPTY;
        }

        @Override
        public int getTankCapacity(int tank) {
            return FluidFarmBlockEntity.this.getTankCapacity();
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return tank >= 0 && tank < TANK_SLOTS && tanks[tank].isFluidValid(0, stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return 0;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) {
                return FluidStack.EMPTY;
            }
            for (SlotTank tank : tanks) {
                FluidStack held = tank.getFluidInTank(0);
                if (!held.isEmpty() && resource.is(held.getFluid())) {
                    return tank.drain(resource.getAmount(), action);
                }
            }
            return FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            for (SlotTank tank : tanks) {
                FluidStack drained = tank.drain(maxDrain, action);
                if (!drained.isEmpty()) {
                    return drained;
                }
            }
            return FluidStack.EMPTY;
        }
    }

    // ---------- MenuProvider ----------

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.simpleautofarm.fluid_farm");
    }

    /** The fluid farm has its own layout (container row + nine tank bars) instead of the farm menu. */
    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new FluidFarmMenu(containerId, playerInventory, this.worldPosition);
    }

    // ---------- ContainerData (farm values plus one amount/capacity pair per tank) ----------

    @Override
    public int getCount() {
        return DATA_SIZE;
    }

    @Override
    public int get(int index) {
        if (index >= DATA_AMOUNT_BASE && index < DATA_CAPACITY_BASE) {
            return getFluidAmount(index - DATA_AMOUNT_BASE);
        }
        if (index >= DATA_CAPACITY_BASE && index < DATA_SIZE) {
            return getTankCapacity();
        }
        return super.get(index);
    }

    // ---------- persistence ----------

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Containers")) {
            containerHandler.deserializeNBT(registries, tag.getCompound("Containers"));
        }
        loadTanks(tag);
        // The yield upgrade that set the old capacity may be gone by now.
        for (SlotTank tank : tanks) {
            tank.clampToCapacity();
        }
        // The client sync carries the identities only, so it is read last: the clamp above clears the
        // fluid of a dry tank and must not wipe what the sync tag just handed over.
        loadSyncedTankFluids(tag);
        if (tag.contains(TAG_DISPLAY_FLUID)) {
            ResourceLocation id = ResourceLocation.tryParse(tag.getString(TAG_DISPLAY_FLUID));
            Fluid fluid = id == null ? null : BuiltInRegistries.FLUID.get(id);
            displayFluid = fluid == null || fluid == Fluids.EMPTY
                    ? FluidStack.EMPTY : new FluidStack(fluid, 1000);
        }
    }

    /**
     * Reads the nine tanks. A save of the single-tank version has no {@code Tanks} list, so its one
     * tank is moved into slot 0 instead of being lost.
     */
    private void loadTanks(CompoundTag tag) {
        ListTag saved = tag.getList(TAG_TANKS, Tag.TAG_COMPOUND);
        for (int i = 0; i < TANK_SLOTS; i++) {
            SlotTank tank = tanks[i];
            tank.clear();
            if (i >= saved.size()) {
                continue;
            }
            CompoundTag entry = saved.getCompound(i);
            tank.fluid = fluidOf(entry.getString("Fluid"));
            tank.amount = tank.fluid == null ? 0 : Math.max(0, entry.getInt("Amount"));
        }
        if (!tag.contains(TAG_TANKS) && tag.contains(TAG_LEGACY_AMOUNT)) {
            SlotTank tank = tanks[0];
            tank.fluid = fluidOf(tag.getString(TAG_LEGACY_FLUID));
            tank.amount = tank.fluid == null ? 0 : Math.max(0, tag.getInt(TAG_LEGACY_AMOUNT));
        }
    }

    /** Reads the per-tank fluid ids of the sync tag; the amounts arrive through the menu data slots. */
    private void loadSyncedTankFluids(CompoundTag tag) {
        if (!tag.contains(TAG_TANK_FLUIDS)) {
            return;
        }
        ListTag fluids = tag.getList(TAG_TANK_FLUIDS, Tag.TAG_STRING);
        for (int i = 0; i < TANK_SLOTS; i++) {
            tanks[i].fluid = i < fluids.size() ? fluidOf(fluids.getString(i)) : null;
        }
    }

    /** Fluid for a registry name, or {@code null} for an unknown or empty one. */
    @Nullable
    private static Fluid fluidOf(String id) {
        ResourceLocation key = id == null || id.isBlank() ? null : ResourceLocation.tryParse(id);
        Fluid fluid = key == null ? null : BuiltInRegistries.FLUID.get(key);
        return fluid == null || fluid == Fluids.EMPTY ? null : fluid;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Containers", containerHandler.serializeNBT(registries));
        ListTag saved = new ListTag();
        for (SlotTank tank : tanks) {
            CompoundTag entry = new CompoundTag();
            if (tank.fluid != null && tank.amount > 0) {
                entry.putString("Fluid", BuiltInRegistries.FLUID.getKey(tank.fluid).toString());
                entry.putInt("Amount", tank.amount);
            }
            saved.add(entry);
        }
        tag.put(TAG_TANKS, saved);
    }

    @Override
    public void dropContents(Level level) {
        super.dropContents(level);
        for (int i = 0; i < CONTAINER_SLOTS; i++) {
            ItemStack stack = containerHandler.getStackInSlot(i);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), stack);
            }
        }
    }
}
