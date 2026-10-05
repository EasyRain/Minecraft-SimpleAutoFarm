package com.simpleautofarm.item;

public enum UpgradeType {
    SPEED,
    EFFICIENCY,
    YIELD,
    CREATIVE,
    /** Speed + efficiency of the same tier in one item, so all four lines fit the three slots. */
    MOTION,
    /** Random extra output per product (equal chance of 1x .. (tier+1)x). */
    FORTUNE;

    /**
     * Whether the two upgrades must never sit in the same machine. {@link #MOTION} delivers Speed and
     * Efficiency of the same tier in one item — it <em>replaces</em> the two single-purpose lines, so
     * it is mutually exclusive with either of them (Speed and Efficiency themselves are independent
     * and may share a machine).
     */
    public boolean conflictsWith(UpgradeType other) {
        if (this == other) {
            return false;
        }
        if (this == MOTION) {
            return other == SPEED || other == EFFICIENCY;
        }
        if (other == MOTION) {
            return this == SPEED || this == EFFICIENCY;
        }
        return false;
    }
}
