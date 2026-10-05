package com.simpleautofarm.ae;

import appeng.api.AECapabilities;
import appeng.api.networking.IInWorldGridNodeHost;
import com.simpleautofarm.SimpleAutoFarm;
import com.simpleautofarm.block.FarmBlockEntity;
import net.minecraft.world.level.ItemLike;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * Entry point for the optional AE2 integration. Every AE2 reference is confined to this package,
 * and every method is only invoked when AE2 is actually present, so the mod still loads and runs
 * without AE2 installed.
 */
public final class Ae2Compat {

    private Ae2Compat() {
    }

    /**
     * Creates the farm's AE2 grid node, or returns {@code null} when AE2 is not installed.
     *
     * @param icon the block shown for this machine in AE2's network-tool device list
     */
    public static IAe2Node create(FarmBlockEntity farm, ItemLike icon) {
        if (!ModList.get().isLoaded("ae2")) {
            return null;
        }
        return new Ae2GridNode(farm, icon);
    }

    /**
     * Registers the AE2 in-world grid-node-host capability for the auto farm. Call only when
     * {@link ModList#isLoaded(String) ModList.get().isLoaded("ae2")} is true.
     */
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                SimpleAutoFarm.AUTO_FARM_BLOCK_ENTITY.get(),
                (farm, ctx) -> (IInWorldGridNodeHost) farm.getAe2Node());
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                SimpleAutoFarm.ORE_FARM_BLOCK_ENTITY.get(),
                (farm, ctx) -> (IInWorldGridNodeHost) farm.getAe2Node());
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                SimpleAutoFarm.AUTO_BEEHIVE_BLOCK_ENTITY.get(),
                (beehive, ctx) -> (IInWorldGridNodeHost) beehive.getAe2Node());
    }
}
