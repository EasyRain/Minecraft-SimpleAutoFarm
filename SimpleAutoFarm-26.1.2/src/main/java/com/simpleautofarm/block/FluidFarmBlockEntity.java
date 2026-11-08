package com.simpleautofarm.block;

import com.simpleautofarm.SimpleAutoFarm;
import com.simpleautofarm.menu.FluidFarmMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import com.mojang.logging.LogUtils;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidActionResult;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.CombinedInvWrapper;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
 * <p>26.1.2 note: the nine tank identities and their amounts travel through the menu's
 * {@code ContainerData} slots (amounts 7..15, capacities 16..24, fluid registry ids 25..33), so this
 * block entity needs no custom update packet at all.
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

    /** Ticks between two filling passes; staggered by position like the display refresh. */
    private static final int FILL_INTERVAL = 10;

    /** Capacity cap: the creative upgrade's unbounded multiplier would be unreadable in the title. */
    private static final int MAX_CAPACITY_MULTIPLIER = 64;

    /**
     * Menu data slots: the seven farm values first, then nine amounts, nine capacities and nine fluid
     * registry ids (one triple per tank, so the screen can render nine bars from data slots alone).
     */
    public static final int DATA_AMOUNT_BASE = 7;
    public static final int DATA_CAPACITY_BASE = DATA_AMOUNT_BASE + TANK_SLOTS;
    public static final int DATA_FLUID_BASE = DATA_CAPACITY_BASE + TANK_SLOTS;
    public static final int DATA_SIZE = DATA_FLUID_BASE + TANK_SLOTS;

    /** Saved keys: one amount and one fluid id per tank; the single-tank keys are read once. */
    private static final String TAG_AMOUNT_PREFIX = "TankAmount";
    private static final String TAG_FLUID_PREFIX = "TankFluid";
    private static final String TAG_LEGACY_AMOUNT = "FluidAmount";
    private static final String TAG_LEGACY_FLUID = "TankFluid0";

    /** The nine product tanks: filled while the machine runs, drained by pipes, never filled outside. */
    private final SlotTank[] tanks = new SlotTank[TANK_SLOTS];

    /** The nine tanks as one handler for pipes, AE2 and the block's bucket interaction. */
    private final IFluidHandler productHandler = new ProductHandler();

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

    /**
     * Anything an item fluid handler can fill: empty bucket, empty bottle, empty tank item. Uses the
     * 26.1.2 capability call seen in {@code GeneratorProBlock} (fluid item capability + {@link ItemAccess}).
     */
    public static boolean isFluidContainer(ItemStack stack) {
        return !stack.isEmpty()
                && stack.getCapability(Capabilities.Fluid.ITEM, ItemAccess.forStack(stack)) != null;
    }

    /**
     * Fills every container slot from the tank of its own column: the empty container is swapped for
     * its filled result in the same slot, so the player only has to collect it. Runs from the block
     * ticker, staggered like the display refresh; a column with an empty tank is skipped.
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
            // tryFillContainer works on a copy and drains the tank only on success; its result is the
            // filled container item, which replaces the empty one in place.
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
     * One batch fills the tank of every occupied sample slot with {@link #FLUID_PER_SAMPLE} mB of that
     * slot's own sample fluid, multiplied by the yield and fortune upgrades exactly like the item
     * output is. The nine columns are independent, so nine different fluids run in parallel.
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

    /** Index of one of this machine's tanks, for log messages. */
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
     * Size of every tank in mB: {@link #BASE_CAPACITY} times the yield multiplier (the creative
     * upgrade reports the int ceiling, so pipes see an effectively endless tank).
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

    /** Fluid one tank holds, or {@code null} while it is dry. */
    @Nullable
    public Fluid getTankFluidType(int tank) {
        return tanks[tank].fluid;
    }

    /** One tank as a single-tank handler, so a container slot can only be filled from its own tank. */
    public IFluidHandler getTankHandler(int tank) {
        return tanks[tank];
    }

    /** All nine tanks as pipes see them: any tank can be drained, none can be filled from outside. */
    public IFluidHandler getFluidHandler() {
        return productHandler;
    }

    /**
     * Keeps the blockstate's {@code fill} variant in line with the tanks, so the model shows an empty
     * cavity, water, lava or a fluid cube. Runs from the block's ticker (server only) and is staggered
     * because it asks item capabilities for their content; the variant is only written when it
     * actually changed. No client sync is needed: the variant travels with the block update.
     */
    public void refreshDisplay() {
        if (level == null || level.isClientSide()) {
            return;
        }
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
        FluidStack held = firstSampleFluid();
        FluidFarmBlock.Fill fill = fillOf(held);
        if (state.getValue(FluidFarmBlock.FILL) != fill) {
            BlockState updated = state.setValue(FluidFarmBlock.FILL, fill);
            level.setBlock(worldPosition, updated, Block.UPDATE_CLIENTS);
            // A plain setBlock does not always resync this block on 26.1.2, so the new state is sent
            // explicitly; without it the client keeps rendering the old variant.
            level.sendBlockUpdated(worldPosition, state, updated, Block.UPDATE_CLIENTS);
            LOGGER.debug("Fluid farm display at {}: fill {} -> {}", worldPosition,
                    state.getValue(FluidFarmBlock.FILL), fill);
        }
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

    /** True while at least one tank holds fluid. */
    private boolean hasAnyFluid() {
        for (SlotTank tank : tanks) {
            if (tank.amount > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Auto-eject takes the fluid along, in the same order the items go: the connected ME network first
     * (AE2 stores fluids through {@code AEFluidKey}), then every neighbouring fluid handler, each one
     * taking what it accepts. Every tank is pushed on its own, so a mixed set does not block the rest.
     */
    @Override
    protected boolean ejectByproducts(Level level) {
        if (!hasAnyFluid()) {
            return false;
        }
        // One capability lookup per side instead of one per tank and side.
        List<IFluidHandler> targets = new ArrayList<>(Direction.values().length);
        for (Direction direction : Direction.values()) {
            var capability = level.getCapability(Capabilities.Fluid.BLOCK,
                    worldPosition.relative(direction), direction.getOpposite());
            if (capability != null) {
                targets.add(IFluidHandler.of(capability));
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
     * Reads the fluid a container item holds, or {@code null} when it holds none. Uses the 26.1.2 item
     * fluid capability through {@link ItemAccess} and reads tank 0 of the handler behind it.
     */
    @Nullable
    private static FluidStack sampleFluid(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        // FluidUtil is the version-independent way to read a container's content: it understands the
        // transfer handlers 26.1.2 uses for item fluid capabilities as well as the legacy ones.
        Optional<FluidStack> contained = FluidUtil.getFluidContained(stack);
        if (contained == null || contained.isEmpty()) {
            return null;
        }
        FluidStack fluid = contained.get();
        return fluid.isEmpty() ? null : fluid.copy();
    }

    // ---------- product tanks ----------

    /**
     * One of the nine product tanks: a fluid identity plus an amount, exposed as a single-tank
     * handler. The machine fills it from the inside, so {@link #fill} refuses everything an outside
     * pipe offers and {@link #drain} hands fluid out. The fluid identity is stored separately from the
     * samples, so swapping a sample cannot relabel fluid that is still inside.
     */
    private final class SlotTank implements IFluidHandler {

        @Nullable
        private Fluid fluid;
        private int amount;

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return fluid == null || amount <= 0 ? FluidStack.EMPTY : new FluidStack(fluid, amount);
        }

        @Override
        public int getTankCapacity(int tank) {
            return FluidFarmBlockEntity.this.getTankCapacity();
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return fluid == null && !stack.isEmpty();
        }

        /** Outside automation may only take fluid out: filling is reserved for the machine. */
        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return 0;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            if (fluid == null || resource.isEmpty() || !resource.is(fluid)) {
                return FluidStack.EMPTY;
            }
            return drain(resource.getAmount(), action);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            if (fluid == null || maxDrain <= 0 || amount <= 0) {
                return FluidStack.EMPTY;
            }
            // The real stack is returned even when the tank runs dry, so callers see what they got.
            int drained = Math.min(maxDrain, amount);
            FluidStack result = new FluidStack(fluid, drained);
            if (action.execute()) {
                amount -= drained;
                setChanged();
            }
            return result;
        }

        /** Internal fill path used by the batches; clamps to the current capacity. */
        private boolean add(Fluid newFluid, int toAdd) {
            if (toAdd <= 0) {
                return false;
            }
            // A tank with fluid in it keeps its fluid; an empty one may adopt whatever the sample holds, so
            // draining a tank (or moving a sample to a fresh column) starts over cleanly.
            if (this.fluid != null && amount > 0 && !newFluid.isSame(this.fluid)) {
                LOGGER.debug("Fluid farm at {}: tank {} holds {} ({} mB), refusing {}",
                        worldPosition, indexOf(this), this.fluid, amount, newFluid);
                return false;
            }
            int space = FluidFarmBlockEntity.this.getTankCapacity() - amount;
            int added = Math.min(space, toAdd);
            if (added <= 0) {
                LOGGER.debug("Fluid farm at {}: tank {} is full ({} mB), refusing {}",
                        worldPosition, indexOf(this), amount, newFluid);
                return false;
            }
            this.fluid = newFluid;
            amount += added;
            setChanged();
            return true;
        }

        /** Index of this tank, for log messages. */
        int index() {
            return indexOf(this);
        }

        /** Removes fluid that no longer fits after an upgrade changed the capacity. */
        private void clampToCapacity() {
            int capacity = FluidFarmBlockEntity.this.getTankCapacity();
            if (amount > capacity) {
                amount = capacity;
                setChanged();
            }
        }
    }

    /** The nine tanks as one handler for pipes, AE2 and the bucket interaction. */
    private final class ProductHandler implements IFluidHandler {

        @Override
        public int getTanks() {
            return TANK_SLOTS;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return tanks[tank].getFluidInTank(0);
        }

        @Override
        public int getTankCapacity(int tank) {
            return FluidFarmBlockEntity.this.getTankCapacity();
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return tanks[tank].isFluidValid(0, stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return 0;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            for (SlotTank tank : tanks) {
                FluidStack drained = tank.drain(resource, action);
                if (!drained.isEmpty()) {
                    return drained;
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

    // ---------- ContainerData (energy/progress/auto-eject + the nine tanks) ----------

    @Override
    public int getCount() {
        return DATA_SIZE;
    }

    @Override
    public int get(int index) {
        if (index >= DATA_FLUID_BASE) {
            Fluid fluid = tanks[index - DATA_FLUID_BASE].amount > 0 ? tanks[index - DATA_FLUID_BASE].fluid : null;
            return fluid == null ? -1 : BuiltInRegistries.FLUID.getId(fluid);
        }
        if (index >= DATA_CAPACITY_BASE) {
            return getTankCapacity();
        }
        if (index >= DATA_AMOUNT_BASE) {
            return tanks[index - DATA_AMOUNT_BASE].amount;
        }
        return super.get(index);
    }

    // ---------- MenuProvider ----------

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.simpleautofarm.fluid_farm");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new FluidFarmMenu(containerId, playerInventory, this.worldPosition);
    }

    // ---------- persistence (ValueInput / ValueOutput) ----------

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        for (int i = 0; i < TANK_SLOTS; i++) {
            tanks[i].amount = Math.max(0, input.getIntOr(TAG_AMOUNT_PREFIX + i, 0));
            tanks[i].fluid = readFluid(input.getStringOr(TAG_FLUID_PREFIX + i, ""));
        }
        // Migration from the single-tank version: its content moves into tank 0.
        int legacyAmount = input.getIntOr(TAG_LEGACY_AMOUNT, 0);
        if (legacyAmount > 0 && tanks[0].amount <= 0) {
            tanks[0].amount = legacyAmount;
            tanks[0].fluid = readFluid(input.getStringOr(TAG_LEGACY_FLUID, ""));
        }
        clampTanksToCapacity();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        for (int i = 0; i < TANK_SLOTS; i++) {
            output.putInt(TAG_AMOUNT_PREFIX + i, tanks[i].amount);
            Identifier id = tanks[i].fluid == null ? null : BuiltInRegistries.FLUID.getKey(tanks[i].fluid);
            output.putString(TAG_FLUID_PREFIX + i, id == null ? "" : id.toString());
        }
    }

    /** Fluid for a saved registry name; an empty or unknown name means "dry tank". */
    @Nullable
    private static Fluid readFluid(String name) {
        if (name.isEmpty()) {
            return null;
        }
        Identifier id = Identifier.tryParse(name);
        if (id == null) {
            return null;
        }
        Fluid fluid = BuiltInRegistries.FLUID.getOptional(id).orElse(null);
        return fluid == null || fluid.isSame(Fluids.EMPTY) ? null : fluid;
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
