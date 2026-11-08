package com.simpleautofarm.event;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects the drops of the mob farm's synthetic deaths.
 *
 * <p>The mob farm kills a throwaway entity that never enters the world. NeoForge's
 * {@code LivingEntity#dropAllDeathLoot} spawns the finished drop list into the level unless a
 * {@link LivingDropsEvent} handler cancels it, so this handler cancels the event for the machine's own
 * marked entities and keeps their items instead — no item entity is ever added to the world and no
 * drop is lost.
 *
 * <p>The collection window is the synchronous {@code die()} call on the server thread, so a single
 * static sink is enough; the marker on the entity guards against unrelated deaths slipping in.
 */
public final class SyntheticDeathDrops {

    /** Entities the machine is killing right now (identity set: no NBT tag needed). */
    private static final java.util.Set<LivingEntity> SYNTHETIC =
            java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    @Nullable
    private static List<ItemStack> sink;

    /** Opens a collection window; call right before the synthetic death. */
    public static void begin() {
        SYNTHETIC.clear();
        sink = new ArrayList<>();
    }

    /** Closes the window and returns everything collected during it. */
    public static List<ItemStack> end() {
        List<ItemStack> collected = sink == null ? List.of() : sink;
        sink = null;
        SYNTHETIC.clear();
        return collected;
    }

    /** Marks the entity so only the machine's own deaths are captured. */
    public static void mark(LivingEntity entity) {
        SYNTHETIC.add(entity);
    }

    /** Fired on the game bus; cancels the level spawn of a synthetic death and keeps its drops. */
    public static void onLivingDrops(LivingDropsEvent event) {
        List<ItemStack> target = sink;
        if (target == null || !SYNTHETIC.contains(event.getEntity())) {
            return;
        }
        event.setCanceled(true);
        for (ItemEntity drop : event.getDrops()) {
            ItemStack stack = drop.getItem();
            if (!stack.isEmpty()) {
                target.add(stack.copy());
            }
        }
    }

    private SyntheticDeathDrops() {
    }
}
