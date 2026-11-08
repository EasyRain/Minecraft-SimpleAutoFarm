package com.simpleautofarm.event;

import com.simpleautofarm.SimpleAutoFarm;
import com.simpleautofarm.item.EntityCardItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * Credits entity card kills: whenever a player kills a mob, every card in that player's inventory
 * (main inventory and offhand) that is bound to the same mob type gains one kill, until it activates.
 *
 * <p>Multiple cards of the same type all advance, and one kill only ever adds one per card. Kills made
 * by a real player through the mob farm also reach this handler, which is intended: the default config
 * uses the fake player, so that only happens when the server owner opts into real-player kills.
 */
public final class EntityCardProgress {

    /** Fired on the game bus; LOWEST so any other death handler has already run. */
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel)) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }
        EntityType<?> killedType = event.getEntity().getType();
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(killedType);
        if (id == null) {
            return;
        }
        boolean changed = false;
        Inventory inventory = player.getInventory();
        for (ItemStack stack : inventory.items) {
            changed |= credit(stack, id);
        }
        for (ItemStack stack : inventory.offhand) {
            changed |= credit(stack, id);
        }
        if (changed) {
            player.containerMenu.broadcastChanges();
        }
    }

    /** Adds one kill to the card when it is bound to {@code killedId} and still unfinished. */
    private static boolean credit(ItemStack stack, ResourceLocation killedId) {
        if (!(stack.getItem() instanceof EntityCardItem)) {
            return false;
        }
        ResourceLocation bound = EntityCardItem.getBoundTypeId(stack);
        if (bound == null || !bound.equals(killedId)) {
            return false;
        }
        int required = EntityCardItem.getRequired(stack);
        int kills = EntityCardItem.getKills(stack);
        if (required <= 0 || kills >= required) {
            return false;
        }
        stack.set(SimpleAutoFarm.CARD_KILLS.get(), kills + 1);
        return true;
    }

    private EntityCardProgress() {
    }
}
