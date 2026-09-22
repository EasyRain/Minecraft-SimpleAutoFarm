package com.simpleautofarm.block;

import com.simpleautofarm.SimpleAutoFarm;
import com.simpleautofarm.ae.Ae2Compat;
import com.simpleautofarm.ae.IAe2Node;
import com.simpleautofarm.item.UpgradeItem;
import com.simpleautofarm.item.UpgradeType;
import com.simpleautofarm.menu.FarmMenu;
import com.simpleautofarm.util.SeedHelper;
import com.simpleautofarm.util.StackCodecs;
import com.simpleautofarm.util.UpgradeEffects;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BambooSaplingBlock;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.PitcherCropBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.CombinedInvWrapper;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class FarmBlockEntity extends BlockEntity implements MenuProvider, ContainerData, IEnergyStorage {

    public static final int INPUT_SLOTS = 9;
    public static final int OUTPUT_SLOTS = 27;
    public static final int UPGRADE_SLOTS = 3;

    public static final int BASE_ENERGY_CAPACITY = 20_000;
    public static final int MAX_ENERGY_RECEIVE = 2_000;
    public static final int ENERGY_PER_TICK_PER_SLOT = 100;
    /** Baseline production time: 30 seconds (600 ticks), shortened by speed upgrades. */
    public static final int BASE_PRODUCTION_TICKS = 600;
    public static final int MAX_CHARGE_PER_CLICK = 5_000;
    public static final int DEFAULT_STACK_LIMIT = 64;

    private final ItemStackHandler inputHandler = new ItemStackHandler(INPUT_SLOTS) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return SeedHelper.isPlant(stack);
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

    private final DeepStackHandler outputHandler = new DeepStackHandler(OUTPUT_SLOTS, this);

    private final ItemStackHandler upgradeHandler = new ItemStackHandler(UPGRADE_SLOTS) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (!(stack.getItem() instanceof UpgradeItem upgrade)) {
                return false;
            }
            // At most one upgrade of each type per machine.
            for (int i = 0; i < UPGRADE_SLOTS; i++) {
                if (i == slot) {
                    continue;
                }
                ItemStack other = getStackInSlot(i);
                if (!other.isEmpty() && other.getItem() instanceof UpgradeItem otherUpgrade
                        && otherUpgrade.getType() == upgrade.getType()) {
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

    private final CombinedInvWrapper combinedHandler = new CombinedInvWrapper(inputHandler, outputHandler);

    private final MachineEnergyStorage energyStorage = new MachineEnergyStorage(this);

    /** Global growth progress of the current batch (0..getProductionTicks()). */
    private int progress;
    /** Fractional energy accumulated toward the next progress step. */
    private int growthBuffer;

    private boolean autoEject;

    /** Optional AE2 grid node; null when AE2 is not installed. */
    private final IAe2Node ae2Node;

    private static final Logger LOGGER = LogUtils.getLogger();

    public FarmBlockEntity(BlockPos pos, BlockState blockState) {
        super(SimpleAutoFarm.AUTO_FARM_BLOCK_ENTITY.get(), pos, blockState);
        this.ae2Node = Ae2Compat.create(this);
    }

    // ---------- AE2 grid-node lifecycle ----------

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        if (ae2Node != null) {
            ae2Node.onLoad();
        }
    }

    @Override
    public void setRemoved() {
        if (ae2Node != null) {
            ae2Node.onRemoved();
        }
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        if (ae2Node != null) {
            ae2Node.onRemoved();
        }
        super.onChunkUnloaded();
    }

    // ---------- upgrade lookups ----------

    /** True when a creative upgrade is installed (it overrides every other upgrade). */
    private boolean hasCreativeUpgrade() {
        for (int i = 0; i < UPGRADE_SLOTS; i++) {
            ItemStack stack = upgradeHandler.getStackInSlot(i);
            if (!stack.isEmpty() && stack.getItem() instanceof UpgradeItem upgrade && upgrade.getType() == UpgradeType.CREATIVE) {
                return true;
            }
        }
        return false;
    }

    /** Highest tier (0 = none) of the given upgrade type currently installed. */
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

    /** Ticks required to finish one batch of growth. */
    public int getProductionTicks() {
        if (hasCreativeUpgrade()) {
            return 20; // one batch per second
        }
        int tier = getUpgradeTier(UpgradeType.SPEED);
        return tier > 0 ? UpgradeEffects.speedFarmSeconds(tier) * 20 : BASE_PRODUCTION_TICKS;
    }

    /** Energy-consumption multiplier: 1 + speed% + yield% - efficiency% (floored). */
    private double getEnergyMultiplier() {
        double multiplier = 1.0;
        int speedTier = getUpgradeTier(UpgradeType.SPEED);
        int yieldTier = getUpgradeTier(UpgradeType.YIELD);
        int efficiencyTier = getUpgradeTier(UpgradeType.EFFICIENCY);
        if (speedTier > 0) {
            multiplier += UpgradeEffects.speedFarmEnergyPercent(speedTier) / 100.0;
        }
        if (yieldTier > 0) {
            multiplier += UpgradeEffects.yieldEnergyPercent(yieldTier) / 100.0;
        }
        if (efficiencyTier > 0) {
            multiplier -= UpgradeEffects.efficiencyFarmEnergyPercent(efficiencyTier) / 100.0;
        }
        return Math.max(0.05, multiplier);
    }

    /** Multiplier applied to each harvested product's count (1 = no yield upgrade). */
    public int getProductionMultiplier() {
        if (hasCreativeUpgrade()) {
            return Integer.MAX_VALUE;
        }
        int tier = getUpgradeTier(UpgradeType.YIELD);
        return tier > 0 ? UpgradeEffects.yieldMultiplier(tier) : 1;
    }

    /** Per-slot stack limit for output items (grows with the yield upgrade). */
    public int getStackLimit() {
        if (hasCreativeUpgrade()) {
            return Integer.MAX_VALUE;
        }
        int tier = getUpgradeTier(UpgradeType.YIELD);
        return tier > 0 ? UpgradeEffects.yieldStackLimit(tier) : DEFAULT_STACK_LIMIT;
    }

    /** Total internal energy capacity (grows with the efficiency upgrade). */
    public int getEnergyCapacity() {
        if (hasCreativeUpgrade()) {
            return Integer.MAX_VALUE;
        }
        int tier = getUpgradeTier(UpgradeType.EFFICIENCY);
        double multiplier = tier > 0 ? 1.0 + UpgradeEffects.efficiencyCachePercent(tier) / 100.0 : 1.0;
        return (int) (BASE_ENERGY_CAPACITY * multiplier);
    }

    /** Maximum FE received per tick from outside (grows with the efficiency upgrade). */
    public int getMaxEnergyReceive() {
        int tier = getUpgradeTier(UpgradeType.EFFICIENCY);
        double multiplier = tier > 0 ? 1.0 + UpgradeEffects.efficiencyInputPercent(tier) / 100.0 : 1.0;
        return (int) (MAX_ENERGY_RECEIVE * multiplier);
    }

    /** FE consumed per tick for the currently planted crops, after all upgrades. */
    public int getEnergyConsumptionPerTick() {
        if (hasCreativeUpgrade()) {
            return 0; // creative: no energy required
        }
        int planted = countPlanted();
        if (planted == 0) {
            return 0;
        }
        return Math.max(1, (int) (ENERGY_PER_TICK_PER_SLOT * (double) planted * getEnergyMultiplier()));
    }

    // ---------- ticking ----------

    public static void serverTick(Level level, BlockPos pos, BlockState state, FarmBlockEntity farm) {
        farm.tickServer(level);
    }

    private void tickServer(Level level) {
        if (hasCreativeUpgrade()) {
            // Creative: no energy is needed, so keep the internal cache full.
            energyStorage.setEnergy(getEnergyCapacity());
        } else {
            // Capacity may have shrunk after an efficiency upgrade was removed.
            energyStorage.clampEnergy();
        }

        int planted = countPlanted();
        boolean changed = false;

        if (planted == 0) {
            if (progress != 0 || growthBuffer != 0) {
                progress = 0;
                growthBuffer = 0;
                changed = true;
            }
        } else if (progress >= getProductionTicks()) {
            // batch ready -> harvest every planted seed at once (seed markers stay in place)
            List<ItemStack> products = computeProductsForAll(level);
            if (!products.isEmpty() && canFitProducts(products)) {
                for (ItemStack product : products) {
                    insertProduct(product);
                }
                progress = 0;
                growthBuffer = 0;
                changed = true;
            }
        } else {
            int costPerTick = getEnergyConsumptionPerTick();
            if (costPerTick <= 0) {
                // creative (or otherwise free): progress every tick without consuming energy
                progress++;
                changed = true;
            } else {
                int available = energyStorage.getEnergyStored();
                if (available > 0) {
                    int consume = Math.min(available, costPerTick);
                    energyStorage.consumeEnergy(consume, false);
                    growthBuffer += consume;
                    if (growthBuffer >= costPerTick) {
                        growthBuffer -= costPerTick;
                        progress++;
                    }
                    changed = true;
                }
            }
        }

        // Eject only every 5 ticks (staggered by position): many farms inserting into AE
        // on every tick is far too expensive.
        if (autoEject && Math.floorMod(level.getGameTime() + worldPosition.asLong(), 5) == 0) {
            // Prefer the AE2 network when connected; fall back to adjacent containers.
            if (ae2Node != null && ae2Node.isActive() && ejectToAe()) {
                changed = true;
            }
            if (ejectToAdjacent(level)) {
                changed = true;
            }
        }

        if (changed) {
            setChanged();
        }
        syncContent(level);
    }

    /** True when the farm holds any crop seed or produce (input or output slot, excluding upgrades). */
    private boolean hasContent() {
        for (int i = 0; i < INPUT_SLOTS; i++) {
            if (!inputHandler.getStackInSlot(i).isEmpty()) {
                return true;
            }
        }
        for (int i = 0; i < OUTPUT_SLOTS; i++) {
            if (!outputHandler.getStackInSlot(i).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** Toggles the block's {@link FarmBlock#CONTENT} blockstate so the model shows/hides the wheat. */
    private void syncContent(Level level) {
        if (level.isClientSide()) {
            return;
        }
        BlockState st = level.getBlockState(worldPosition);
        boolean content = hasContent();
        if (st.getValue(FarmBlock.CONTENT) != content) {
            level.setBlock(worldPosition, st.setValue(FarmBlock.CONTENT, content), 3);
        }
    }

    private int countPlanted() {
        int count = 0;
        for (int i = 0; i < INPUT_SLOTS; i++) {
            if (!inputHandler.getStackInSlot(i).isEmpty()) {
                count++;
            }
        }
        return count;
    }

    private List<ItemStack> computeProductsForAll(Level level) {
        List<ItemStack> products = new ArrayList<>();
        int multiplier = getProductionMultiplier();
        for (int i = 0; i < INPUT_SLOTS; i++) {
            ItemStack seed = inputHandler.getStackInSlot(i);
            if (!seed.isEmpty()) {
                for (ItemStack product : computeProducts(level, seed)) {
                    long count = (long) product.getCount() * multiplier;
                    int clamped = count > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) count;
                    products.add(product.copyWithCount(clamped));
                }
            }
        }
        return products;
    }

    private List<ItemStack> computeProducts(Level level, ItemStack seed) {
        Item item = seed.getItem();
        if (item instanceof BlockItem blockItem) {
            Block block = blockItem.getBlock();
            if (block instanceof CropBlock cropBlock) {
                if (level instanceof ServerLevel serverLevel) {
                    BlockState grown = cropBlock.getStateForAge(cropBlock.getMaxAge());
                    return Block.getDrops(grown, serverLevel, this.worldPosition, this);
                }
                return List.of();
            }
            if (block instanceof CocoaBlock) {
                // Cocoa is not a CropBlock (it grows on jungle logs) and only drops 3 beans at age 2;
                // its default state would give a single bean, i.e. no net gain.
                if (level instanceof ServerLevel serverLevel) {
                    BlockState grown = block.defaultBlockState().setValue(CocoaBlock.AGE, CocoaBlock.MAX_AGE);
                    List<ItemStack> drops = Block.getDrops(grown, serverLevel, this.worldPosition, this);
                    if (!drops.isEmpty()) {
                        return drops;
                    }
                }
                return List.of(new ItemStack(Items.COCOA_BEANS, 3));
            }
            if (block instanceof PitcherCropBlock) {
                // Pitcher crop is a growable crop but extends DoublePlantBlock (not CropBlock); the lower
                // half of a fully grown plant drops the pitcher plant item, younger ages drop a pod.
                if (level instanceof ServerLevel serverLevel) {
                    BlockState grown = block.defaultBlockState().setValue(PitcherCropBlock.AGE, PitcherCropBlock.MAX_AGE);
                    List<ItemStack> drops = Block.getDrops(grown, serverLevel, this.worldPosition, this);
                    if (!drops.isEmpty()) {
                        return drops;
                    }
                }
                return List.of(new ItemStack(Items.PITCHER_PLANT));
            }
            if (block instanceof StemBlock) {
                return stemFruit(block);
            }
            if (block instanceof SaplingBlock || block instanceof BambooSaplingBlock) {
                return saplingProducts(level, block);
            }
            if (SeedHelper.isBuddingBlock(block)) {
                return buddingCrystal(level, block);
            }
            // generic plants: sugarcane, cactus, flowers, mushrooms, saplings, bushes, ...
            if (level instanceof ServerLevel serverLevel) {
                List<ItemStack> drops = Block.getDrops(block.defaultBlockState(), serverLevel, this.worldPosition, this);
                return drops.isEmpty() ? List.of(new ItemStack(block.asItem())) : drops;
            }
            return List.of();
        }
        if (seed.is(Items.NETHER_WART)) {
            return List.of(new ItemStack(Items.NETHER_WART, 2 + level.random.nextInt(3)));
        }
        return List.of();
    }

    /** Pumpkin/melon stems produce their attached fruit (pumpkin_stem -> pumpkin). */
    private List<ItemStack> stemFruit(Block stemBlock) {
        ResourceLocation key = BuiltInRegistries.BLOCK.getKey(stemBlock);
        if (key == null) {
            return List.of();
        }
        String path = key.getPath();
        if (!path.endsWith("_stem")) {
            return List.of();
        }
        String fruitPath = path.substring(0, path.length() - "_stem".length());
        Block fruit = findBlock(key.getNamespace(), fruitPath);
        return fruit != null ? List.of(new ItemStack(fruit.asItem())) : List.of();
    }

    /** Tree saplings produce their log (oak_sapling -> oak_log); bamboo_sapling -> bamboo. */
    private List<ItemStack> saplingProducts(Level level, Block saplingBlock) {
        ResourceLocation key = BuiltInRegistries.BLOCK.getKey(saplingBlock);
        if (key == null) {
            return List.of(new ItemStack(saplingBlock.asItem()));
        }
        String path = key.getPath();
        String material;
        if (path.equals("mangrove_propagule")) {
            material = "mangrove";
        } else if (path.endsWith("_sapling")) {
            material = path.substring(0, path.length() - "_sapling".length());
        } else {
            return List.of(new ItemStack(saplingBlock.asItem()));
        }
        Block log = findBlock(key.getNamespace(), material + "_log");
        if (log == null) {
            log = findBlock(key.getNamespace(), material);
        }
        if (log != null && log != saplingBlock) {
            return List.of(new ItemStack(log.asItem(), 3 + level.random.nextInt(3)));
        }
        return List.of(new ItemStack(saplingBlock.asItem()));
    }

    /** Budding blocks produce their grown cluster's drops (budding_amethyst -> amethyst shard, AE2/GeOre likewise). */
    private List<ItemStack> buddingCrystal(Level level, Block buddingBlock) {
        ResourceLocation key = BuiltInRegistries.BLOCK.getKey(buddingBlock);
        if (key == null) {
            return List.of();
        }
        // Extract the crystal material by dropping the "budding" segment and quality words:
        // flawless_budding_quartz -> quartz, budding_amethyst -> amethyst,
        // entro_budding_fully -> entro, lattra_budding_mostly -> lattra,
        // flawless_budding_overload_crystal -> overload_crystal.
        String material = String.join("_", extractMaterialSegments(key.getPath()));
        String namespace = key.getNamespace();
        // Cluster naming varies: X_cluster (amethyst/quartz/entro/coal/overload_crystal)
        // or X_crystal_cluster (lattra).
        Block cluster = findBlock(namespace, material + "_cluster");
        if (cluster == null) {
            cluster = findBlock(namespace, material + "_crystal_cluster");
        }
        if (cluster == null || !(level instanceof ServerLevel serverLevel)) {
            return List.of();
        }

        // 1) code-based drops (vanilla amethyst, GeOre clusters)
        List<ItemStack> drops = Block.getDrops(cluster.defaultBlockState(), serverLevel, this.worldPosition, this);
        if (!drops.isEmpty()) {
            return drops;
        }

        // 2) some mods (AE2 and addons) return empty from code drops when no player entity is
        //    involved, so fall back to the block's data-driven loot table.
        ResourceLocation clusterId = BuiltInRegistries.BLOCK.getKey(cluster);
        ResourceKey<LootTable> lootKey = ResourceKey.create(Registries.LOOT_TABLE,
                ResourceLocation.fromNamespaceAndPath(clusterId.getNamespace(), "blocks/" + clusterId.getPath()));
        LootTable table = serverLevel.getServer().reloadableRegistries().getLootTable(lootKey);
        if (table != LootTable.EMPTY) {
            LootParams params = new LootParams.Builder(serverLevel)
                    .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(this.worldPosition))
                    .withParameter(LootContextParams.TOOL, ItemStack.EMPTY)
                    .withParameter(LootContextParams.BLOCK_STATE, cluster.defaultBlockState())
                    .create(LootContextParamSets.BLOCK);
            List<ItemStack> lootDrops = table.getRandomItems(params);
            if (!lootDrops.isEmpty()) {
                return lootDrops;
            }
        }
        return List.of();
    }

    @Nullable
    private static Block findBlock(String namespace, String path) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(namespace, path);
        return BuiltInRegistries.BLOCK.containsKey(id) ? BuiltInRegistries.BLOCK.get(id) : null;
    }

    private static List<String> extractMaterialSegments(String path) {
        List<String> remaining = new ArrayList<>();
        for (String segment : path.split("_")) {
            if (!segment.equals("budding") && !isQualityWord(segment)) {
                remaining.add(segment);
            }
        }
        return remaining;
    }

    private static boolean isQualityWord(String segment) {
        return switch (segment) {
            case "flawless", "flawed", "chipped", "cracked", "damaged",
                 "fully", "mostly", "half", "hardly" -> true;
            default -> false;
        };
    }

    private boolean canFitProducts(List<ItemStack> products) {
        for (ItemStack product : products) {
            if (!ItemHandlerHelper.insertItemStacked(outputHandler, product.copy(), true).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private void insertProduct(ItemStack product) {
        ItemHandlerHelper.insertItemStacked(outputHandler, product, false);
    }

    /** Pushes output items into adjacent containers when auto-eject is enabled. */
    private boolean ejectToAdjacent(Level level) {
        boolean moved = false;
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = worldPosition.relative(dir);
            // Farms are isolated from each other: never feed a neighbouring farm's input buffer.
            if (level.getBlockEntity(neighbor) instanceof FarmBlockEntity) {
                continue;
            }
            IItemHandler target = level.getCapability(Capabilities.ItemHandler.BLOCK, neighbor, dir.getOpposite());
            if (target == null) {
                continue;
            }
            for (int i = 0; i < OUTPUT_SLOTS; i++) {
                ItemStack stack = outputHandler.getStackInSlot(i);
                if (stack.isEmpty()) {
                    continue;
                }
                ItemStack remainder = ItemHandlerHelper.insertItemStacked(target, stack, false);
                if (remainder.getCount() != stack.getCount()) {
                    outputHandler.setStackInSlot(i, remainder);
                    moved = true;
                }
            }
        }
        return moved;
    }

    /** Pushes output items directly into the connected AE2 network. */
    private boolean ejectToAe() {
        boolean moved = false;
        for (int i = 0; i < OUTPUT_SLOTS; i++) {
            ItemStack stack = outputHandler.getStackInSlot(i);
            if (stack.isEmpty()) {
                continue;
            }
            long inserted = ae2Node.insert(stack);
            if (inserted <= 0) {
                continue;
            }
            int remaining = stack.getCount() - (int) Math.min(inserted, stack.getCount());
            outputHandler.setStackInSlot(i, remaining > 0 ? stack.copyWithCount(remaining) : ItemStack.EMPTY);
            moved = true;
        }
        return moved;
    }

    // ---------- energy ----------

    public void chargeFromItem(IEnergyStorage source) {
        int toExtract = Math.min(source.getEnergyStored(), MAX_CHARGE_PER_CLICK);
        int extracted = source.extractEnergy(toExtract, false);
        int accepted = energyStorage.receiveEnergy(extracted, false);
        if (accepted < extracted) {
            source.receiveEnergy(extracted - accepted, false);
        }
        setChanged();
    }

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

    public IItemHandler getItemHandler() {
        return combinedHandler;
    }

    /** The AE2 grid node (or null when AE2 is not installed). Exposed to AE2 via capability. */
    public IAe2Node getAe2Node() {
        return ae2Node;
    }

    // ---------- inventory access (used by the menu) ----------

    public IItemHandler getInputHandler() {
        return inputHandler;
    }

    public IItemHandler getOutputHandler() {
        return outputHandler;
    }

    public IItemHandler getUpgradeHandler() {
        return upgradeHandler;
    }

    /**
     * Shift-right-click insertion: adds the held upgrade if no upgrade of that type is present,
     * or replaces an existing lower-tier upgrade of the same type (returning the old one).
     */
    public boolean tryInsertUpgrade(Player player, ItemStack held, UpgradeItem upgrade) {
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

    public void toggleAutoEject() {
        this.autoEject = !this.autoEject;
        setChanged();
    }

    public boolean isAutoEject() {
        return this.autoEject;
    }

    /** True when the farm is connected to an active AE2 network (ready to receive products). */
    public boolean isAeConnected() {
        return ae2Node != null && ae2Node.isActive();
    }

    public void dropContents(Level level) {
        for (int i = 0; i < INPUT_SLOTS; i++) {
            ItemStack stack = inputHandler.getStackInSlot(i);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), stack);
            }
        }
        for (int i = 0; i < OUTPUT_SLOTS; i++) {
            ItemStack stack = outputHandler.getStackInSlot(i);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), stack);
            }
        }
        for (int i = 0; i < UPGRADE_SLOTS; i++) {
            ItemStack stack = upgradeHandler.getStackInSlot(i);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), stack);
            }
        }
    }

    // ---------- MenuProvider ----------

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.simpleautofarm.auto_farm");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new FarmMenu(containerId, playerInventory, this.worldPosition);
    }

    // ---------- ContainerData (energy/progress/auto-eject/capacity/ticks sync) ----------

    @Override
    public int getCount() {
        return 7;
    }

    @Override
    public int get(int index) {
        return switch (index) {
            case 0 -> energyStorage.getEnergyStored();
            case 1 -> progress;
            case 2 -> autoEject ? 1 : 0;
            case 3 -> getEnergyCapacity();
            case 4 -> getProductionTicks();
            case 5 -> getEnergyConsumptionPerTick();
            case 6 -> isAeConnected() ? 1 : 0;
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
        if (tag.contains("Input")) {
            inputHandler.deserializeNBT(registries, tag.getCompound("Input"));
        }
        if (tag.contains("Output")) {
            outputHandler.deserializeNBT(registries, tag.getCompound("Output"));
        }
        if (tag.contains("Upgrades")) {
            upgradeHandler.deserializeNBT(registries, tag.getCompound("Upgrades"));
        }
        progress = tag.getInt("Progress");
        growthBuffer = tag.getInt("GrowthBuffer");
        autoEject = tag.getBoolean("AutoEject");
        if (tag.contains("Energy")) {
            energyStorage.setEnergy(tag.getInt("Energy"));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Input", inputHandler.serializeNBT(registries));
        tag.put("Output", outputHandler.serializeNBT(registries));
        tag.put("Upgrades", upgradeHandler.serializeNBT(registries));
        tag.putInt("Progress", progress);
        tag.putInt("GrowthBuffer", growthBuffer);
        tag.putBoolean("AutoEject", autoEject);
        tag.putInt("Energy", energyStorage.getEnergyStored());
    }

    /**
     * Output handler that supports stacking beyond the item's own max stack size
     * (yield upgrade). Extraction stays capped at one vanilla stack (64) so the
     * player never pulls out more than a single group at a time.
     */
    private static final class DeepStackHandler extends ItemStackHandler {
        private final FarmBlockEntity be;

        DeepStackHandler(int size, FarmBlockEntity be) {
            super(size);
            this.be = be;
        }

        @Override
        public int getSlotLimit(int slot) {
            return be.getStackLimit();
        }

        @Override
        protected int getStackLimit(int slot, ItemStack stack) {
            return getSlotLimit(slot);
        }

        @Override
        public CompoundTag serializeNBT(HolderLookup.Provider provider) {
            ListTag items = new ListTag();
            for (int i = 0; i < this.stacks.size(); i++) {
                ItemStack stack = this.stacks.get(i);
                if (!stack.isEmpty()) {
                    CompoundTag itemTag = (CompoundTag) StackCodecs.OVERSIZED_ITEM_STACK_CODEC
                            .encodeStart(provider.createSerializationContext(NbtOps.INSTANCE), stack)
                            .getOrThrow();
                    itemTag.putInt("Slot", i);
                    items.add(itemTag);
                }
            }
            CompoundTag nbt = new CompoundTag();
            nbt.put("Items", items);
            nbt.putInt("Size", this.stacks.size());
            return nbt;
        }

        @Override
        public void deserializeNBT(HolderLookup.Provider provider, CompoundTag nbt) {
            setSize(nbt.contains("Size", Tag.TAG_INT) ? nbt.getInt("Size") : this.stacks.size());
            ListTag items = nbt.getList("Items", Tag.TAG_COMPOUND);
            for (int i = 0; i < items.size(); i++) {
                CompoundTag itemTag = items.getCompound(i);
                int slot = itemTag.getInt("Slot");
                if (slot >= 0 && slot < this.stacks.size()) {
                    StackCodecs.OVERSIZED_ITEM_STACK_CODEC
                            .parse(provider.createSerializationContext(NbtOps.INSTANCE), itemTag)
                            .resultOrPartial(err -> LOGGER.error("Failed to load farm output item in slot {}: {}", slot, err))
                            .ifPresent(stack -> this.stacks.set(slot, stack));
                }
            }
            onLoad();
        }
    }

    /**
     * Energy storage that can receive FE from the outside but cannot be extracted externally;
     * internal consumption bypasses {@link #canExtract()} via {@link #consumeEnergy(int, boolean)}.
     * Capacity is dynamic (efficiency upgrade).
     */
    static class MachineEnergyStorage extends EnergyStorage {
        private final FarmBlockEntity be;

        MachineEnergyStorage(FarmBlockEntity be) {
            super(BASE_ENERGY_CAPACITY, MAX_ENERGY_RECEIVE, 0);
            this.be = be;
        }

        @Override
        public int getMaxEnergyStored() {
            return be.getEnergyCapacity();
        }

        @Override
        public int receiveEnergy(int toReceive, boolean simulate) {
            if (!canReceive() || toReceive <= 0) {
                return 0;
            }
            int capacity = be.getEnergyCapacity();
            int received = Math.min(Math.min(be.getMaxEnergyReceive(), toReceive), Math.max(0, capacity - this.energy));
            if (!simulate) {
                this.energy += received;
            }
            return received;
        }

        int consumeEnergy(int amount, boolean simulate) {
            int consumed = Math.min(this.energy, amount);
            if (!simulate) {
                this.energy -= consumed;
            }
            return consumed;
        }

        void setEnergy(int value) {
            this.energy = Math.max(0, Math.min(be.getEnergyCapacity(), value));
        }

        void clampEnergy() {
            this.energy = Math.min(this.energy, be.getEnergyCapacity());
        }
    }
}
