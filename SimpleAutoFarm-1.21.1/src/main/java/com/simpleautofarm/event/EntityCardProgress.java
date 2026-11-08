package com.simpleautofarm.event;

import com.simpleautofarm.item.EntityCardItem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Credits entity card progress on player kills: every card in the killer's inventory that is already
 * bound to the killed mob gains one point, up to its requirement. Blank cards are never bound here —
 * binding is done by right-clicking a mob ({@link EntityCardBinding}) — so scanning the whole pack is
 * safe and means the card does not have to be in hand while fighting.
 *
 * <p>Kill credit follows HNN's approach: {@link LivingEntity#getKillCredit()} first, so indirect kills
 * (arrow, fire aspect, a pet finishing the mob, ...) still count (MIT, design reference only); the
 * damage source and its projectile owner are only fallbacks.
 *
 * <p>Kills made by a real player through the mob farm also reach this handler, which is intended: the
 * default config uses the fake player, so that only happens when the server owner opts into
 * real-player kills.
 */
public final class EntityCardProgress {

    /** Fired on the game bus; LOWEST so any other death handler has already run. */
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        LivingEntity killed = event.getEntity();
        ServerPlayer killer = resolveKiller(event, killed);
        if (killer == null) {
            return;
        }
        EntityType<?> killedType = killed.getType();
        boolean changed = false;
        Inventory inventory = killer.getInventory();
        for (ItemStack stack : inventory.items) {
            changed |= EntityCardItem.creditKill(stack, killedType, level);
        }
        for (ItemStack stack : inventory.offhand) {
            changed |= EntityCardItem.creditKill(stack, killedType, level);
        }
        if (changed) {
            killer.containerMenu.broadcastChanges();
        }
    }

    /**
     * The player to credit, in order: the mob's kill credit (covers "hit by a player, finished by fire
     * or a fall"), the damage source's entity, and finally the owner of the projectile that dealt it.
     */
    @Nullable
    private static ServerPlayer resolveKiller(LivingDeathEvent event, LivingEntity killed) {
        LivingEntity credit = killed.getKillCredit();
        if (credit instanceof ServerPlayer player) {
            return player;
        }
        if (event.getSource().getEntity() instanceof ServerPlayer player) {
            return player;
        }
        if (event.getSource().getDirectEntity() instanceof Projectile projectile
                && projectile.getOwner() instanceof ServerPlayer player) {
            return player;
        }
        return null;
    }

    private EntityCardProgress() {
    }
}
