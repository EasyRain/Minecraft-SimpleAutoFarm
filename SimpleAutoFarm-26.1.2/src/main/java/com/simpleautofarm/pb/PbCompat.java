package com.simpleautofarm.pb;

import cy.jdkdigital.productivebees.common.entity.bee.ProductiveBee;
import cy.jdkdigital.productivebees.common.item.BeeCage;
import cy.jdkdigital.productivebees.util.BeeHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

import java.util.List;

/**
 * Entry point for the optional Productive Bees integration. Every reference to a Productive Bees
 * type lives inside this package and is only reached after a
 * {@link ModList#isLoaded(String) ModList.get().isLoaded("productivebees")} check, so the mod
 * still loads and runs with the resource-bee mod absent.
 *
 * <p>The item lookups deliberately go through registry names and item tags instead of
 * Productive Bees' Java constants: {@code productivelib:upgrade_block} is registered under the
 * same id in every version we support, while the Java-visible holder types differ between them.
 *
 * <p>The machine's food slots take the place of a Productive Bees feeder: a bee that carries no
 * flower it marked in the world is fed from those slots, and without a flower it accepts there it
 * produces nothing at all — the same "no flower, no harvest" rule a Productive Bees hive lives by.
 */
public final class PbCompat {

    public static final String MODID = "productivebees";

    /** Productive Bees' own "Block Upgrade": changes honeycombs into comb blocks. */
    private static final Identifier COMB_BLOCK_UPGRADE =
            Identifier.fromNamespaceAndPath("productivelib", "upgrade_block");

    /**
     * Items Productive Bees bees are tempted by / eat: the vanilla flower tag plus the mod's
     * honey treat (the mod's own {@code productivebees:bee_tempt_items}).
     */
    private static final TagKey<Item> BEE_TEMPT_ITEMS =
            ItemTags.create(Identifier.fromNamespaceAndPath(MODID, "bee_tempt_items"));

    /**
     * Productive Bees' hardcoded flower-driven bees: their harvest <em>is</em> the flower
     * ({@code BeeHelper.getBeeProduce} reads the flower block instead of a recipe), so the machine
     * has to reproduce those three branches from the flower item in its food slots.
     */
    private static final String LUMBER_BEE = "productivebees:lumber_bee";
    private static final String QUARRY_BEE = "productivebees:quarry_bee";
    private static final String DYE_BEE = "productivebees:dye_bee";

