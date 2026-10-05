package com.simpleautofarm.block;

import com.simpleautofarm.SimpleAutoFarm;
import com.simpleautofarm.menu.BeehiveMenu;
import com.simpleautofarm.pb.PbCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.CombinedInvWrapper;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Simple Auto Beehive: the crop farm's machinery (FE energy, speed/efficiency/yield/fortune/
 * creative upgrades, deep stacks, auto-eject, AE2 output) fed with Productive Bees bee cages
 * instead of seeds.
 *
 * <p>Input slots take filled bee cages; each cycle rolls that bee's own produce list through
 * Productive Bees' recipe rules. The three food rows are the machine's feeder: a bee harvests only
 * while they hold a flower it accepts (and a lumber/quarry/dye bee harvests that flower itself),
 * but the food is only ever read, never consumed. The extra right-hand slot takes Productive Bees'
 * Block Upgrade, which swaps honeycomb output for comb blocks exactly like the mod's own advanced
 * beehive.
 */
public class BeehiveBlockEntity extends FarmBlockEntity {

    /** Extra storage rows below the outputs: three rows of nine. */
    public static final int FOOD_SLOTS = 27;

    /** One slot for Productive Bees' Block Upgrade (productivelib:upgrade_block). */
    public static final int COMB_UPGRADE_SLOTS = 1;

    /** Honey a single bee adds per finished batch, before the yield / fortune upgrades. */
    public static final int HONEY_PER_BEE = 50;

    /** Base honey tank size in mB: 32 buckets, scaled by the yield upgrade like the item output is. */
    public static final int HONEY_CAPACITY = 32_000;

    /** Capacity cap: the creative upgrade's unbounded multiplier would be unreadable in the title. */
    private static final int MAX_HONEY_MULTIPLIER = 64;

    /**
     * NeoForge's common honey tag. Productive Bees' own honey fluid carries it, so the tank accepts
     * any mod's honey instead of hardcoding {@code productivebees:honey}.
     */
    private static final TagKey<Fluid> HONEY_TAG =
            TagKey.create(Registries.FLUID, ResourceLocation.fromNamespaceAndPath("c", "honey"));

    /** Honey tank: filled while the bees work, drained by pipes, never filled from the outside. */
    private final HoneyTank honeyTank = new HoneyTank();

    private final ItemStackHandler foodHandler = new ItemStackHandler(FOOD_SLOTS) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            // Exactly like a Productive Bees feeder, which holds anything and lets the bee decide
            // whether what is inside counts as its flower (FeederBlockEntity#refreshInventoryHandler
            // returns true from isItemValid).  Anything narrower silently refuses real flowers:
            // 49 of the bee definitions in the packs name a plain item (chemlib:iodine,
            // ars_nouveau:air_essence, draconicevolution:chaos_shard ...) that is neither a block
            // nor a fluid container, and the player would have no way to feed those bees.
            return !stack.isEmpty();
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    private final ItemStackHandler combUpgradeHandler = new ItemStackHandler(COMB_UPGRADE_SLOTS) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return PbCompat.isCombBlockUpgrade(stack);
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
     * Inputs, outputs, food and the comb upgrade, so pipes can feed every slot of the machine.
     * Built in the constructor (not a field initializer) so the handlers inherited from the farm
     * are guaranteed to exist.
     */
    private final IItemHandler automationHandler;

    public BeehiveBlockEntity(BlockPos pos, BlockState state) {
        super(SimpleAutoFarm.AUTO_BEEHIVE_BLOCK_ENTITY.get(), pos, state);
        this.automationHandler = new CombinedInvWrapper(
                (ItemStackHandler) getInputHandler(),
                (ItemStackHandler) getOutputHandler(),
                foodHandler,
                combUpgradeHandler);
    }

    /** Only filled Productive Bees bee cages belong in the input slots. */
    @Override
    public boolean isValidSample(ItemStack stack) {
        return PbCompat.isBeeCage(stack);
    }

    @Override
    protected List<ItemStack> computeProducts(Level level, ItemStack cage) {
        if (!(level instanceof ServerLevel)) {
            // Productive Bees' produce lookup expects a server level (some bee types cast to it).
            return List.of();
        }
        boolean combBlockUpgrade = !combUpgradeHandler.getStackInSlot(0).isEmpty();
        return PbCompat.produce(level, cage, foodHandler, combBlockUpgrade);
    }

    /**
     * Bees also make honey: every finished batch adds {@link #HONEY_PER_BEE} mB per occupied input
     * slot, multiplied by the yield and fortune upgrades exactly like the item output is.
     */
    @Override
    protected void onBatchProduced(Level level, int planted) {
        if (planted <= 0) {
            return;
        }
        Fluid honey = findHoneyFluid();
        if (honey == null) {
            // No mod offers a c:honey fluid: the tank stays empty and pipes see an empty handler.
            return;
        }
        long amount = (long) HONEY_PER_BEE * planted * getProductionMultiplier() * rollFortune(level);
        honeyTank.addHoney(honey, (int) Math.min(Integer.MAX_VALUE, amount));
    }

    /** Honey currently in the tank, in mB. */
    public int getHoneyAmount() {
        return honeyTank.amount;
    }

    /** Current honey tank size in mB: {@link #HONEY_CAPACITY} times the yield multiplier. */
    public int getHoneyCapacity() {
        long capacity = (long) HONEY_CAPACITY * Math.min(getProductionMultiplier(), MAX_HONEY_MULTIPLIER);
        return (int) Math.min(Integer.MAX_VALUE, capacity);
    }

    /** The honey tank as pipes see it: drain from any side, never fill. */
    public IFluidHandler getFluidHandler() {
        return honeyTank;
    }

