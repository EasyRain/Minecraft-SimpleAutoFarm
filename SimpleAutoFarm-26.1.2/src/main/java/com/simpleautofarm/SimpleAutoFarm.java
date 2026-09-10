package com.simpleautofarm;

import com.simpleautofarm.ae.Ae2Compat;
import com.simpleautofarm.block.FarmBlock;
import com.simpleautofarm.block.FarmBlockEntity;
import com.simpleautofarm.block.GeneratorBlock;
import com.simpleautofarm.block.GeneratorBlockEntity;
import com.simpleautofarm.block.GeneratorProBlock;
import com.simpleautofarm.block.GeneratorProBlockEntity;
import com.simpleautofarm.client.FarmScreen;
import com.simpleautofarm.client.GeneratorScreen;
import com.simpleautofarm.item.UpgradeItem;
import com.simpleautofarm.item.UpgradeType;
import com.simpleautofarm.menu.FarmMenu;
import com.simpleautofarm.menu.GeneratorMenu;
import com.simpleautofarm.util.TransferAdapters;
import net.minecraft.core.registries.Registries;
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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

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

    public static final DeferredBlock<FarmBlock> AUTO_FARM_BLOCK = BLOCKS.registerBlock("auto_farm",
            FarmBlock::new,
            p -> p.mapColor(MapColor.METAL).sound(SoundType.METAL).destroyTime(3.0F).requiresCorrectToolForDrops());

    public static final DeferredItem<BlockItem> AUTO_FARM_ITEM = ITEMS.registerSimpleBlockItem("auto_farm", AUTO_FARM_BLOCK);

    public static final DeferredBlock<GeneratorBlock> GENERATOR_BLOCK = BLOCKS.registerBlock("generator",
            GeneratorBlock::new,
            p -> p.mapColor(MapColor.METAL).sound(SoundType.METAL).destroyTime(3.0F).requiresCorrectToolForDrops());

    public static final DeferredItem<BlockItem> GENERATOR_ITEM = ITEMS.registerSimpleBlockItem("generator", GENERATOR_BLOCK);

    public static final DeferredBlock<GeneratorProBlock> GENERATOR_PRO_BLOCK = BLOCKS.registerBlock("generator_pro",
            GeneratorProBlock::new,
            p -> p.mapColor(MapColor.METAL).sound(SoundType.METAL).destroyTime(3.0F).requiresCorrectToolForDrops());

    public static final DeferredItem<BlockItem> GENERATOR_PRO_ITEM = ITEMS.registerSimpleBlockItem("generator_pro", GENERATOR_PRO_BLOCK);

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

    public static final DeferredItem<UpgradeItem> CREATIVE_UPGRADE = ITEMS.registerItem("creative_upgrade",
            props -> new UpgradeItem(UpgradeType.CREATIVE, 1, props));

    public static boolean isUpgradeItem(Item item) {
        return item instanceof UpgradeItem;
    }

    public static final Supplier<BlockEntityType<FarmBlockEntity>> AUTO_FARM_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("auto_farm",
                    () -> new BlockEntityType<>(FarmBlockEntity::new, AUTO_FARM_BLOCK.get()));

    public static final Supplier<BlockEntityType<GeneratorBlockEntity>> GENERATOR_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("generator",
                    () -> new BlockEntityType<>(GeneratorBlockEntity::new, GENERATOR_BLOCK.get()));

    public static final Supplier<BlockEntityType<GeneratorProBlockEntity>> GENERATOR_PRO_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("generator_pro",
                    () -> new BlockEntityType<>(GeneratorProBlockEntity::new, GENERATOR_PRO_BLOCK.get()));

    public static final Supplier<MenuType<FarmMenu>> AUTO_FARM_MENU =
            MENUS.register("auto_farm", () -> IMenuTypeExtension.create(FarmMenu::new));

    public static final Supplier<MenuType<GeneratorMenu>> GENERATOR_MENU =
            MENUS.register("generator", () -> IMenuTypeExtension.create(GeneratorMenu::new));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> CREATIVE_TAB =
            CREATIVE_MODE_TABS.register("simpleautofarm_tab",
                    () -> CreativeModeTab.builder()
                            .title(Component.translatable("itemGroup.simpleautofarm"))
                            .icon(() -> new ItemStack(AUTO_FARM_ITEM.get()))
                            .displayItems((parameters, output) -> {
                                output.accept(AUTO_FARM_ITEM.get());
                                output.accept(GENERATOR_ITEM.get());
                                output.accept(GENERATOR_PRO_ITEM.get());
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

        if (FMLEnvironment.getDist().isClient()) {
            modEventBus.addListener(SimpleAutoFarm::onClientSetup);
        }
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Energy.BLOCK, AUTO_FARM_BLOCK_ENTITY.get(),
                (farm, side) -> TransferAdapters.energy(farm.getEnergyStorage()));
        event.registerBlockEntity(Capabilities.Item.BLOCK, AUTO_FARM_BLOCK_ENTITY.get(),
                (farm, side) -> TransferAdapters.items(farm.getItemHandler()));
        event.registerBlockEntity(Capabilities.Energy.BLOCK, GENERATOR_BLOCK_ENTITY.get(),
                (generator, side) -> TransferAdapters.energy(generator.getEnergyStorage()));
        event.registerBlockEntity(Capabilities.Item.BLOCK, GENERATOR_BLOCK_ENTITY.get(),
                (generator, side) -> TransferAdapters.items(generator.getFuelHandler()));
        event.registerBlockEntity(Capabilities.Energy.BLOCK, GENERATOR_PRO_BLOCK_ENTITY.get(),
                (generator, side) -> TransferAdapters.energy(generator.getEnergyStorage()));
        event.registerBlockEntity(Capabilities.Item.BLOCK, GENERATOR_PRO_BLOCK_ENTITY.get(),
                (generator, side) -> TransferAdapters.items(generator.getFuelHandler()));
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, GENERATOR_PRO_BLOCK_ENTITY.get(),
                (generator, side) -> TransferAdapters.fluids(generator.getFluidTank()));

        if (ModList.get().isLoaded("ae2")) {
            Ae2Compat.registerCapabilities(event);
        }
    }

    @OnlyIn(Dist.CLIENT)
    private static void onClientSetup(RegisterMenuScreensEvent event) {
        event.register(AUTO_FARM_MENU.get(), FarmScreen::new);
        event.register(GENERATOR_MENU.get(), GeneratorScreen::new);
    }
}
