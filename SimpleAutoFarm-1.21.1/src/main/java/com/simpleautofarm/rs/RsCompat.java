package com.simpleautofarm.rs;

import com.refinedmods.refinedstorage.api.core.Action;
import com.refinedmods.refinedstorage.api.network.Network;
import com.refinedmods.refinedstorage.api.network.node.NetworkNode;
import com.refinedmods.refinedstorage.api.network.storage.StorageNetworkComponent;
import com.refinedmods.refinedstorage.api.storage.Actor;
import com.refinedmods.refinedstorage.common.api.support.network.InWorldNetworkNodeContainer;
import com.refinedmods.refinedstorage.common.api.support.network.NetworkNodeContainerProvider;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;
import com.refinedmods.refinedstorage.neoforge.api.RefinedStorageNeoForgeApi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.BlockCapability;
import org.jetbrains.annotations.Nullable;

/**
 * Entry point for the optional Refined Storage integration. Every RS reference is confined to this
 * class, and the public entry point is a no-op unless RS is loaded, so the mod still runs (and
 * loads) with RS absent.
 *
 * <p>Unlike AE2, the machine does not need to join the RS network: RS exposes its
 * {@link NetworkNodeContainerProvider} capability on every network block (cable, controller, disk
 * drive, machine), so an adjacent cable or machine is enough. From that provider we find the
 * network's {@link StorageNetworkComponent} -- the same {@code RootStorage} RS' own exporter writes
 * to -- and insert the output straight into the network storage. No importer, interface or external
 * storage block is needed.</p>
 *
 * <p>Nothing is ever deleted here: the returned count is exactly what the network accepted, and the
 * caller only removes that many items from the output slot. A missing, unpowered or full network
 * therefore leaves the stack untouched (the caller then falls back to adjacent containers).</p>
 */
public final class RsCompat {

    private static final String MOD_ID = "refinedstorage";

    private RsCompat() {
    }

    /** True when Refined Storage is installed. Contains no RS class reference on purpose. */
    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    /**
     * Offers {@code stack} to every adjacent Refined Storage network.
     *
     * @return the number of items the network actually accepted (0 when RS is absent, no neighbour
     *         exposes an RS network, or the network has no room)
     */
    public static long insertIntoAdjacentNetwork(Level level, BlockPos pos, ItemStack stack) {
        if (!isLoaded() || stack.isEmpty() || stack.getCount() <= 0 || level.isClientSide()) {
            return 0L;
        }
        return insert(level, pos, stack);
    }

    /** RS-only code: only reached after {@link #isLoaded()} returned true. */
    private static long insert(Level level, BlockPos pos, ItemStack stack) {
        BlockCapability<NetworkNodeContainerProvider, Direction> capability =
                RefinedStorageNeoForgeApi.INSTANCE.getNetworkNodeContainerProviderCapability();
        // ItemResource keeps the item's data components, so it maps to exactly the same entry RS
        // would create when the item enters through a grid/importer.
        ItemResource key = ItemResource.ofItemStack(stack);
        Actor actor = () -> "SimpleAutoFarm";
        long remaining = stack.getCount();
        for (Direction dir : Direction.values()) {
            // Side context follows the vanilla/NeoForge convention: the face of the neighbour that
            // points back at us.
            NetworkNodeContainerProvider provider =
                    level.getCapability(capability, pos.relative(dir), dir.getOpposite());
            if (provider == null) {
                continue;
            }
            Network network = networkOf(provider);
            if (network == null) {
                continue;
            }
            remaining -= insertInto(network, key, remaining, actor);
            if (remaining <= 0L) {
                break;
            }
        }
        return stack.getCount() - remaining;
    }

    /** Inserts up to {@code amount} items into the network's storage component. */
    private static long insertInto(Network network, ItemResource key, long amount, Actor actor) {
        if (amount <= 0L) {
            return 0L;
        }
        StorageNetworkComponent storage;
        try {
            storage = network.getComponent(StorageNetworkComponent.class);
        } catch (IllegalArgumentException e) {
            // RS' ComponentMap throws for an unregistered component (e.g. a network built without
            // storage support): nothing can be accepted, so leave the items in the machine.
            return 0L;
        }
        if (storage == null) {
            return 0L;
        }
        long inserted = storage.insert(key, amount, Action.EXECUTE, actor);
        // Defensive clamp: never report more than was offered (the caller uses this to shrink slots).
        return Math.max(0L, Math.min(inserted, amount));
    }

    /**
     * The network a container provider belongs to, or {@code null} when its node is not part of a
     * network yet (e.g. a cable that is still being initialised).
     */
    @Nullable
    private static Network networkOf(NetworkNodeContainerProvider provider) {
        for (InWorldNetworkNodeContainer container : provider.getContainers()) {
            if (container == null || container.isRemoved()) {
                continue;
            }
            NetworkNode node = container.getNode();
            if (node == null) {
                continue;
            }
            Network network = node.getNetwork();
            if (network != null) {
                return network;
            }
        }
        return null;
    }
}
