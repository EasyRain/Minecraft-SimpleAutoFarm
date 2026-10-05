package com.simpleautofarm;

import com.simpleautofarm.ae.Ae2Compat;
import com.simpleautofarm.compat.MekanismCompat;
import com.simpleautofarm.block.BeehiveBlock;
import com.simpleautofarm.block.BeehiveBlockEntity;
import com.simpleautofarm.block.FarmBlock;
import com.simpleautofarm.block.FarmBlockEntity;
import com.simpleautofarm.block.GeneratorBlock;
import com.simpleautofarm.block.GeneratorBlockEntity;
import com.simpleautofarm.block.GeneratorProBlock;
import com.simpleautofarm.block.GeneratorProBlockEntity;
import com.simpleautofarm.block.OreFarmBlock;
import com.simpleautofarm.block.OreFarmBlockEntity;
import com.simpleautofarm.block.VoidSingularityBlock;
import com.simpleautofarm.block.VoidSingularityBlockEntity;
import com.simpleautofarm.block.WaterSourceBlock;
import com.simpleautofarm.block.WaterSourceBlockEntity;
import com.simpleautofarm.client.BeehiveScreen;
import com.simpleautofarm.client.FarmScreen;
import com.simpleautofarm.client.GeneratorScreen;
import com.simpleautofarm.client.VoidSingularityRenderer;
import com.simpleautofarm.item.BeehiveItem;
import com.simpleautofarm.item.UpgradeItem;
import com.simpleautofarm.item.UpgradeType;
import com.simpleautofarm.item.VoidSingularityItem;
import com.simpleautofarm.item.WaterSourceItem;
import com.simpleautofarm.menu.BeehiveMenu;
import com.simpleautofarm.menu.FarmMenu;
import com.simpleautofarm.menu.GeneratorMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.minecraft.client.renderer.BiomeColors;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

@Mod(SimpleAutoFarm.MODID)
public class SimpleAutoFarm {

    public static final String MODID = "simpleautofarm";

    public static ResourceLocation prefix(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final DeferredBlock<Block> AUTO_FARM_BLOCK = BLOCKS.register("auto_farm",
            () -> new FarmBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .sound(SoundType.METAL)
                    .destroyTime(3.0F)
                    .requiresCorrectToolForDrops()));

    public static final DeferredHolder<Item, Item> AUTO_FARM_ITEM = ITEMS.register("auto_farm",
            () -> new BlockItem(AUTO_FARM_BLOCK.get(), new Item.Properties()));