    private PbCompat() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded(MODID);
    }

    /** True for Productive Bees' Block Upgrade, which turns honeycomb output into comb blocks. */
    public static boolean isCombBlockUpgrade(ItemStack stack) {
        if (stack.isEmpty() || !isLoaded()) {
            return false;
        }
        // getKey is @Nullable in 26.1.2: compare the other way round so a missing key cannot NPE.
        return COMB_BLOCK_UPGRADE.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    /** Whether the stack may sit in the beehive's food storage slots (flowers / honey treats). */
    public static boolean isBeeFood(ItemStack stack) {
        return !stack.isEmpty() && (stack.is(ItemTags.FLOWERS) || stack.is(BEE_TEMPT_ITEMS));
    }

    /**
     * Shift-click routing hint for the food page: is this stack <em>obviously</em> something a bee
     * could want? The slots themselves take anything at all (exactly like Productive Bees' own
     * feeder, see {@code FeederBlockEntity}), and the real flower check happens when the machine
     * looks for one ({@link #produce}); this only decides where shift-click parks a stack, so junk
     * does not get dumped into the 27 food slots.
     *
     * <p>Shapes it recognises: vanilla flowers and honey treats, block-shaped flowers (logs for a
     * lumber bee, coal blocks for a coal bee ...) and fluid containers (a lava bucket for a lava
     * bee). Bees whose flower is a plain item (chemlib:iodine, ars_nouveau:air_essence ...) are
     * placed by hand — the slot accepts them.
     *
     * <p>Deliberately independent of Productive Bees: it is also consulted with the mod absent.
     */
    public static boolean isFoodStorageItem(ItemStack stack) {
        if (isBeeFood(stack)) {
            return true;
        }
        return stack.getItem() instanceof BlockItem || fluidInContainer(stack) != null;
    }

    /** True for a Productive Bees bee cage that actually holds a bee. */
    public static boolean isBeeCage(ItemStack stack) {
        if (stack.isEmpty() || !isLoaded()) {
            return false;
        }
        // 26.1.2 moved isFilled from an instance method to a static one on BeeCage.
        return stack.getItem() instanceof BeeCage && BeeCage.isFilled(stack);
    }

    /** The bee type id stored in the cage, or {@code null} when it is not a filled cage. */
    public static String getBeeType(ItemStack stack) {
        if (stack.isEmpty() || !isLoaded() || !(stack.getItem() instanceof BeeCage)) {
            return null;
        }
        return BeeCage.getBeeType(stack);
    }

    /**
     * Rolls one harvest of the bee inside the cage, using Productive Bees' own produce rules
     * (recipe match by bee type, per-output chance and count).
     *
     * <p>A bee whose cage carries the flower it marked in the world is handed to the original
     * lookup untouched. Otherwise the food slots act as its feeder: no flower the bee accepts in
     * there, no harvest.
     *
     * @param food             the machine's food slots, standing in for a Productive Bees feeder
     * @param combBlockUpgrade whether the hive has a Block Upgrade installed, which swaps
     *                         honeycomb results for comb blocks
     */
    public static List<ItemStack> produce(Level level, ItemStack cage, IItemHandler food, boolean combBlockUpgrade) {
        if (!isLoaded() || !(cage.getItem() instanceof BeeCage)) {
            return List.of();
        }
        // 26.1.2 returns the Bee directly (nullable) instead of an Entity.
        Bee bee = BeeCage.getEntityFromStack(cage, level, true);
        if (bee == null) {
            return List.of();
        }
        if (bee.getSavedFlowerPos() != null) {
            // The cage remembers the flower the bee marked in the world (vanilla "flower_pos"), and
            // that is the only flower source Productive Bees itself reads: let it do the work.
            return BeeHelper.getBeeProduce(level, bee, combBlockUpgrade, 1.0D);
        }
        ItemStack flower = findFlower(bee, food);
        if (flower.isEmpty()) {
            return List.of();
        }
        String beeId = bee.getEncodeId();
        if (LUMBER_BEE.equals(beeId) || QUARRY_BEE.equals(beeId)) {
            // BeeHelper: new ItemStack(flowerBlock.asItem(), rolls) — rolls is 1 for this machine
            return List.of(flower.copyWithCount(1));
        }
        if (DYE_BEE.equals(beeId)) {
            // BeeHelper: the flower's single-ingredient crafting result, again `rolls` = 1
            ItemStack dye = BeeHelper.getRecipeOutputFromInput(level, flower.getItem());
            if (dye.isEmpty()) {
                return List.of();
            }
            dye.setCount(1);
            return List.of(dye);
        }
        return BeeHelper.getBeeProduce(level, bee, combBlockUpgrade, 1.0D);
    }

    /**
     * The first stack in the food slots the bee accepts as its flower, or an empty stack.
     * Productive Bees' feeder draws a random matching entry; taking the first one keeps the
     * machine predictable, since the player controls the order of the food page.
     */
    private static ItemStack findFlower(Bee bee, IItemHandler food) {
        if (food == null) {
            return ItemStack.EMPTY;
        }
        for (int i = 0; i < food.getSlots(); i++) {
            ItemStack stack = food.getStackInSlot(i);
            if (acceptsAsFlower(bee, stack)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * Whether the stack is a flower for this bee, asked through Productive Bees' own predicates so
     * the rules stay the mod's: {@code isFlowerItem} covers flower item/tag definitions and fluid
     * buckets, the block shape of the stack covers plain {@code flowerBlock} definitions (a
     * {@code ConfigurableBee} only tests tag/item/fluid there, so a fluxite bee and its like would
     * otherwise be missed), and any other container holding the bee's fluid is matched through the
     * fluid's own block state — the branch {@code ConfigurableBee.isFlowerBlock} tests.
     */
    private static boolean acceptsAsFlower(Bee bee, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (!(bee instanceof ProductiveBee productiveBee)) {
            // Plain vanilla bees: Productive Bees' default flowering tag is minecraft:flowers.
            return stack.is(ItemTags.FLOWERS);
        }
        if (productiveBee.isFlowerItem(stack)) {
            return true;
        }
        // The same fallback ProductiveBee#isFlowerItem makes on its own, repeated here because
        // ConfigurableBee#isFlowerItem overrides it without any "flowerBlock" branch.
        if (stack.getItem() instanceof BlockItem blockItem
                && productiveBee.isFlowerBlock(blockItem.getBlock().defaultBlockState())) {
            return true;
        }
        Fluid fluid = fluidInContainer(stack);
        return fluid != null && productiveBee.isFlowerBlock(fluid.defaultFluidState().createLegacyBlock());
    }

    /** The fluid an item holds (bucket, cell, tank ...), or {@code null} when it holds none. */
    private static Fluid fluidInContainer(ItemStack stack) {
        ResourceHandler<FluidResource> handler =
                stack.getCapability(Capabilities.Fluid.ITEM, ItemAccess.forStack(stack));
        if (handler == null || handler.size() <= 0 || handler.getAmountAsInt(0) <= 0) {
            return null;
        }
        FluidResource resource = handler.getResource(0);
        return resource == null || resource.isEmpty() ? null : resource.getFluid();
    }
}
