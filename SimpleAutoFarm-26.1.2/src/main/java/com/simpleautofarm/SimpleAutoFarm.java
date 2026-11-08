package com.simpleautofarm;

import com.simpleautofarm.ae.Ae2Compat;
import com.simpleautofarm.block.BeehiveBlock;
import com.simpleautofarm.block.BeehiveBlockEntity;
import com.simpleautofarm.block.FarmBlock;
import com.simpleautofarm.block.FarmBlockEntity;
import com.simpleautofarm.block.FluidFarmBlock;
import com.simpleautofarm.block.FluidFarmBlockEntity;
import com.simpleautofarm.block.MobFarmBlock;
import com.simpleautofarm.block.MobFarmBlockEntity;
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
import com.simpleautofarm.client.ConfigScreens;
import com.simpleautofarm.client.FarmScreen;
import com.simpleautofarm.client.FluidFarmScreen;
import com.simpleautofarm.client.GeneratorScreen;
import com.simpleautofarm.client.MobFarmScreen;
import com.simpleautofarm.client.VoidSingularityRenderer;
import com.simpleautofarm.item.BeehiveItem;
import com.simpleautofarm.item.UpgradeItem;
import com.simpleautofarm.item.UpgradeType;
import com.simpleautofarm.item.VoidSingularityItem;
import com.simpleautofarm.item.WaterSourceItem;
import com.simpleautofarm.menu.BeehiveMenu;
import com.simpleautofarm.menu.FarmMenu;
import com.simpleautofarm.menu.FluidFarmMenu;
import com.simpleautofarm.menu.GeneratorMenu;
import com.simpleautofarm.menu.MobFarmMenu;
import com.simpleautofarm.util.TransferAdapters;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
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
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import com.simpleautofarm.event.EntityCardBinding;
import com.simpleautofarm.event.EntityCardProgress;
import com.simpleautofarm.event.SyntheticDeathDrops;
import com.simpleautofarm.item.EntityCardItem;
import net.minecraft.client.color.block.BlockTintSources;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

@Mod(SimpleAutoFarm.MODID)
public class SimpleAutoFarm {

    public static final String MODID = "simpleautofarm";

    public static Identifier prefix(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, MODID);

    /** Entity type a card is bound to. */
    public static final Supplier<DataComponentType<Optional<Identifier>>> CARD_ENTITY =
            DATA_COMPONENTS.register("entity_card_entity", () -> DataComponentType
                    .<Optional<Identifier>>builder()
                    .persistent(Identifier.CODEC.optionalFieldOf("entity").codec())
                    .networkSynchronized(ByteBufCodecs.optional(Identifier.STREAM_CODEC))
                    .build());

