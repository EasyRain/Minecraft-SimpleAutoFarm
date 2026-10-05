package com.simpleautofarm.ae;

import appeng.api.config.Actionable;
import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import appeng.api.util.AECableType;
import com.simpleautofarm.block.FarmBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

/**
 * The AE2 integration node for the auto farm. Exposed to AE2 via the
 * {@link appeng.api.AECapabilities#IN_WORLD_GRID_NODE_HOST} capability (registered by
 * {@link Ae2Compat}), so the farm acts as a channel-using machine on the ME network and can push
 * its output directly into the network's item storage.
 *
 * <p>This class is only ever loaded when AE2 is present; {@link FarmBlockEntity} references it
 * solely through {@link IAe2Node}.</p>
 */
public final class Ae2GridNode
        implements IAe2Node, IInWorldGridNodeHost, IGridNodeListener<Ae2GridNode>, IActionHost {

    private final FarmBlockEntity farm;
    private final IManagedGridNode mainNode;
    private final IActionSource actionSource;

    Ae2GridNode(FarmBlockEntity farm, ItemLike icon) {
        this.farm = farm;
        this.actionSource = IActionSource.ofMachine(this);
        this.mainNode = GridHelper.createManagedNode(this, this)
                // AE2's network tool groups its device list by IGridNode#getVisualRepresentation()
                // and silently skips every node whose representation is null (see
                // NetworkStatus#getKey) -- without this icon the farm is invisible there even
                // though it is perfectly connected to the grid.
                .setVisualRepresentation(icon)
                .setInWorldNode(true)
                .setTagName("farm")
                .setFlags(GridFlags.REQUIRE_CHANNEL);
    }

    // ---------- IAe2Node ----------

    @Override
    public void onLoad() {
        GridHelper.onFirstTick(this.farm, be -> this.mainNode.create(be.getLevel(), be.getBlockPos()));
    }

    @Override
    public void onRemoved() {
        this.mainNode.destroy();
    }

    @Override
    public boolean isActive() {
        return this.mainNode.isActive();
    }

    @Override
    public long insert(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0L;
        }
        MEStorage inventory = this.storage();
        if (inventory == null) {
            return 0L;
        }
        AEItemKey key = AEItemKey.of(stack);
        if (key == null) {
            return 0L;
        }
        return inventory.insert(key, stack.getCount(), Actionable.MODULATE, this.actionSource);
    }

    @Override
    public long insertFluid(FluidStack stack) {
        if (stack.isEmpty()) {
            return 0L;
        }
        MEStorage inventory = this.storage();
        if (inventory == null) {
            return 0L;
        }
        AEFluidKey key = AEFluidKey.of(stack);
        if (key == null) {
            return 0L;
        }
        return inventory.insert(key, stack.getAmount(), Actionable.MODULATE, this.actionSource);
    }

    /** The network's storage, or {@code null} while the node is off the grid. */
    @Nullable
    private MEStorage storage() {
        IGrid grid = this.mainNode.getGrid();
        if (grid == null) {
            return null;
        }
        IStorageService storage = grid.getStorageService();
        if (storage == null) {
            return null;
        }
        return storage.getInventory();
    }

    // ---------- IInWorldGridNodeHost ----------

    @Nullable
    @Override
    public IGridNode getGridNode(Direction dir) {
        return this.mainNode.getNode();
    }

    @Override
    public AECableType getCableConnectionType(Direction dir) {
        return AECableType.SMART;
    }

    // ---------- IActionHost ----------

    @Override
    public IGridNode getActionableNode() {
        return this.mainNode.getNode();
    }

    // ---------- IGridNodeListener ----------

    @Override
    public void onSaveChanges(Ae2GridNode nodeOwner, IGridNode node) {
        this.farm.setChanged();
    }

    @Override
    public void onGridChanged(Ae2GridNode nodeOwner, IGridNode node) {
        this.farm.setChanged();
    }

    @Override
    public void onStateChanged(Ae2GridNode nodeOwner, IGridNode node, State state) {
        this.farm.setChanged();
    }
}
