package com.simpleautofarm.ae;

import net.minecraft.world.item.ItemStack;

/**
 * Abstraction over the optional AE2 integration so that {@code FarmBlockEntity} never has to
 * reference any AE2 class directly (keeping AE2 a soft dependency).
 */
public interface IAe2Node {

    /** Schedules creation of the AE2 grid node on the host's first server tick. */
    void onLoad();

    /** Destroys the AE2 grid node (host block entity removed or chunk unloaded). */
    void onRemoved();

    /** True when the node is connected to an active, powered grid with a free channel. */
    boolean isActive();

    /**
     * Inserts as much of {@code stack} as possible into the AE2 network.
     *
     * @return the number of items actually inserted (0 if not connected or no space).
     */
    long insert(ItemStack stack);
}
