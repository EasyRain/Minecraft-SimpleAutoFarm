package com.simpleautofarm.rs;

import com.mojang.logging.LogUtils;
import com.refinedmods.refinedstorage.api.core.Action;
import com.refinedmods.refinedstorage.api.network.Network;
import com.refinedmods.refinedstorage.api.network.node.GraphNetworkComponent;
import com.refinedmods.refinedstorage.api.network.node.NetworkNode;
import com.refinedmods.refinedstorage.api.network.storage.StorageNetworkComponent;
import com.refinedmods.refinedstorage.api.storage.Actor;
import com.refinedmods.refinedstorage.common.api.RefinedStorageApi;
import com.refinedmods.refinedstorage.common.api.support.network.InWorldNetworkNodeContainer;
import com.refinedmods.refinedstorage.common.api.support.network.NetworkNodeContainerProvider;
import com.refinedmods.refinedstorage.common.support.network.SimpleConnectionStrategy;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;
import com.refinedmods.refinedstorage.neoforge.api.RefinedStorageNeoForgeApi;
import com.simpleautofarm.SimpleAutoFarm;
import com.simpleautofarm.block.FarmBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Entry point for the optional Refined Storage integration. Every RS reference is confined to this
 * class, and the machine only ever sees {@link IRsNetworkNode}, so the mod still loads and runs
 * with RS absent.
 *
 * <p><b>Eject priority:</b> AE2 first, then Refined Storage, then plain neighbouring
 * {@code IItemHandler}s, and finally the products stay inside the machine. This is the default and
 * can be swapped with the {@code machines.autoEjectPriority} config option ({@code AE2} / {@code RS},
 * default {@code AE2}). When the higher-priority network is missing, inactive (no channel/power for
 * AE2) or takes nothing, the same tick falls through to the other network; only when both digital
 * networks accept nothing does the old adjacent-container path run.</p>
 *
 * <p><b>Why the machine becomes a real RS network block:</b> an RS cable only connects to a
 * neighbour that exposes the {@code NetworkNodeContainerProvider} capability (cable side: every
 * container's {@link InWorldNetworkNodeContainer#canAcceptIncomingConnection
 * canAcceptIncomingConnection}). Exposing a container (as done in {@link Host}) makes the cable
 * render and connect to the machine, and lets us insert into the network through our own node
 * instead of probing neighbours.</p>
 *
 * <p><b>The node is deliberately a bare {@link NetworkNode}:</b> it does not implement RS'
 * {@code StorageProvider}, so RS' {@code StorageNetworkComponent} never adds a storage source for
 * this machine. The RS grid therefore cannot see, extract or autocraft the products out of the
 * output slots -- only our own eject logic ever writes into the network, which keeps the
 * auto-eject toggle authoritative.</p>
 */
public final class RsCompat {

    private static final String MOD_ID = "refinedstorage";
    private static final Logger LOGGER = LogUtils.getLogger();

    private RsCompat() {
    }

    /** True when Refined Storage is installed. Contains no RS class reference on purpose. */
    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    /**
     * Creates the RS network node of this machine, or returns {@code null} when RS is not installed.
     * The nested class is only resolved when this method actually runs with RS present, so no RS
     * type is loaded without the mod.
     */
    public static IRsNetworkNode create(FarmBlockEntity farm) {
        if (!isLoaded()) {
            return null;
        }
        return Host.create(farm);
    }

