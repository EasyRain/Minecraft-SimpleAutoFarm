package com.simpleautofarm.block;

import com.simpleautofarm.SimpleAutoFarm;
import com.simpleautofarm.item.UpgradeItem;
import com.simpleautofarm.item.UpgradeType;
import com.simpleautofarm.menu.GeneratorMenu;
import com.simpleautofarm.util.UpgradeEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

public class GeneratorBlockEntity extends BlockEntity implements MenuProvider, ContainerData, IEnergyStorage {

    public static final int BASE_ENERGY_CAPACITY = 10_000;
    public static final int BASE_FE_PER_TICK = 250;
    public static final int MAX_EXTRACT = 1_000;
    public static final int PUSH_RATE = 500;
    public static final int UPGRADE_SLOTS = 2;

    private final ItemStackHandler fuelHandler = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.getBurnTime(null) > 0;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    private final ItemStackHandler upgradeHandler = new ItemStackHandler(UPGRADE_SLOTS) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (!(stack.getItem() instanceof UpgradeItem upgrade)) {
                return false;
            }
            // The generator accepts speed and efficiency only.
            if (upgrade.getType() == UpgradeType.YIELD) {
                return false;
            }
            // At most one upgrade of each type per machine, and the combined upgrade never shares a
            // machine with Speed/Efficiency (it replaces both).
            for (int i = 0; i < UPGRADE_SLOTS; i++) {
                if (i == slot) {
                    continue;
                }
                ItemStack other = getStackInSlot(i);
                if (!other.isEmpty() && other.getItem() instanceof UpgradeItem otherUpgrade
                        && (otherUpgrade.getType() == upgrade.getType()
                                || upgrade.getType().conflictsWith(otherUpgrade.getType()))) {
                    return false;
                }
            }
            return true;
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

    protected final GeneratorEnergyStorage energyStorage = new GeneratorEnergyStorage(this);

    /** Remaining burn time in raw (unmodified) ticks. */
    protected double burnTime;
    /** Total burn time of the current fuel, for the flame ratio. */
    protected double burnTimeTotal;

    public GeneratorBlockEntity(BlockPos pos, BlockState blockState) {
        this(SimpleAutoFarm.GENERATOR_BLOCK_ENTITY.get(), pos, blockState);
    }

    protected GeneratorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    /** Base values, overridable by the Pro variant (10x). */
    protected int getBaseFePerTick() {
        return BASE_FE_PER_TICK;
    }

    protected int getBaseEnergyCapacity() {
        return BASE_ENERGY_CAPACITY;
    }

    protected int getBaseMaxExtract() {
        return MAX_EXTRACT;
    }

    protected int getBasePushRate() {
        return PUSH_RATE;
    }

    // ---------- upgrade lookups ----------

    /** True when a creative upgrade is installed (it overrides every other upgrade). */
    protected boolean hasCreativeUpgrade() {
        for (int i = 0; i < UPGRADE_SLOTS; i++) {
            ItemStack stack = upgradeHandler.getStackInSlot(i);
            if (!stack.isEmpty() && stack.getItem() instanceof UpgradeItem upgrade && upgrade.getType() == UpgradeType.CREATIVE) {
                return true;
            }
        }
        return false;
    }

    private int getUpgradeTier(UpgradeType type) {
        if (hasCreativeUpgrade()) {
            return 0; // the creative upgrade ignores every other upgrade
        }
        int tier = 0;
        for (int i = 0; i < UPGRADE_SLOTS; i++) {
            ItemStack stack = upgradeHandler.getStackInSlot(i);
            if (!stack.isEmpty() && stack.getItem() instanceof UpgradeItem upgrade && upgrade.getType() == type) {
                tier = Math.max(tier, upgrade.getTier());
            }
        }
        return tier;
    }

    /** FE produced per real tick while burning, after the speed / combined upgrade is applied. */
    public int getFePerTick() {
        if (hasCreativeUpgrade()) {
            return Integer.MAX_VALUE;
        }
        int tier = Math.max(getUpgradeTier(UpgradeType.SPEED), getUpgradeTier(UpgradeType.MOTION));
        double multiplier = tier > 0 ? 1.0 + UpgradeEffects.speedGenEnergyPercent(tier) / 100.0 : 1.0;
        return (int) Math.round(getBaseFePerTick() * multiplier);
    }

