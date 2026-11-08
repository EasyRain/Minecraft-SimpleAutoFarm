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

    /**
     * Whether the mob farm may fall back to a fake player when no real player is available.
     *
     * <p>Some packs disable fake players (FTB Chunks and similar); turning this off keeps the machine
     * working — it then rolls the plain loot tables, so basic drops still appear but
     * "killed by a player" conditions and the player-scaled modded drops do not.
     */
    public static final ModConfigSpec.BooleanValue MOB_FARM_ALLOW_FAKE_PLAYER;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("Simple Auto Farm machine settings").push("machines");
        MOB_FARM_SIMULATE_DEATH = builder
                .comment("Mob farm: simulate a real mob death so modded death drops (affix gear, gems, ...) appear.",
                        "Turning this off only rolls the vanilla loot table.",
                        "Default: true")
                .define("mobFarmSimulateDeath", true);
        MOB_FARM_ALLOW_FAKE_PLAYER = builder
                .comment("Mob farm: allow the fake player as killer when no real player is online.",
                        "Turn this off in packs that block fake players (FTB Chunks, ...).",
                        "The machine then rolls plain loot tables: normal drops still appear, but drops",
                        "that need a player kill (and the player-scaled modded loot) do not.",
                        "Default: true")
                .define("mobFarmAllowFakePlayer", true);
        builder.pop();
        SPEC = builder.build();
    }

    private SimpleAutoFarmConfig() {
    }
}