    public static final DeferredBlock<Block> ORE_FARM_BLOCK = BLOCKS.register("ore_farm",
            () -> new OreFarmBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .sound(SoundType.METAL)
                    .destroyTime(3.0F)
                    .requiresCorrectToolForDrops()));

    public static final DeferredHolder<Item, Item> ORE_FARM_ITEM = ITEMS.register("ore_farm",
            () -> new BlockItem(ORE_FARM_BLOCK.get(), new Item.Properties()));

    public static final DeferredBlock<Block> AUTO_BEEHIVE_BLOCK = BLOCKS.register("auto_beehive",
            () -> new BeehiveBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .sound(SoundType.WOOD)
                    .destroyTime(3.0F)
                    .requiresCorrectToolForDrops()));

    public static final DeferredHolder<Item, Item> AUTO_BEEHIVE_ITEM = ITEMS.register("auto_beehive",
            () -> new BeehiveItem(AUTO_BEEHIVE_BLOCK.get(), new Item.Properties()));

    public static final DeferredBlock<Block> GENERATOR_BLOCK = BLOCKS.register("generator",
            () -> new GeneratorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .sound(SoundType.METAL)
                    .destroyTime(3.0F)
                    .requiresCorrectToolForDrops()));

    public static final DeferredHolder<Item, Item> GENERATOR_ITEM = ITEMS.register("generator",
            () -> new BlockItem(GENERATOR_BLOCK.get(), new Item.Properties()));

    public static final DeferredBlock<Block> GENERATOR_PRO_BLOCK = BLOCKS.register("generator_pro",
            () -> new GeneratorProBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .sound(SoundType.METAL)
                    .destroyTime(3.0F)
                    .requiresCorrectToolForDrops()));

    public static final DeferredHolder<Item, Item> GENERATOR_PRO_ITEM = ITEMS.register("generator_pro",
            () -> new BlockItem(GENERATOR_PRO_BLOCK.get(), new Item.Properties()));

    public static final DeferredBlock<Block> VOID_SINGULARITY_BLOCK = BLOCKS.register("void_singularity",
            () -> new VoidSingularityBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .sound(SoundType.STONE)
                    .destroyTime(3.0F)
                    .requiresCorrectToolForDrops()
                    .lightLevel(state -> 7)));

    public static final DeferredHolder<Item, Item> VOID_SINGULARITY_ITEM = ITEMS.register("void_singularity",
            () -> new VoidSingularityItem(VOID_SINGULARITY_BLOCK.get(), new Item.Properties()));

    public static final DeferredBlock<Block> WATER_SOURCE_BLOCK = BLOCKS.register("water_source",
            () -> new WaterSourceBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WATER)
                    .sound(SoundType.GLASS)
                    .destroyTime(0.3F)
                    .noOcclusion()
                    .isViewBlocking((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)));

    public static final DeferredHolder<Item, Item> WATER_SOURCE_ITEM = ITEMS.register("water_source",
            () -> new WaterSourceItem(WATER_SOURCE_BLOCK.get(), new Item.Properties()));

    // ---------- upgrade items ----------

    public static final DeferredHolder<Item, Item> UPGRADE_BASE = ITEMS.register("upgrade_base",
            () -> new Item(new Item.Properties()));

    public static final DeferredHolder<Item, Item> SPEED_UPGRADE_T1 = ITEMS.register("speed_upgrade_t1", () -> new UpgradeItem(UpgradeType.SPEED, 1, new Item.Properties()));
    public static final DeferredHolder<Item, Item> SPEED_UPGRADE_T2 = ITEMS.register("speed_upgrade_t2", () -> new UpgradeItem(UpgradeType.SPEED, 2, new Item.Properties()));
    public static final DeferredHolder<Item, Item> SPEED_UPGRADE_T3 = ITEMS.register("speed_upgrade_t3", () -> new UpgradeItem(UpgradeType.SPEED, 3, new Item.Properties()));
    public static final DeferredHolder<Item, Item> SPEED_UPGRADE_T4 = ITEMS.register("speed_upgrade_t4", () -> new UpgradeItem(UpgradeType.SPEED, 4, new Item.Properties()));

    public static final DeferredHolder<Item, Item> EFFICIENCY_UPGRADE_T1 = ITEMS.register("efficiency_upgrade_t1", () -> new UpgradeItem(UpgradeType.EFFICIENCY, 1, new Item.Properties()));
    public static final DeferredHolder<Item, Item> EFFICIENCY_UPGRADE_T2 = ITEMS.register("efficiency_upgrade_t2", () -> new UpgradeItem(UpgradeType.EFFICIENCY, 2, new Item.Properties()));
    public static final DeferredHolder<Item, Item> EFFICIENCY_UPGRADE_T3 = ITEMS.register("efficiency_upgrade_t3", () -> new UpgradeItem(UpgradeType.EFFICIENCY, 3, new Item.Properties()));
    public static final DeferredHolder<Item, Item> EFFICIENCY_UPGRADE_T4 = ITEMS.register("efficiency_upgrade_t4", () -> new UpgradeItem(UpgradeType.EFFICIENCY, 4, new Item.Properties()));

    public static final DeferredHolder<Item, Item> YIELD_UPGRADE_T1 = ITEMS.register("yield_upgrade_t1", () -> new UpgradeItem(UpgradeType.YIELD, 1, new Item.Properties()));
    public static final DeferredHolder<Item, Item> YIELD_UPGRADE_T2 = ITEMS.register("yield_upgrade_t2", () -> new UpgradeItem(UpgradeType.YIELD, 2, new Item.Properties()));
    public static final DeferredHolder<Item, Item> YIELD_UPGRADE_T3 = ITEMS.register("yield_upgrade_t3", () -> new UpgradeItem(UpgradeType.YIELD, 3, new Item.Properties()));
    public static final DeferredHolder<Item, Item> YIELD_UPGRADE_T4 = ITEMS.register("yield_upgrade_t4", () -> new UpgradeItem(UpgradeType.YIELD, 4, new Item.Properties()));

    public static final DeferredHolder<Item, Item> MOTION_UPGRADE_T1 = ITEMS.register("motion_upgrade_t1", () -> new UpgradeItem(UpgradeType.MOTION, 1, new Item.Properties()));
    public static final DeferredHolder<Item, Item> MOTION_UPGRADE_T2 = ITEMS.register("motion_upgrade_t2", () -> new UpgradeItem(UpgradeType.MOTION, 2, new Item.Properties()));
    public static final DeferredHolder<Item, Item> MOTION_UPGRADE_T3 = ITEMS.register("motion_upgrade_t3", () -> new UpgradeItem(UpgradeType.MOTION, 3, new Item.Properties()));
    public static final DeferredHolder<Item, Item> MOTION_UPGRADE_T4 = ITEMS.register("motion_upgrade_t4", () -> new UpgradeItem(UpgradeType.MOTION, 4, new Item.Properties()));

    public static final DeferredHolder<Item, Item> FORTUNE_UPGRADE_T1 = ITEMS.register("fortune_upgrade_t1", () -> new UpgradeItem(UpgradeType.FORTUNE, 1, new Item.Properties()));
    public static final DeferredHolder<Item, Item> FORTUNE_UPGRADE_T2 = ITEMS.register("fortune_upgrade_t2", () -> new UpgradeItem(UpgradeType.FORTUNE, 2, new Item.Properties()));
    public static final DeferredHolder<Item, Item> FORTUNE_UPGRADE_T3 = ITEMS.register("fortune_upgrade_t3", () -> new UpgradeItem(UpgradeType.FORTUNE, 3, new Item.Properties()));
    public static final DeferredHolder<Item, Item> FORTUNE_UPGRADE_T4 = ITEMS.register("fortune_upgrade_t4", () -> new UpgradeItem(UpgradeType.FORTUNE, 4, new Item.Properties()));

    public static final DeferredHolder<Item, Item> CREATIVE_UPGRADE = ITEMS.register("creative_upgrade",
            () -> new UpgradeItem(UpgradeType.CREATIVE, 1, new Item.Properties()));

    public static boolean isUpgradeItem(Item item) {
        return item instanceof UpgradeItem;
    }

    public static final Supplier<BlockEntityType<FarmBlockEntity>> AUTO_FARM_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("auto_farm",
                    () -> BlockEntityType.Builder.of(FarmBlockEntity::new, AUTO_FARM_BLOCK.get()).build(null));

    public static final Supplier<BlockEntityType<OreFarmBlockEntity>> ORE_FARM_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("ore_farm",
                    () -> BlockEntityType.Builder.of(OreFarmBlockEntity::new, ORE_FARM_BLOCK.get()).build(null));

    public static final Supplier<BlockEntityType<BeehiveBlockEntity>> AUTO_BEEHIVE_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("auto_beehive",
                    () -> BlockEntityType.Builder.of(BeehiveBlockEntity::new, AUTO_BEEHIVE_BLOCK.get()).build(null));

    public static final Supplier<BlockEntityType<GeneratorBlockEntity>> GENERATOR_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("generator",
                    () -> BlockEntityType.Builder.of(GeneratorBlockEntity::new, GENERATOR_BLOCK.get()).build(null));

    public static final Supplier<BlockEntityType<GeneratorProBlockEntity>> GENERATOR_PRO_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("generator_pro",
                    () -> BlockEntityType.Builder.of(GeneratorProBlockEntity::new, GENERATOR_PRO_BLOCK.get()).build(null));

    public static final Supplier<BlockEntityType<VoidSingularityBlockEntity>> VOID_SINGULARITY_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("void_singularity",
                    () -> BlockEntityType.Builder.of(VoidSingularityBlockEntity::new, VOID_SINGULARITY_BLOCK.get()).build(null));

    public static final Supplier<BlockEntityType<WaterSourceBlockEntity>> WATER_SOURCE_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("water_source",
                    () -> BlockEntityType.Builder.of(WaterSourceBlockEntity::new, WATER_SOURCE_BLOCK.get()).build(null));

    public static final Supplier<MenuType<FarmMenu>> AUTO_FARM_MENU =
            MENUS.register("auto_farm", () -> IMenuTypeExtension.create(FarmMenu::new));

    public static final Supplier<MenuType<BeehiveMenu>> AUTO_BEEHIVE_MENU =
            MENUS.register("auto_beehive", () -> IMenuTypeExtension.create(BeehiveMenu::new));

    public static final Supplier<MenuType<GeneratorMenu>> GENERATOR_MENU =
            MENUS.register("generator", () -> IMenuTypeExtension.create(GeneratorMenu::new));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> CREATIVE_TAB =
            CREATIVE_MODE_TABS.register("simpleautofarm_tab",
                    () -> CreativeModeTab.builder()
                            .title(Component.translatable("itemGroup.simpleautofarm"))
                            .icon(() -> new ItemStack(AUTO_FARM_ITEM.get()))
                            .displayItems((parameters, output) -> {
                                output.accept(AUTO_FARM_ITEM.get());
                                output.accept(ORE_FARM_ITEM.get());
                                output.accept(AUTO_BEEHIVE_ITEM.get());
                                output.accept(GENERATOR_ITEM.get());
                                output.accept(GENERATOR_PRO_ITEM.get());
                                output.accept(VOID_SINGULARITY_ITEM.get());
                                output.accept(WATER_SOURCE_ITEM.get());
                                output.accept(UPGRADE_BASE.get());
                                output.accept(SPEED_UPGRADE_T1.get());
                                output.accept(SPEED_UPGRADE_T2.get());
                                output.accept(SPEED_UPGRADE_T3.get());
                                output.accept(SPEED_UPGRADE_T4.get());
                                output.accept(EFFICIENCY_UPGRADE_T1.get());
                                output.accept(EFFICIENCY_UPGRADE_T2.get());
                                output.accept(EFFICIENCY_UPGRADE_T3.get());
                                output.accept(EFFICIENCY_UPGRADE_T4.get());
                                output.accept(YIELD_UPGRADE_T1.get());
                                output.accept(YIELD_UPGRADE_T2.get());
                                output.accept(YIELD_UPGRADE_T3.get());
                                output.accept(YIELD_UPGRADE_T4.get());
                                output.accept(MOTION_UPGRADE_T1.get());
                                output.accept(MOTION_UPGRADE_T2.get());
                                output.accept(MOTION_UPGRADE_T3.get());
                                output.accept(MOTION_UPGRADE_T4.get());
                                output.accept(FORTUNE_UPGRADE_T1.get());
                                output.accept(FORTUNE_UPGRADE_T2.get());
                                output.accept(FORTUNE_UPGRADE_T3.get());
                                output.accept(FORTUNE_UPGRADE_T4.get());
                                output.accept(CREATIVE_UPGRADE.get());
                            })
                            .build());

    public SimpleAutoFarm(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        MENUS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);

        modEventBus.addListener(SimpleAutoFarm::registerCapabilities);

        if (FMLEnvironment.dist.isClient()) {
            modEventBus.addListener(SimpleAutoFarm::onClientSetup);
            modEventBus.addListener(SimpleAutoFarm::onRegisterRenderers);
            modEventBus.addListener(SimpleAutoFarm::onRegisterBlockColors);
            modEventBus.addListener(SimpleAutoFarm::onRegisterItemColors);
        }
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, AUTO_FARM_BLOCK_ENTITY.get(),
                (farm, side) -> farm.getEnergyStorage());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, AUTO_FARM_BLOCK_ENTITY.get(),
                (farm, side) -> farm.getItemHandler());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ORE_FARM_BLOCK_ENTITY.get(),
                (farm, side) -> farm.getEnergyStorage());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ORE_FARM_BLOCK_ENTITY.get(),
                (farm, side) -> farm.getItemHandler());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, AUTO_BEEHIVE_BLOCK_ENTITY.get(),
                (beehive, side) -> beehive.getEnergyStorage());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, AUTO_BEEHIVE_BLOCK_ENTITY.get(),
                (beehive, side) -> beehive.getItemHandler());
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, AUTO_BEEHIVE_BLOCK_ENTITY.get(),
                (beehive, side) -> beehive.getFluidHandler());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, GENERATOR_BLOCK_ENTITY.get(),
                (generator, side) -> generator.getEnergyStorage());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, GENERATOR_BLOCK_ENTITY.get(),
                (generator, side) -> generator.getFuelHandler());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, GENERATOR_PRO_BLOCK_ENTITY.get(),
                (generator, side) -> generator.getEnergyStorage());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, GENERATOR_PRO_BLOCK_ENTITY.get(),
                (generator, side) -> generator.getFuelHandler());
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, GENERATOR_PRO_BLOCK_ENTITY.get(),
                (generator, side) -> generator.getFluidTank());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, VOID_SINGULARITY_BLOCK_ENTITY.get(),
                (voidBe, side) -> voidBe.getItemHandler());
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, VOID_SINGULARITY_BLOCK_ENTITY.get(),
                (voidBe, side) -> voidBe.getFluidHandler());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, VOID_SINGULARITY_BLOCK_ENTITY.get(),
                (voidBe, side) -> voidBe.getEnergyHandler());
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, WATER_SOURCE_BLOCK_ENTITY.get(),
                (source, side) -> source.getFluidHandler());

        if (ModList.get().isLoaded("ae2")) {
            Ae2Compat.registerCapabilities(event);
        }
        if (ModList.get().isLoaded("mekanism")) {
            MekanismCompat.registerCapabilities(event);
        }
    }

    @OnlyIn(Dist.CLIENT)
    private static void onClientSetup(RegisterMenuScreensEvent event) {
        event.register(AUTO_FARM_MENU.get(), FarmScreen::new);
        event.register(AUTO_BEEHIVE_MENU.get(), BeehiveScreen::new);
        event.register(GENERATOR_MENU.get(), GeneratorScreen::new);
    }

    @OnlyIn(Dist.CLIENT)
    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(VOID_SINGULARITY_BLOCK_ENTITY.get(), VoidSingularityRenderer::new);
    }

    /**
     * The water source's inner water block carries {@code tintindex: 0}, so it needs a colour source —
     * otherwise the water renders plain white. Vanilla water uses the biome water colour.
     */
    @OnlyIn(Dist.CLIENT)
    private static void onRegisterBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tintIndex) ->
                        level != null && pos != null ? BiomeColors.getAverageWaterColor(level, pos) : 0x3F76E4,
                WATER_SOURCE_BLOCK.get());
    }

    /** Same colour for the inventory/hand icon (the item model has no biome to look up). */
    @OnlyIn(Dist.CLIENT)
    private static void onRegisterItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> 0x3F76E4, WATER_SOURCE_ITEM.get());
    }
}