    /** Max FE extracted per tick, scaled by the speed / combined upgrade by the same amount as production. */
    public int getMaxExtract() {
        if (hasCreativeUpgrade()) {
            return Integer.MAX_VALUE;
        }
        int tier = Math.max(getUpgradeTier(UpgradeType.SPEED), getUpgradeTier(UpgradeType.MOTION));
        double multiplier = tier > 0 ? 1.0 + UpgradeEffects.speedGenEnergyPercent(tier) / 100.0 : 1.0;
        return (int) Math.round(getBaseMaxExtract() * multiplier);
    }

    /** FE pushed per adjacent direction per tick, scaled by the speed / combined upgrade like production. */
    public int getPushRate() {
        if (hasCreativeUpgrade()) {
            return Integer.MAX_VALUE;
        }
        int tier = Math.max(getUpgradeTier(UpgradeType.SPEED), getUpgradeTier(UpgradeType.MOTION));
        double multiplier = tier > 0 ? 1.0 + UpgradeEffects.speedGenEnergyPercent(tier) / 100.0 : 1.0;
        return (int) Math.round(getBasePushRate() * multiplier);
    }

    /** Raw burn ticks consumed per real tick: (1 + speed%) / efficiency divisor (combined upgrade counts as both). */
    protected double getFuelUsePerTick() {
        int speedTier = Math.max(getUpgradeTier(UpgradeType.SPEED), getUpgradeTier(UpgradeType.MOTION));
        int efficiencyTier = Math.max(getUpgradeTier(UpgradeType.EFFICIENCY), getUpgradeTier(UpgradeType.MOTION));
        double use = 1.0;
        if (speedTier > 0) {
            use *= 1.0 + UpgradeEffects.speedGenFuelPercent(speedTier) / 100.0;
        }
        if (efficiencyTier > 0) {
            use /= UpgradeEffects.efficiencyFuelDivisor(efficiencyTier);
        }
        return use;
    }

    /** Total internal energy capacity (grows with the efficiency / combined upgrade). */
    public int getEnergyCapacity() {
        if (hasCreativeUpgrade()) {
            return Integer.MAX_VALUE;
        }
        int tier = Math.max(getUpgradeTier(UpgradeType.EFFICIENCY), getUpgradeTier(UpgradeType.MOTION));
        double multiplier = tier > 0 ? 1.0 + UpgradeEffects.efficiencyCachePercent(tier) / 100.0 : 1.0;
        return (int) (getBaseEnergyCapacity() * multiplier);
    }

    /** Fluid fuel amount in mB (0 for the regular generator; the Pro overrides this). */
    public int getFluidAmount() {
        return 0;
    }

    /** Fluid fuel capacity in mB (0 for the regular generator; the Pro overrides this). */
    public int getFluidCapacity() {
        return 0;
    }

    // ---------- ticking ----------

    public static void serverTick(Level level, BlockPos pos, BlockState state, GeneratorBlockEntity generator) {
        generator.tickServer(level);
    }

    protected void tickServer(Level level) {
        energyStorage.clampEnergy();
        boolean changed = false;

        if (hasCreativeUpgrade()) {
            // creative: produces energy every tick with no fuel required
            if (energyStorage.getEnergyStored() < energyStorage.getMaxEnergyStored()) {
                energyStorage.addEnergy(getFePerTick());
                burnTime = 1;
                burnTimeTotal = 1;
                changed = true;
            }
        } else if (burnTime > 0) {
            // pause production while the energy buffer is full; resume once energy is consumed
            if (energyStorage.getEnergyStored() < energyStorage.getMaxEnergyStored()) {
                // Fuel consumption rate only shortens how long fuel lasts (the speed-upgrade
                // balance); it does NOT add extra energy. Energy per real tick is fixed.
                double use = Math.min(getFuelUsePerTick(), burnTime);
                burnTime -= use;
                energyStorage.addEnergy(getFePerTick());
                changed = true;
            }
        } else if (energyStorage.getEnergyStored() < energyStorage.getMaxEnergyStored()) {
            if (tryConsumeFuel()) {
                changed = true;
            }
        }

        if (energyStorage.getEnergyStored() > 0 && pushEnergy(level) > 0) {
            changed = true;
        }

        if (changed) {
            setChanged();
        }
        syncActive(level);
    }