    /**
     * Auto-eject takes the honey along, in the same order the items go: the connected ME network
     * first (AE2 stores fluids through {@code AEFluidKey}), then every neighbouring fluid handler,
     * each one taking what it accepts.
     */
    @Override
    protected boolean ejectByproducts(Level level) {
        Fluid honey = findHoneyFluid();
        if (honey == null || honeyTank.amount <= 0) {
            return false;
        }
        boolean moved = false;
        long inserted = insertFluidIntoAe(new FluidStack(honey, honeyTank.amount));
        if (inserted > 0) {
            honeyTank.drain((int) Math.min(inserted, honeyTank.amount), IFluidHandler.FluidAction.EXECUTE);
            moved = true;
        }
        for (Direction direction : Direction.values()) {
            if (honeyTank.amount <= 0) {
                break;
            }
            IFluidHandler target = level.getCapability(Capabilities.FluidHandler.BLOCK,
                    worldPosition.relative(direction), direction.getOpposite());
            if (target == null) {
                continue;
            }
            int accepted = target.fill(new FluidStack(honey, honeyTank.amount),
                    IFluidHandler.FluidAction.EXECUTE);
            if (accepted > 0) {
                honeyTank.drain(accepted, IFluidHandler.FluidAction.EXECUTE);
                moved = true;
            }
        }
        return moved;
    }

    // ---------- honey tank ----------

    /**
     * Output-only tank for the {@code c:honey} fluid. The machine fills it from the inside, so
     * {@link #fill} refuses everything an outside pipe offers and {@link #drain} hands honey out.
     * The capacity follows the yield upgrade, hence the overridden tank capacity instead of a
     * fixed size.
     */
    private final class HoneyTank implements IFluidHandler {

        /** Stored amount in mB; the fluid is resolved from the tag when it is first needed. */
        private int amount;

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            Fluid honey = findHoneyFluid();
            return honey == null || amount <= 0 ? FluidStack.EMPTY : new FluidStack(honey, amount);
        }

        @Override
        public int getTankCapacity(int tank) {
            return getHoneyCapacity();
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            Fluid honey = findHoneyFluid();
            return honey != null && !stack.isEmpty() && stack.is(honey);
        }

        /** Outside automation may only take honey out: filling is reserved for the bees. */
        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return 0;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            Fluid honey = findHoneyFluid();
            if (resource.isEmpty() || honey == null || !resource.is(honey)) {
                return FluidStack.EMPTY;
            }
            return drain(resource.getAmount(), action);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            Fluid honey = findHoneyFluid();
            if (honey == null || maxDrain <= 0 || amount <= 0) {
                return FluidStack.EMPTY;
            }
            int drained = Math.min(maxDrain, amount);
            if (action.execute()) {
                amount -= drained;
                setChanged();
            }
            return new FluidStack(honey, drained);
        }

        /** Internal fill path used by the bees; clamps to the current capacity. */
        private void addHoney(Fluid honey, int toAdd) {
            int space = getHoneyCapacity() - amount;
            int added = Math.min(space, toAdd);
            if (added <= 0) {
                return;
            }
            amount += added;
            setChanged();
        }

        /** Removes honey that no longer fits after the yield upgrade was pulled out. */
        private void clampToCapacity() {
            if (amount > getHoneyCapacity()) {
                amount = getHoneyCapacity();
                setChanged();
            }
        }
    }

    /** The first source fluid tagged {@code c:honey}, or {@code null} when no mod provides one. */
    @Nullable
    private static Fluid findHoneyFluid() {
        var tag = BuiltInRegistries.FLUID.getTag(HONEY_TAG);
        if (tag.isEmpty()) {
            return null;
        }
        Fluid first = null;
        for (Holder<Fluid> holder : tag.get()) {
            Fluid fluid = holder.value();
            if (fluid.defaultFluidState().isSource()) {
                return fluid;
            }
            if (first == null) {
                first = fluid;
            }
        }
        return first;
    }

    public IItemHandler getFoodHandler() {
        return foodHandler;
    }

    public IItemHandler getCombUpgradeHandler() {
        return combUpgradeHandler;
    }

    @Override
    public IItemHandler getItemHandler() {
        return automationHandler;
    }

    // ---------- MenuProvider ----------

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.simpleautofarm.auto_beehive");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new BeehiveMenu(containerId, playerInventory, this.worldPosition);
    }

    // ---------- ContainerData (adds "resource bees installed" for the screen hint) ----------

    @Override
    public int getCount() {
        return 10;
    }

    @Override
    public int get(int index) {
        return switch (index) {
            case 7 -> PbCompat.isLoaded() ? 1 : 0;
            case 8 -> getHoneyAmount();
            case 9 -> getHoneyCapacity();
            default -> super.get(index);
        };
    }

    // ---------- persistence ----------

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Food")) {
            foodHandler.deserializeNBT(registries, tag.getCompound("Food"));
        }
        if (tag.contains("CombUpgrade")) {
            combUpgradeHandler.deserializeNBT(registries, tag.getCompound("CombUpgrade"));
        }
        honeyTank.amount = tag.getInt("Honey");
        // The yield upgrade that set the old capacity may be gone by now.
        honeyTank.clampToCapacity();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Food", foodHandler.serializeNBT(registries));
        tag.put("CombUpgrade", combUpgradeHandler.serializeNBT(registries));
        tag.putInt("Honey", honeyTank.amount);
    }

    @Override
    public void dropContents(Level level) {
        super.dropContents(level);
        for (int i = 0; i < FOOD_SLOTS; i++) {
            ItemStack stack = foodHandler.getStackInSlot(i);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), stack);
            }
        }
        for (int i = 0; i < COMB_UPGRADE_SLOTS; i++) {
            ItemStack stack = combUpgradeHandler.getStackInSlot(i);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), stack);
            }
        }
    }
}
