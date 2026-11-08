package com.simpleautofarm.event;

import com.simpleautofarm.item.EntityCardItem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * Credits entity card progress on player kills: a card in the main hand or offhand that is already
 * bound to the killed mob gains one point, up to its requirement. Blank cards are never bound here —
 * binding is done by right-clicking a mob ({@link EntityCardBinding}) — and only the two held stacks
 * are checked, so carrying a stack of blanks while fighting does nothing.
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
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }
        EntityType<?> killedType = event.getEntity().getType();
        boolean changed = EntityCardItem.creditKill(player.getMainHandItem(), killedType, level);
        changed |= EntityCardItem.creditKill(player.getOffhandItem(), killedType, level);
        if (changed) {
            player.containerMenu.broadcastChanges();
        }
    }

    private EntityCardProgress() {
    }
}