    /** True while the generator is actively producing energy (burning and not paused). */
    protected boolean isActive() {
        return (burnTime > 0 || hasCreativeUpgrade())
                && energyStorage.getEnergyStored() < energyStorage.getMaxEnergyStored();
    }

    /** Toggles the block's {@link GeneratorBlock#ACTIVE} blockstate so the model swaps front screens. */
    protected void syncActive(Level level) {
        if (level.isClientSide()) {
            return;
        }
        BlockState st = level.getBlockState(worldPosition);
        if (!st.hasProperty(GeneratorBlock.ACTIVE)) {
            return;
        }
        boolean active = isActive();
        if (st.getValue(GeneratorBlock.ACTIVE) != active) {
            level.setBlock(worldPosition, st.setValue(GeneratorBlock.ACTIVE, active), 3);
        }
    }

    /** Attempts to start burning an item fuel; returns true if fuel was consumed. */
    protected boolean tryConsumeFuel() {
        ItemStack fuel = fuelHandler.getStackInSlot(0);
        if (!fuel.isEmpty()) {
            int burn = fuel.getBurnTime(null);
            if (burn > 0) {
                fuelHandler.extractItem(0, 1, false);
                burnTime = burn;
                burnTimeTotal = burn;
                return true;
            }
        }
        return false;
    }

    /** Pushes buffered energy into adjacent energy-capable blocks (e.g. the auto farm). */
    protected int pushEnergy(Level level) {
        int total = 0;
        for (Direction dir : Direction.values()) {
            if (energyStorage.getEnergyStored() <= 0) {
                break;
            }
            BlockPos neighbor = worldPosition.relative(dir);
            IEnergyStorage target = level.getCapability(Capabilities.EnergyStorage.BLOCK, neighbor, dir.getOpposite());
            if (target != null && target.canReceive()) {
                int toSend = Math.min(energyStorage.getEnergyStored(), getPushRate());
                int accepted = target.receiveEnergy(toSend, false);
                if (accepted > 0) {
                    energyStorage.extractEnergy(accepted, false);
                    total += accepted;
                }
            }
        }
        return total;
    }

    // ---------- energy ----------

    @Override
    public int receiveEnergy(int toReceive, boolean simulate) {
        return energyStorage.receiveEnergy(toReceive, simulate);
    }

    @Override
    public int extractEnergy(int toExtract, boolean simulate) {
        return energyStorage.extractEnergy(toExtract, simulate);
    }

    @Override
    public int getEnergyStored() {
        return energyStorage.getEnergyStored();
    }

    @Override
    public int getMaxEnergyStored() {
        return energyStorage.getMaxEnergyStored();
    }

    @Override
    public boolean canExtract() {
        return energyStorage.canExtract();
    }

    @Override
    public boolean canReceive() {
        return energyStorage.canReceive();
    }

    public IEnergyStorage getEnergyStorage() {
        return this;
    }

    // ---------- inventory ----------

    public IItemHandler getFuelHandler() {
        return fuelHandler;
    }

    public IItemHandler getUpgradeHandler() {
        return upgradeHandler;
    }

    /** Shift-right-click insertion; the generator accepts speed and efficiency upgrades only. */
    public boolean tryInsertUpgrade(Player player, ItemStack held, UpgradeItem upgrade) {
        if (upgrade.getType() == UpgradeType.YIELD) {
            return false;
        }
        // The combined upgrade replaces Speed and Efficiency, so it can never join a machine that
        // already holds either of them (and vice versa).
        for (int i = 0; i < UPGRADE_SLOTS; i++) {
            ItemStack slotStack = upgradeHandler.getStackInSlot(i);
            if (!slotStack.isEmpty() && slotStack.getItem() instanceof UpgradeItem other
                    && upgrade.getType().conflictsWith(other.getType())) {
                return false;
            }
        }
        int typeSlot = -1;
        for (int i = 0; i < UPGRADE_SLOTS; i++) {
            ItemStack slotStack = upgradeHandler.getStackInSlot(i);
            if (!slotStack.isEmpty() && slotStack.getItem() instanceof UpgradeItem existing
                    && existing.getType() == upgrade.getType()) {
                typeSlot = i;
                break;
            }
        }
        if (typeSlot >= 0) {
            UpgradeItem existing = (UpgradeItem) upgradeHandler.getStackInSlot(typeSlot).getItem();
            if (upgrade.getTier() <= existing.getTier()) {
                return false;
            }
            ItemStack old = upgradeHandler.getStackInSlot(typeSlot).copy();
            upgradeHandler.setStackInSlot(typeSlot, held.copyWithCount(1));
            held.shrink(1);
            if (!player.getInventory().add(old)) {
                player.drop(old, false);
            }
            return true;
        }
        for (int i = 0; i < UPGRADE_SLOTS; i++) {
            if (upgradeHandler.getStackInSlot(i).isEmpty()) {
                upgradeHandler.setStackInSlot(i, held.copyWithCount(1));
                held.shrink(1);
                return true;
            }
        }
        return false;
    }