    /**
     * Registers the RS in-world network-node provider capability for every machine that runs the
     * farm logic. Call only when {@link ModList#isLoaded(String) ModList.get().isLoaded("refinedstorage")}
     * is true.
     */
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        Host.registerCapabilities(event);
    }

    /**
     * RS-only implementation. Kept as a nested class so loading {@link RsCompat} itself resolves no
     * RS type; it is only resolved after {@link #isLoaded()} returned true.
     */
    private static final class Host implements IRsNetworkNode {

        private static final Actor ACTOR = () -> "SimpleAutoFarm";

        private final FarmBlockEntity farm;
        private final Node node = new Node();
        private final NetworkNodeContainerProvider provider;
        private final InWorldNetworkNodeContainer container;
        private boolean initialized;

        static IRsNetworkNode create(FarmBlockEntity farm) {
            return new Host(farm);
        }

        static void registerCapabilities(RegisterCapabilitiesEvent event) {
            BlockCapability<NetworkNodeContainerProvider, Direction> capability =
                    RefinedStorageNeoForgeApi.INSTANCE.getNetworkNodeContainerProviderCapability();
            event.registerBlockEntity(capability, SimpleAutoFarm.AUTO_FARM_BLOCK_ENTITY.get(),
                    (farm, side) -> providerOf(farm));
            event.registerBlockEntity(capability, SimpleAutoFarm.ORE_FARM_BLOCK_ENTITY.get(),
                    (farm, side) -> providerOf(farm));
            event.registerBlockEntity(capability, SimpleAutoFarm.AUTO_BEEHIVE_BLOCK_ENTITY.get(),
                    (beehive, side) -> providerOf(beehive));
            event.registerBlockEntity(capability, SimpleAutoFarm.FLUID_FARM_BLOCK_ENTITY.get(),
                    (fluidFarm, side) -> providerOf(fluidFarm));
            event.registerBlockEntity(capability, SimpleAutoFarm.MOB_FARM_BLOCK_ENTITY.get(),
                    (mobFarm, side) -> providerOf(mobFarm));
        }

        @Nullable
        private static NetworkNodeContainerProvider providerOf(FarmBlockEntity farm) {
            return farm.getRsNode() instanceof Host host ? host.provider() : null;
        }

        Host(FarmBlockEntity farm) {
            this.farm = farm;
            this.provider = RefinedStorageApi.INSTANCE.createNetworkNodeContainerProvider();
            // SimpleConnectionStrategy accepts an incoming cable from every side and tries to
            // connect to all six neighbours, exactly like RS' own machines (which use its
            // colour-aware subclass). No colors here, so every cable may attach.
            this.container = RefinedStorageApi.INSTANCE
                    .createNetworkNodeContainer(farm, this.node)
                    .name("simpleautofarm")
                    .connectionStrategy(new SimpleConnectionStrategy(farm.getBlockPos()))
                    .build();
            this.provider.addContainer(this.container);
        }

        NetworkNodeContainerProvider provider() {
            return this.provider;
        }

        @Override
        public void onLoad() {
            if (this.initialized) {
                return;
            }
            Level level = this.farm.getLevel();
            if (level == null || level.isClientSide()) {
                return;
            }
            // RS queues the actual network build on the server thread and ignores a second call
            // when the node already belongs to a network.
            this.provider.initialize(level, () -> {
            });
            this.initialized = true;
        }

        @Override
        public void onRemoved() {
            this.initialized = false;
            Level level = this.farm.getLevel();
            if (level == null || level.isClientSide()) {
                return;
            }
            // Sends the network through its remove/split/merge path; a no-op when already detached.
            this.provider.remove(level);
        }

        @Override
        public boolean isConnected() {
            Network network = this.node.getNetwork();
            if (network == null) {
                return false;
            }
            // RS gives every initialised node a network of its own, so a lone machine also has a
            // (useless) network. "Connected" therefore means the machine shares that network with
            // at least one other RS block (cable, controller, disk drive, machine ...).
            GraphNetworkComponent graph;
            try {
                graph = network.getComponent(GraphNetworkComponent.class);
            } catch (IllegalArgumentException e) {
                return false;
            }
            return graph != null && graph.getContainers().size() > 1;
        }

        @Override
        public long insert(ItemStack stack) {
            if (stack.isEmpty()) {
                return 0L;
            }
            Network network = this.node.getNetwork();
            if (network != null) {
                long inserted = insertInto(network, stack, stack.getCount());
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("RS node insert at {}: net=true inserted={}", this.farm.getBlockPos(), inserted);
                }
                if (inserted > 0L) {
                    return inserted;
                }
            }
            // Fallback for the ticks before RS has initialised our own node (or if RS refused the
            // container): read the network of a neighbouring RS block directly.
            return probeAdjacent(stack);
        }

        /**
         * The old position-based route: query every neighbouring RS block's network-node provider
         * and insert into whichever network accepts the stack.
         */
        private long probeAdjacent(ItemStack stack) {
            Level level = this.farm.getLevel();
            if (level == null || level.isClientSide()) {
                return 0L;
            }
            BlockPos pos = this.farm.getBlockPos();
            BlockCapability<NetworkNodeContainerProvider, Direction> capability =
                    RefinedStorageNeoForgeApi.INSTANCE.getNetworkNodeContainerProviderCapability();
            ItemResource key = ItemResource.ofItemStack(stack);
            long remaining = stack.getCount();
            for (Direction dir : Direction.values()) {
                NetworkNodeContainerProvider provider =
                        level.getCapability(capability, pos.relative(dir), dir.getOpposite());
                Network network = provider == null ? null : networkOf(provider);
                long inserted = network == null ? 0L : insertInto(network, key, remaining);
                remaining -= inserted;
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("RS probe at {}: dir={} cap={} containers={} net={} inserted={}",
                            pos, dir, provider != null,
                            provider == null ? 0 : provider.getContainers().size(),
                            network != null, inserted);
                }
                if (remaining <= 0L) {
                    break;
                }
            }
            return stack.getCount() - remaining;
        }

        /** The network a container provider belongs to, or {@code null} while it has none. */
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

        private static long insertInto(Network network, ItemStack stack, long amount) {
            return insertInto(network, ItemResource.ofItemStack(stack), amount);
        }

        private static long insertInto(Network network, ItemResource key, long amount) {
            if (amount <= 0L) {
                return 0L;
            }
            StorageNetworkComponent storage;
            try {
                storage = network.getComponent(StorageNetworkComponent.class);
            } catch (IllegalArgumentException e) {
                // RS' ComponentMap throws for an unregistered component: nothing can be accepted.
                return 0L;
            }
            if (storage == null) {
                return 0L;
            }
            long inserted = storage.insert(key, amount, Action.EXECUTE, ACTOR);
            // Defensive clamp: never report more than was offered (the caller shrinks the slot).
            return Math.max(0L, Math.min(inserted, amount));
        }
    }

    /**
     * A graph-only RS node: it carries no components, so RS sees the machine as a plain network
     * member (like a relay) and never as a storage source for the products in the output slots.
     */
    private static final class Node implements NetworkNode {

        private Network network;

        @Override
        public Network getNetwork() {
            return this.network;
        }

        @Override
        public void setNetwork(Network network) {
            this.network = network;
        }
    }
}
