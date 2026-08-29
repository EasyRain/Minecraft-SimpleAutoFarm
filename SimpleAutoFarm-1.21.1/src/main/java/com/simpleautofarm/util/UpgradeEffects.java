package com.simpleautofarm.util;

/**
 * Central table of upgrade effects. Each array is indexed by tier-1 (0..3 = T1..T4).
 *
 * Interpretation of the percentages (see the design notes):
 * <ul>
 *   <li>"增加 X%"  = multiply by (1 + X/100).</li>
 *   <li>"减少 X%" (X&lt;100) = multiply by (1 - X/100).</li>
 *   <li>Generator fuel consumption "降低 200/400/600/800%" is read as a divisor
 *       (÷2 / ÷4 / ÷6 / ÷8).</li>
 * </ul>
 *
 * Same-parameter modifiers stack additively; the generator's fuel rate combines
 * speed (multiplicative) with efficiency (divisor) per the design notes.
 */
public final class UpgradeEffects {

    private UpgradeEffects() {
    }

    private static int idx(int tier) {
        return Math.max(0, Math.min(3, tier - 1));
    }

    // ---- speed (farm): production time in seconds + extra energy consumption ----
    private static final int[] SPEED_FARM_SECONDS = {20, 10, 5, 1};
    private static final int[] SPEED_FARM_ENERGY_PERCENT = {20, 40, 60, 80};

    // ---- speed (generator): extra energy output + extra fuel consumption ----
    private static final int[] SPEED_GEN_ENERGY_PERCENT = {100, 200, 400, 800};
    private static final int[] SPEED_GEN_FUEL_PERCENT = {150, 300, 600, 1200};

    // ---- efficiency (farm): reduced energy consumption ----
    private static final int[] EFFICIENCY_FARM_ENERGY_PERCENT = {20, 40, 60, 80};

    // ---- efficiency (generator): fuel consumption divisor ----
    private static final int[] EFFICIENCY_FUEL_DIVISOR = {2, 4, 6, 8};

    // ---- efficiency (farm): enlarged energy input rate ----
    private static final int[] EFFICIENCY_INPUT_PERCENT = {400, 800, 1200, 1600};

    // ---- efficiency (both machines): enlarged energy cache ----
    private static final int[] EFFICIENCY_CACHE_PERCENT = {100, 200, 300, 400};

    // ---- yield (farm): output multiplier, extra energy, output stack limit ----
    private static final int[] YIELD_MULTIPLIER = {4, 16, 64, 256};
    private static final int[] YIELD_ENERGY_PERCENT = {20, 80, 320, 1280};
    private static final int[] YIELD_STACK_LIMIT = {256, 1024, 5120, 20480};

    public static int speedFarmSeconds(int tier) {
        return SPEED_FARM_SECONDS[idx(tier)];
    }

    public static int speedFarmEnergyPercent(int tier) {
        return SPEED_FARM_ENERGY_PERCENT[idx(tier)];
    }

    public static int speedGenEnergyPercent(int tier) {
        return SPEED_GEN_ENERGY_PERCENT[idx(tier)];
    }

    public static int speedGenFuelPercent(int tier) {
        return SPEED_GEN_FUEL_PERCENT[idx(tier)];
    }

    public static int efficiencyFarmEnergyPercent(int tier) {
        return EFFICIENCY_FARM_ENERGY_PERCENT[idx(tier)];
    }

    public static int efficiencyInputPercent(int tier) {
        return EFFICIENCY_INPUT_PERCENT[idx(tier)];
    }

    public static int efficiencyFuelDivisor(int tier) {
        return EFFICIENCY_FUEL_DIVISOR[idx(tier)];
    }

    public static int efficiencyCachePercent(int tier) {
        return EFFICIENCY_CACHE_PERCENT[idx(tier)];
    }

    public static int yieldMultiplier(int tier) {
        return YIELD_MULTIPLIER[idx(tier)];
    }

    public static int yieldEnergyPercent(int tier) {
        return YIELD_ENERGY_PERCENT[idx(tier)];
    }

    public static int yieldStackLimit(int tier) {
        return YIELD_STACK_LIMIT[idx(tier)];
    }
}