    public void dropContents(Level level) {
        ItemStack stack = fuelHandler.getStackInSlot(0);
        if (!stack.isEmpty()) {
            Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), stack);
        }
        for (int i = 0; i < UPGRADE_SLOTS; i++) {
            ItemStack upgrade = upgradeHandler.getStackInSlot(i);
            if (!upgrade.isEmpty()) {
                Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), upgrade);
            }
        }
    }

    // ---------- MenuProvider ----------

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.simpleautofarm.generator");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new GeneratorMenu(containerId, playerInventory, this.worldPosition);
    }

    // ---------- ContainerData ----------

    @Override
    public int getCount() {
        return 8;
    }

    @Override
    public int get(int index) {
        return switch (index) {
            case 0 -> energyStorage.getEnergyStored();
            case 1 -> (int) Math.ceil(burnTime);
            case 2 -> (int) Math.ceil(burnTimeTotal);
            case 3 -> getEnergyCapacity();
            case 4 -> getFePerTick();
            case 5 -> getFluidAmount();
            case 6 -> getFluidCapacity();
            case 7 -> hasCreativeUpgrade() ? 1 : 0;
            default -> 0;
        };
    }

    @Override
    public void set(int index, int value) {
    }

    // ---------- persistence ----------

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Fuel")) {
            fuelHandler.deserializeNBT(registries, tag.getCompound("Fuel"));
        }
        if (tag.contains("Upgrades")) {
            upgradeHandler.deserializeNBT(registries, tag.getCompound("Upgrades"));
        }
        burnTime = tag.getDouble("BurnTime");
        burnTimeTotal = tag.getDouble("BurnTimeTotal");
        if (tag.contains("Energy")) {
            energyStorage.setEnergy(tag.getInt("Energy"));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Fuel", fuelHandler.serializeNBT(registries));
        tag.put("Upgrades", upgradeHandler.serializeNBT(registries));
        tag.putDouble("BurnTime", burnTime);
        tag.putDouble("BurnTimeTotal", burnTimeTotal);
        tag.putInt("Energy", energyStorage.getEnergyStored());
    }

    /**
     * Energy storage that produces internally ({@link #addEnergy}) but only accepts extraction
     * from the outside (for cables / auto-push). Capacity is dynamic (efficiency upgrade).
     */
    static class GeneratorEnergyStorage extends EnergyStorage {
        private final GeneratorBlockEntity be;

        GeneratorEnergyStorage(GeneratorBlockEntity be) {
            super(BASE_ENERGY_CAPACITY, 0, MAX_EXTRACT);
            this.be = be;
        }

        @Override
        public int getMaxEnergyStored() {
            return be.getEnergyCapacity();
        }

        @Override
        public int extractEnergy(int toExtract, boolean simulate) {
            if (!canExtract() || toExtract <= 0) {
                return 0;
            }
            int extracted = Math.min(this.energy, Math.min(be.getMaxExtract(), toExtract));
            if (!simulate) {
                this.energy -= extracted;
            }
            return extracted;
        }

        int addEnergy(int amount) {
            int capacity = be.getEnergyCapacity();
            int added = Math.min(capacity - this.energy, amount);
            this.energy += added;
            return added;
        }

        void setEnergy(int value) {
            this.energy = Math.max(0, Math.min(be.getEnergyCapacity(), value));
        }

        void clampEnergy() {
            this.energy = Math.min(this.energy, be.getEnergyCapacity());
        }
    }
}
