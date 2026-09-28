package com.simpleautofarm.item;

public enum UpgradeType {
    SPEED,
    EFFICIENCY,
    YIELD,
    CREATIVE,
    /** Speed + efficiency of the same tier in one item, so all four lines fit the three slots. */
    MOTION,
    /** Random extra output per product (equal chance of 1x .. (tier+1)x). */
    FORTUNE
}
