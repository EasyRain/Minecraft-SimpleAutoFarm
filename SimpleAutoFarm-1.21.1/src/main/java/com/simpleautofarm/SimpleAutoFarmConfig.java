package com.simpleautofarm;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Common configuration (config/simpleautofarm-common.toml).
 *
 * <p>Only machine behaviour that may need to be turned off on a busy server lives here; every
 * default keeps the mod working exactly as documented.
 */
public final class SimpleAutoFarmConfig {

    public static final ModConfigSpec SPEC;

    /**
     * Whether the mob farm simulates a full mob death instead of only rolling the loot table.
     *
     * <p>A death also runs global loot modifiers, {@code dropCustomDeathLoot} (other mods' extra drop
     * tables, e.g. Apotheosis affix gear) and {@code LivingDropsEvent}, which is how modded drops
     * appear. Turning it off falls back to the plain vanilla loot-table roll.
     */
    public static final ModConfigSpec.BooleanValue MOB_FARM_SIMULATE_DEATH;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("Simple Auto Farm machine settings").push("machines");
        MOB_FARM_SIMULATE_DEATH = builder
                .comment("Mob farm: simulate a real mob death so modded death drops (affix gear, gems, ...) appear.",
                        "Turning this off only rolls the vanilla loot table.",
                        "Default: true")
                .define("mobFarmSimulateDeath", true);
        builder.pop();
        SPEC = builder.build();
    }

    private SimpleAutoFarmConfig() {
    }
}