    /** Kills credited to a card so far. */
    public static final Supplier<DataComponentType<Integer>> CARD_KILLS =
            DATA_COMPONENTS.register("entity_card_kills", () -> DataComponentType.<Integer>builder()
                    .persistent(com.mojang.serialization.Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build());

    /** Kills a card needs before it becomes a sample. */
    public static final Supplier<DataComponentType<Integer>> CARD_REQUIRED =
            DATA_COMPONENTS.register("entity_card_required", () -> DataComponentType.<Integer>builder()
                    .persistent(com.mojang.serialization.Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build());

    public static final DeferredBlock<FarmBlock> AUTO_FARM_BLOCK = BLOCKS.registerBlock("auto_farm",
            FarmBlock::new,
            p -> p.mapColor(MapColor.METAL).sound(SoundType.METAL).destroyTime(3.0F).requiresCorrectToolForDrops());

    public static final DeferredItem<BlockItem> AUTO_FARM_ITEM = ITEMS.registerSimpleBlockItem("auto_farm", AUTO_FARM_BLOCK);

    public static final DeferredBlock<OreFarmBlock> ORE_FARM_BLOCK = BLOCKS.registerBlock("ore_farm",
            OreFarmBlock::new,
            p -> p.mapColor(MapColor.METAL).sound(SoundType.METAL).destroyTime(3.0F).requiresCorrectToolForDrops());

    public static final DeferredItem<BlockItem> ORE_FARM_ITEM = ITEMS.registerSimpleBlockItem("ore_farm", ORE_FARM_BLOCK);

    public static final DeferredBlock<BeehiveBlock> AUTO_BEEHIVE_BLOCK = BLOCKS.registerBlock("auto_beehive",
            BeehiveBlock::new,
            p -> p.mapColor(MapColor.WOOD).sound(SoundType.WOOD).destroyTime(3.0F).requiresCorrectToolForDrops());

    public static final DeferredItem<BeehiveItem> AUTO_BEEHIVE_ITEM = ITEMS.registerItem("auto_beehive",
            props -> new BeehiveItem(AUTO_BEEHIVE_BLOCK.get(), props));

    public static final DeferredBlock<FluidFarmBlock> FLUID_FARM_BLOCK = BLOCKS.registerBlock("fluid_farm",
            FluidFarmBlock::new,
            p -> p.mapColor(MapColor.METAL).sound(SoundType.METAL).destroyTime(3.0F).requiresCorrectToolForDrops());

    public static final DeferredItem<BlockItem> FLUID_FARM_ITEM = ITEMS.registerSimpleBlockItem("fluid_farm", FLUID_FARM_BLOCK);

    public static final DeferredBlock<MobFarmBlock> MOB_FARM_BLOCK = BLOCKS.registerBlock("mob_farm",
            MobFarmBlock::new,
            p -> p.mapColor(MapColor.METAL).sound(SoundType.METAL).destroyTime(3.0F).requiresCorrectToolForDrops());

    public static final DeferredItem<BlockItem> MOB_FARM_ITEM = ITEMS.registerSimpleBlockItem("mob_farm", MOB_FARM_BLOCK);

    public static final DeferredItem<EntityCardItem> ENTITY_CARD_ITEM = ITEMS.registerItem("entity_card",
            props -> new EntityCardItem(props.stacksTo(1)));

    public static final DeferredItem<EntityCardItem> ENTITY_CARD_ANIMAL = ITEMS.registerItem("entity_card_animal",
            props -> new EntityCardItem(props.stacksTo(1)));

    public static final DeferredItem<EntityCardItem> ENTITY_CARD_MONSTER = ITEMS.registerItem("entity_card_monster",
            props -> new EntityCardItem(props.stacksTo(1)));

    public static final DeferredItem<EntityCardItem> ENTITY_CARD_BOSS = ITEMS.registerItem("entity_card_boss",
            props -> new EntityCardItem(props.stacksTo(1)));

    public static final DeferredBlock<GeneratorBlock> GENERATOR_BLOCK = BLOCKS.registerBlock("generator",
            GeneratorBlock::new,
            p -> p.mapColor(MapColor.METAL).sound(SoundType.METAL).destroyTime(3.0F).requiresCorrectToolForDrops());

    public static final DeferredItem<BlockItem> GENERATOR_ITEM = ITEMS.registerSimpleBlockItem("generator", GENERATOR_BLOCK);

    public static final DeferredBlock<GeneratorProBlock> GENERATOR_PRO_BLOCK = BLOCKS.registerBlock("generator_pro",
            GeneratorProBlock::new,
            p -> p.mapColor(MapColor.METAL).sound(SoundType.METAL).destroyTime(3.0F).requiresCorrectToolForDrops());

    public static final DeferredItem<BlockItem> GENERATOR_PRO_ITEM = ITEMS.registerSimpleBlockItem("generator_pro", GENERATOR_PRO_BLOCK);

    public static final DeferredBlock<VoidSingularityBlock> VOID_SINGULARITY_BLOCK = BLOCKS.registerBlock("void_singularity",
            VoidSingularityBlock::new,
            p -> p.mapColor(MapColor.COLOR_BLACK).sound(SoundType.STONE).destroyTime(3.0F).requiresCorrectToolForDrops().lightLevel(s -> 7));

    public static final DeferredItem<VoidSingularityItem> VOID_SINGULARITY_ITEM = ITEMS.registerItem("void_singularity",
            props -> new VoidSingularityItem(VOID_SINGULARITY_BLOCK.get(), props));

    public static final DeferredBlock<WaterSourceBlock> WATER_SOURCE_BLOCK = BLOCKS.registerBlock("water_source",
            WaterSourceBlock::new,
            p -> p.mapColor(MapColor.WATER).sound(SoundType.GLASS).destroyTime(0.3F).noOcclusion()
                    .isViewBlocking((state, level, pos) -> false).isSuffocating((state, level, pos) -> false));

    public static final DeferredItem<WaterSourceItem> WATER_SOURCE_ITEM = ITEMS.registerItem("water_source",
            props -> new WaterSourceItem(WATER_SOURCE_BLOCK.get(), props));

    // ---------- upgrade items ----------

    public static final DeferredItem<Item> UPGRADE_BASE = ITEMS.registerSimpleItem("upgrade_base");

    public static final DeferredItem<UpgradeItem> SPEED_UPGRADE_T1 = ITEMS.registerItem("speed_upgrade_t1", props -> new UpgradeItem(UpgradeType.SPEED, 1, props));
    public static final DeferredItem<UpgradeItem> SPEED_UPGRADE_T2 = ITEMS.registerItem("speed_upgrade_t2", props -> new UpgradeItem(UpgradeType.SPEED, 2, props));
    public static final DeferredItem<UpgradeItem> SPEED_UPGRADE_T3 = ITEMS.registerItem("speed_upgrade_t3", props -> new UpgradeItem(UpgradeType.SPEED, 3, props));
    public static final DeferredItem<UpgradeItem> SPEED_UPGRADE_T4 = ITEMS.registerItem("speed_upgrade_t4", props -> new UpgradeItem(UpgradeType.SPEED, 4, props));

    public static final DeferredItem<UpgradeItem> EFFICIENCY_UPGRADE_T1 = ITEMS.registerItem("efficiency_upgrade_t1", props -> new UpgradeItem(UpgradeType.EFFICIENCY, 1, props));
    public static final DeferredItem<UpgradeItem> EFFICIENCY_UPGRADE_T2 = ITEMS.registerItem("efficiency_upgrade_t2", props -> new UpgradeItem(UpgradeType.EFFICIENCY, 2, props));
    public static final DeferredItem<UpgradeItem> EFFICIENCY_UPGRADE_T3 = ITEMS.registerItem("efficiency_upgrade_t3", props -> new UpgradeItem(UpgradeType.EFFICIENCY, 3, props));
    public static final DeferredItem<UpgradeItem> EFFICIENCY_UPGRADE_T4 = ITEMS.registerItem("efficiency_upgrade_t4", props -> new UpgradeItem(UpgradeType.EFFICIENCY, 4, props));

    public static final DeferredItem<UpgradeItem> YIELD_UPGRADE_T1 = ITEMS.registerItem("yield_upgrade_t1", props -> new UpgradeItem(UpgradeType.YIELD, 1, props));
    public static final DeferredItem<UpgradeItem> YIELD_UPGRADE_T2 = ITEMS.registerItem("yield_upgrade_t2", props -> new UpgradeItem(UpgradeType.YIELD, 2, props));
    public static final DeferredItem<UpgradeItem> YIELD_UPGRADE_T3 = ITEMS.registerItem("yield_upgrade_t3", props -> new UpgradeItem(UpgradeType.YIELD, 3, props));
    public static final DeferredItem<UpgradeItem> YIELD_UPGRADE_T4 = ITEMS.registerItem("yield_upgrade_t4", props -> new UpgradeItem(UpgradeType.YIELD, 4, props));

    public static final DeferredItem<UpgradeItem> MOTION_UPGRADE_T1 = ITEMS.registerItem("motion_upgrade_t1", props -> new UpgradeItem(UpgradeType.MOTION, 1, props));
    public static final DeferredItem<UpgradeItem> MOTION_UPGRADE_T2 = ITEMS.registerItem("motion_upgrade_t2", props -> new UpgradeItem(UpgradeType.MOTION, 2, props));
    public static final DeferredItem<UpgradeItem> MOTION_UPGRADE_T3 = ITEMS.registerItem("motion_upgrade_t3", props -> new UpgradeItem(UpgradeType.MOTION, 3, props));
    public static final DeferredItem<UpgradeItem> MOTION_UPGRADE_T4 = ITEMS.registerItem("motion_upgrade_t4", props -> new UpgradeItem(UpgradeType.MOTION, 4, props));

    public static final DeferredItem<UpgradeItem> FORTUNE_UPGRADE_T1 = ITEMS.registerItem("fortune_upgrade_t1", props -> new UpgradeItem(UpgradeType.FORTUNE, 1, props));
    public static final DeferredItem<UpgradeItem> FORTUNE_UPGRADE_T2 = ITEMS.registerItem("fortune_upgrade_t2", props -> new UpgradeItem(UpgradeType.FORTUNE, 2, props));
    public static final DeferredItem<UpgradeItem> FORTUNE_UPGRADE_T3 = ITEMS.registerItem("fortune_upgrade_t3", props -> new UpgradeItem(UpgradeType.FORTUNE, 3, props));
    public static final DeferredItem<UpgradeItem> FORTUNE_UPGRADE_T4 = ITEMS.registerItem("fortune_upgrade_t4", props -> new UpgradeItem(UpgradeType.FORTUNE, 4, props));

    public static final DeferredItem<UpgradeItem> CREATIVE_UPGRADE = ITEMS.registerItem("creative_upgrade",
            props -> new UpgradeItem(UpgradeType.CREATIVE, 1, props));

    public static boolean isUpgradeItem(Item item) {
        return item instanceof UpgradeItem;
    }

    public static final Supplier<BlockEntityType<FarmBlockEntity>> AUTO_FARM_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("auto_farm",
                    () -> new BlockEntityType<>(FarmBlockEntity::new, AUTO_FARM_BLOCK.get()));

    public static final Supplier<BlockEntityType<OreFarmBlockEntity>> ORE_FARM_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("ore_farm",
                    () -> new BlockEntityType<>(OreFarmBlockEntity::new, ORE_FARM_BLOCK.get()));

    public static final Supplier<BlockEntityType<BeehiveBlockEntity>> AUTO_BEEHIVE_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("auto_beehive",
                    () -> new BlockEntityType<>(BeehiveBlockEntity::new, AUTO_BEEHIVE_BLOCK.get()));

    public static final Supplier<BlockEntityType<FluidFarmBlockEntity>> FLUID_FARM_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("fluid_farm",
                    () -> new BlockEntityType<>(FluidFarmBlockEntity::new, FLUID_FARM_BLOCK.get()));

    public static final Supplier<BlockEntityType<MobFarmBlockEntity>> MOB_FARM_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("mob_farm",
                    () -> new BlockEntityType<>(MobFarmBlockEntity::new, MOB_FARM_BLOCK.get()));

    public static final Supplier<BlockEntityType<GeneratorBlockEntity>> GENERATOR_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("generator",
                    () -> new BlockEntityType<>(GeneratorBlockEntity::new, GENERATOR_BLOCK.get()));

    public static final Supplier<BlockEntityType<GeneratorProBlockEntity>> GENERATOR_PRO_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("generator_pro",
                    () -> new BlockEntityType<>(GeneratorProBlockEntity::new, GENERATOR_PRO_BLOCK.get()));

    public static final Supplier<BlockEntityType<VoidSingularityBlockEntity>> VOID_SINGULARITY_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("void_singularity",
                    () -> new BlockEntityType<>(VoidSingularityBlockEntity::new, VOID_SINGULARITY_BLOCK.get()));

    public static final Supplier<BlockEntityType<WaterSourceBlockEntity>> WATER_SOURCE_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("water_source",
                    () -> new BlockEntityType<>(WaterSourceBlockEntity::new, WATER_SOURCE_BLOCK.get()));

    public static final Supplier<MenuType<FarmMenu>> AUTO_FARM_MENU =
            MENUS.register("auto_farm", () -> IMenuTypeExtension.create(FarmMenu::new));

    public static final Supplier<MenuType<BeehiveMenu>> AUTO_BEEHIVE_MENU =
            MENUS.register("auto_beehive", () -> IMenuTypeExtension.create(BeehiveMenu::new));

    public static final Supplier<MenuType<FluidFarmMenu>> FLUID_FARM_MENU =
            MENUS.register("fluid_farm", () -> IMenuTypeExtension.create(FluidFarmMenu::new));

    public static final Supplier<MenuType<MobFarmMenu>> MOB_FARM_MENU =
            MENUS.register("mob_farm", () -> IMenuTypeExtension.create(MobFarmMenu::new));

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
                                output.accept(FLUID_FARM_ITEM.get());
                                output.accept(MOB_FARM_ITEM.get());
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
                                output.accept(ENTITY_CARD_ITEM.get());
                                output.accept(ENTITY_CARD_ANIMAL.get());
                                output.accept(ENTITY_CARD_MONSTER.get());
                                output.accept(ENTITY_CARD_BOSS.get());
                            })
                            .build());

    public SimpleAutoFarm(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        MENUS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        DATA_COMPONENTS.register(modEventBus);

        modEventBus.addListener(SimpleAutoFarm::registerCapabilities);

        // Common machine settings (config/simpleautofarm-common.toml).
        ModList.get().getModContainerById(MODID).ifPresent(container ->
                container.registerConfig(ModConfig.Type.COMMON, SimpleAutoFarmConfig.SPEC));

        if (FMLEnvironment.getDist().isClient()) {
            // The client types live in ConfigScreens, never here: a lambda mentioning Screen would put a client
            // type into this class's method descriptors, which RuntimeDistCleaner cannot strip on a server.
            ModList.get().getModContainerById(MODID).ifPresent(ConfigScreens::register);
        }

        // Keeps the mob farm's synthetic deaths from spawning their drops into the world.
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, SyntheticDeathDrops::onLivingDrops);

        // Binds entity cards on right-click; HIGH so it runs before the mob's own interaction.
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, EntityCardBinding::onEntityInteract);
        // Credits entity card progress on player kills; LOWEST so other death handlers run first.
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityCardProgress::onLivingDeath);

        if (FMLEnvironment.getDist().isClient()) {
            modEventBus.addListener(SimpleAutoFarm::onClientSetup);
            modEventBus.addListener(SimpleAutoFarm::onRegisterRenderers);
            modEventBus.addListener(SimpleAutoFarm::onRegisterBlockTintSources);
        }
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Energy.BLOCK, AUTO_FARM_BLOCK_ENTITY.get(),
                (farm, side) -> TransferAdapters.energy(farm.getEnergyStorage()));
        event.registerBlockEntity(Capabilities.Item.BLOCK, AUTO_FARM_BLOCK_ENTITY.get(),
                (farm, side) -> TransferAdapters.items(farm.getItemHandler()));
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ORE_FARM_BLOCK_ENTITY.get(),
                (farm, side) -> TransferAdapters.energy(farm.getEnergyStorage()));
        event.registerBlockEntity(Capabilities.Item.BLOCK, ORE_FARM_BLOCK_ENTITY.get(),
                (farm, side) -> TransferAdapters.items(farm.getItemHandler()));
        event.registerBlockEntity(Capabilities.Energy.BLOCK, AUTO_BEEHIVE_BLOCK_ENTITY.get(),
                (beehive, side) -> TransferAdapters.energy(beehive.getEnergyStorage()));
        event.registerBlockEntity(Capabilities.Item.BLOCK, AUTO_BEEHIVE_BLOCK_ENTITY.get(),
                (beehive, side) -> TransferAdapters.items(beehive.getItemHandler()));
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, AUTO_BEEHIVE_BLOCK_ENTITY.get(),
                (beehive, side) -> TransferAdapters.fluids(beehive.getFluidHandler()));
        event.registerBlockEntity(Capabilities.Energy.BLOCK, FLUID_FARM_BLOCK_ENTITY.get(),
                (fluidFarm, side) -> TransferAdapters.energy(fluidFarm.getEnergyStorage()));
        event.registerBlockEntity(Capabilities.Item.BLOCK, FLUID_FARM_BLOCK_ENTITY.get(),
                (fluidFarm, side) -> TransferAdapters.items(fluidFarm.getItemHandler()));
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, FLUID_FARM_BLOCK_ENTITY.get(),
                (fluidFarm, side) -> TransferAdapters.fluids(fluidFarm.getFluidHandler()));
        event.registerBlockEntity(Capabilities.Energy.BLOCK, MOB_FARM_BLOCK_ENTITY.get(),
                (mobFarm, side) -> TransferAdapters.energy(mobFarm.getEnergyStorage()));
        event.registerBlockEntity(Capabilities.Item.BLOCK, MOB_FARM_BLOCK_ENTITY.get(),
                (mobFarm, side) -> TransferAdapters.items(mobFarm.getItemHandler()));
        event.registerBlockEntity(Capabilities.Energy.BLOCK, GENERATOR_BLOCK_ENTITY.get(),
                (generator, side) -> TransferAdapters.energy(generator.getEnergyStorage()));
        event.registerBlockEntity(Capabilities.Item.BLOCK, GENERATOR_BLOCK_ENTITY.get(),
                (generator, side) -> TransferAdapters.items(generator.getAutomationHandler()));
        event.registerBlockEntity(Capabilities.Energy.BLOCK, GENERATOR_PRO_BLOCK_ENTITY.get(),
                (generator, side) -> TransferAdapters.energy(generator.getEnergyStorage()));
        event.registerBlockEntity(Capabilities.Item.BLOCK, GENERATOR_PRO_BLOCK_ENTITY.get(),
                (generator, side) -> TransferAdapters.items(generator.getAutomationHandler()));
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, GENERATOR_PRO_BLOCK_ENTITY.get(),
                (generator, side) -> TransferAdapters.fluids(generator.getAutomationTank()));
        event.registerBlockEntity(Capabilities.Item.BLOCK, VOID_SINGULARITY_BLOCK_ENTITY.get(),
                (voidBe, side) -> TransferAdapters.items(voidBe.getItemHandler()));
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, VOID_SINGULARITY_BLOCK_ENTITY.get(),
                (voidBe, side) -> TransferAdapters.fluids(voidBe.getFluidHandler()));
        event.registerBlockEntity(Capabilities.Energy.BLOCK, VOID_SINGULARITY_BLOCK_ENTITY.get(),
                (voidBe, side) -> TransferAdapters.energy(voidBe.getEnergyHandler()));
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, WATER_SOURCE_BLOCK_ENTITY.get(),
                (source, side) -> TransferAdapters.fluids(source.getFluidHandler()));

        if (ModList.get().isLoaded("ae2")) {
            Ae2Compat.registerCapabilities(event);
        }
    }

    @OnlyIn(Dist.CLIENT)
    private static void onClientSetup(RegisterMenuScreensEvent event) {
        event.register(AUTO_FARM_MENU.get(), FarmScreen::new);
        event.register(AUTO_BEEHIVE_MENU.get(), BeehiveScreen::new);
        event.register(FLUID_FARM_MENU.get(), FluidFarmScreen::new);
        event.register(MOB_FARM_MENU.get(), MobFarmScreen::new);
        event.register(GENERATOR_MENU.get(), GeneratorScreen::new);
    }

    @OnlyIn(Dist.CLIENT)
    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(VOID_SINGULARITY_BLOCK_ENTITY.get(), VoidSingularityRenderer::new);
    }

    /**
     * The water source's inner water block carries {@code tintindex: 0}, so it needs a tint source —
     * otherwise the water renders plain white. 26.1.2 replaced {@code BlockColor} with
     * {@code BlockTintSources}; the built-in {@code water()} one is exactly vanilla water's.
     */
    @OnlyIn(Dist.CLIENT)
    private static void onRegisterBlockTintSources(RegisterColorHandlersEvent.BlockTintSources event) {
        event.register(List.of(BlockTintSources.water()), WATER_SOURCE_BLOCK.get());
    }
}
