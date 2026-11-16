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
     * Probes the six directly adjacent blocks and reports whether one of them belongs to a live RS
     * network with a <b>powered</b> controller. A network without a controller (two of our own
     * machines touching, a dead-end cable, a lone disk drive) or with a controller that has no
     * energy does not count, mirroring AE2's "attached but unpowered is not connected". The cached
     * network pointer of our own node is not trusted: RS leaves it stale after a cable is broken.
     */
    boolean isConnected();

    /**
     * Inserts as much of {@code stack} as possible into the adjacent RS network(s). The machine's
     * own node is used only while a live neighbouring member still points at the same network
     * instance; otherwise the six directions are probed, so a broken cable can never receive items.
     *
     * @return the number of items actually accepted (0 if not connected or no space)
     */
    long insert(ItemStack stack);
}
