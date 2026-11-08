package com.simpleautofarm.event;

import com.simpleautofarm.item.EntityCardItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Binds entity cards by right-clicking a mob.
 *
 * <p>This uses the NeoForge interaction event instead of {@code Item#interactLivingEntity}: the
 * event fires at the very start of {@code Player#interactOn}, before the entity's own
 * {@code interact}/{@code mobInteract} or another mod's handler can consume the click, which is what
 * made the item hook unreliable.
 *
 * <p>Only the server binds; the client gets the feedback from the swing and the component sync. A
 * card that is already bound is left alone and the event is not cancelled, so the mob keeps its own
 * interaction.
 */
public final class EntityCardBinding {

    /** Fired on the game bus at HIGH priority, so it runs before most other interaction handlers. */
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof EntityCardItem) || EntityCardItem.isBound(stack)) {
            return;
        }
        if (!(event.getTarget() instanceof LivingEntity target) || target instanceof Player) {
            return;
        }
        Level level = player.level();
        if (!EntityCardItem.bind(stack, target.getType(), level)) {
            return;
        }
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
        player.swing(event.getHand(), true);
        player.containerMenu.broadcastChanges();
    }

    private EntityCardBinding() {
    }
}
