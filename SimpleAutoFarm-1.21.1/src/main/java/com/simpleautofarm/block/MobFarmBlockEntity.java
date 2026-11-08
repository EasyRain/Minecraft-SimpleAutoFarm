package com.simpleautofarm.block;

import com.simpleautofarm.SimpleAutoFarm;
import com.simpleautofarm.menu.MobFarmMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.CombinedInvWrapper;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Simple Auto Mob Farm: the crop farm's machinery (FE energy, speed/efficiency/yield/fortune/
 * creative upgrades, deep stacks, auto-eject, AE2 output) fed with spawn eggs instead of seeds.
 *
 * <p>Spawn eggs are only markers and are never consumed. A batch spawns nothing: the egg's entity
 * type is instantiated (but never added to the world) and its own loot table is rolled with a fake
 * player as the killer, so the machine reproduces the mob's normal player-kill drops — bones and
 * rotten flesh for a zombie, a wither skeleton skull for a wither skeleton, a music disc for a
 * creeper killed by a skeleton (the luck bonus covers the looting branch). Entities without a usable
 * loot table, or whose type cannot be created, simply produce nothing instead of breaking the batch.
 *
 * <p>The machine has two product pages: the inherited 27-slot output band and a second 27-slot
 * overflow band that takes whatever the first one cannot hold. Both are plain product storage; the
 * page is only a view, and auto-eject/AE2 drain both of them.
 */
public class MobFarmBlockEntity extends FarmBlockEntity {

    /** Overflow page: same size as the main product band. */
    public static final int OVERFLOW_SLOTS = OUTPUT_SLOTS;

    /** The overflow page's storage; also the {@link #getOverflowOutput()} of this machine. */
    private final ItemStackHandler overflowHandler = createOutputHandler(OVERFLOW_SLOTS);

    /** Inputs, both product pages, so pipes can read the machine from any side. */
    private final IItemHandler automationHandler;

    public MobFarmBlockEntity(BlockPos pos, BlockState state) {
        super(SimpleAutoFarm.MOB_FARM_BLOCK_ENTITY.get(), pos, state);
        this.automationHandler = new CombinedInvWrapper(
                (ItemStackHandler) getInputHandler(),
                (ItemStackHandler) getOutputHandler(),
                this.overflowHandler);
    }

    /** Only spawn eggs belong in the input slots. */
    @Override
    public boolean isValidSample(ItemStack stack) {
        return stack.getItem() instanceof SpawnEggItem;
    }

    /**
     * Rolls the mob's own loot table: the entity is created for the loot context only (it is never
     * added to the level), and a fake player is the killer so {@code killed_by_player} drops and the
     * looting luck path behave like a real player kill. Missing loot tables and uncreatable
     * entity types produce nothing instead of throwing.
     */
    @Override
    protected List<ItemStack> computeProducts(Level level, ItemStack seed) {
        if (!(level instanceof ServerLevel serverLevel) || !(seed.getItem() instanceof SpawnEggItem egg)) {
            return List.of();
        }
        try {
            EntityType<?> type = egg.getType(seed);
            if (type == null) {
                return List.of();
            }
            Entity entity = type.create(serverLevel);
            if (!(entity instanceof LivingEntity mob)) {
                return List.of();
            }
            ResourceKey<LootTable> key = mob.getLootTable();
            LootTable lootTable = serverLevel.getServer().reloadableRegistries().getLootTable(key);
            if (lootTable == LootTable.EMPTY) {
                return List.of();
            }
            Player killer = FakePlayerFactory.getMinecraft(serverLevel);
            LootParams params = new LootParams.Builder(serverLevel)
                    .withParameter(LootContextParams.THIS_ENTITY, mob)
                    .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(worldPosition))
                    .withParameter(LootContextParams.DAMAGE_SOURCE,
                            serverLevel.damageSources().playerAttack(killer))
                    .withOptionalParameter(LootContextParams.ATTACKING_ENTITY, killer)
                    .withOptionalParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, killer)
                    .withOptionalParameter(LootContextParams.LAST_DAMAGE_PLAYER, killer)
                    .withLuck(rollFortune(level))
                    .create(LootContextParamSets.ENTITY);
            return lootTable.getRandomItems(params);
        } catch (Exception exception) {
            // A broken or missing loot table must never break the machine: produce nothing instead.
            return List.of();
        }
    }

    /** The second product page behind the main output band. */
    @Override
    protected IItemHandler getOverflowOutput() {
        return overflowHandler;
    }

    public IItemHandler getOverflowHandler() {
        return overflowHandler;
    }

    @Override
    public IItemHandler getItemHandler() {
        return automationHandler;
    }

    // ---------- MenuProvider ----------

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.simpleautofarm.mob_farm");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new MobFarmMenu(containerId, playerInventory, this.worldPosition);
    }

    // ---------- persistence ----------

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Overflow")) {
            overflowHandler.deserializeNBT(registries, tag.getCompound("Overflow"));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Overflow", overflowHandler.serializeNBT(registries));
    }

    @Override
    public void dropContents(Level level) {
        super.dropContents(level);
        for (int i = 0; i < OVERFLOW_SLOTS; i++) {
            ItemStack stack = overflowHandler.getStackInSlot(i);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), stack);
            }
        }
    }
}
