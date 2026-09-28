package com.simpleautofarm.util;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Ore recognition for the ore farm, following the same convention as useless_mod's ore generator:
 * an item counts as an ore when it lives in the {@code c:ores} or {@code c:raw_materials} item tags
 * (NeoForge common tags) — no name heuristics, no recipes.
 */
public final class OreHelper {

    private static final TagKey<Item> ORES =
            TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "ores"));

    private static final TagKey<Item> RAW_MATERIALS =
            TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "raw_materials"));

    private OreHelper() {
    }

    /** True when the stack is an ore block item or a raw ore material. */
    public static boolean isOre(ItemStack stack) {
        return !stack.isEmpty() && (stack.is(ORES) || stack.is(RAW_MATERIALS));
    }
}
