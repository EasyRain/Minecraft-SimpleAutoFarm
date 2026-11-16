package com.simpleautofarm.rs;

import net.minecraft.world.item.ItemStack;

/**
 * Abstraction over the optional Refined Storage integration so that {@code FarmBlockEntity} never
 * has to reference any RS class directly (keeping Refined Storage a soft dependency).
 */
public interface IRsNetworkNode {

    /**
     * Joins the adjacent RS network: builds the node's container and queues RS' network
     * initialisation. Idempotent; safe to call from every server tick.
     */
    void onLoad();

    /** Leaves the RS network (machine removed or chunk unloaded). Idempotent. */
    void onRemoved();

    /**
     * True once this machine's RS node shares a network with at least one other RS block (a cable,
     * controller, disk drive or machine). RS gives every node a network of its own, so a lone
     * machine is not "connected".
     */
    boolean isConnected();

    /**
     * Inserts as much of {@code stack} as possible into the RS network. The machine's own node is
     * preferred (it is a first-class RS network block, so a cable touches it), with the six
     * directions probed as a fallback for the ticks before RS has initialised the node.
     *
     * @return the number of items actually accepted (0 if not connected or no space)
     */
    long insert(ItemStack stack);
}
