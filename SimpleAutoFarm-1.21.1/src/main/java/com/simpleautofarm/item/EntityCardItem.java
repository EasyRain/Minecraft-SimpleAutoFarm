package com.simpleautofarm.item;

import com.simpleautofarm.SimpleAutoFarm;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Entity Card: a survival-obtainable mob-farm sample.
 *
 * <p>Right-clicking a mob with a blank card binds it to that mob's type and stores the kill
 * requirement (inspired by Hostile Neural Networks, MIT — design reference only, no code copied).
 * Every player kill of that mob while the card is held in a hand then adds one point, and once the
 * requirement is met the card activates and the mob farm accepts it as a sample.
 *
 * <p>All state lives in data components ({@code entity_card_entity}, {@code entity_card_kills},
 * {@code entity_card_required}), so it survives saving and item form changes. A bound card cannot be
 * re-bound by right-clicking (progress must never be lost by accident); crafting it with paper wipes
 * it back to a blank card instead.
 */
public class EntityCardItem extends Item {

    /** Hard cap of the kill requirement, whatever the mob's health is. */
    public static final int MAX_REQUIRED = 32;

    /** Kills for a full-health "reference" mob: 600 / max health, clamped to {@link #MAX_REQUIRED}. */
    private static final double KILLS_BASE = 600.0;

    /** Health assumed when neither the entity type nor an instance can report one. */
    private static final double FALLBACK_MAX_HEALTH = 20.0;

    public EntityCardItem(Properties properties) {
        super(properties);
    }

    /**
     * Binds a blank card to a mob type and stores its kill requirement, starting progress at zero.
     *
     * @return {@code true} when the card was bound; {@code false} for an already bound card, a
     *         non-card stack, or an entity type without a registry id
     */
    public static boolean bind(ItemStack stack, EntityType<?> type, Level level) {
        if (!(stack.getItem() instanceof EntityCardItem) || isBound(stack)) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        if (id == null) {
            return false;
        }
        stack.set(SimpleAutoFarm.CARD_ENTITY.get(), Optional.of(id));
        stack.set(SimpleAutoFarm.CARD_REQUIRED.get(), requiredKills(type, level));
        stack.set(SimpleAutoFarm.CARD_KILLS.get(), 0);
        return true;
    }

    /**
     * Applies one player kill to an <b>already bound</b> card: one more point for the matching mob,
     * never past the requirement. Blank cards are never bound here — that is what right-clicking is
     * for — so carrying a stack of blanks while fighting does nothing.
     *
     * @return {@code true} when the stack changed and has to be synced to the client
     */
    public static boolean creditKill(ItemStack stack, EntityType<?> type, Level level) {
        if (!(stack.getItem() instanceof EntityCardItem)) {
            return false;
        }
        ResourceLocation bound = getBoundTypeId(stack);
        if (bound == null) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        if (id == null || !bound.equals(id)) {
            return false;
        }
        int required = getRequired(stack);
        int kills = getKills(stack);
        if (required <= 0 || kills >= required) {
            return false;
        }
        stack.set(SimpleAutoFarm.CARD_KILLS.get(), kills + 1);
        return true;
    }

    /** A finished card glows, so the machine-readable state is visible at a glance. */
    @Override
    public boolean isFoil(ItemStack stack) {
        return isActivated(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        ResourceLocation bound = getBoundTypeId(stack);
        if (bound == null) {
            tooltip.add(Component.translatable("tooltip.simpleautofarm.entity_card.unbound")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.add(Component.translatable("tooltip.simpleautofarm.entity_card.bound", entityName(bound))
                .withStyle(ChatFormatting.GRAY));
        int required = getRequired(stack);
        int kills = getKills(stack);
        if (kills >= required) {
            tooltip.add(Component.translatable("tooltip.simpleautofarm.entity_card.active")
                    .withStyle(ChatFormatting.GREEN));
        } else {
            tooltip.add(Component.translatable("tooltip.simpleautofarm.entity_card.progress", kills, required)
                    .withStyle(ChatFormatting.YELLOW));
        }
        tooltip.add(Component.translatable("tooltip.simpleautofarm.entity_card.clear")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    /** Vanilla entity name key: {@code entity.<namespace>.<path>} (works for modded entities too). */
    private static Component entityName(ResourceLocation id) {
        return Component.translatable("entity." + id.getNamespace() + "." + id.getPath());
    }

    // ---------- requirement ----------

    /** Kills the card needs for this mob: 600 / base health, clamped to 1..{@link #MAX_REQUIRED}. */
    public static int requiredKills(EntityType<?> type, Level level) {
        double health = baseMaxHealth(type, level);
        int required = (int) Math.ceil(KILLS_BASE / Math.max(1.0D, health));
        return Math.max(1, Math.min(MAX_REQUIRED, required));
    }

    /**
     * Base health of the entity type: the type's own default attributes first (an instance could have
     * been modified by other mods), then a freshly created instance, then a 20 HP assumption.
     */
    @SuppressWarnings("unchecked")
    private static double baseMaxHealth(EntityType<?> type, Level level) {
        try {
            if (DefaultAttributes.hasSupplier(type)) {
                AttributeSupplier supplier =
                        DefaultAttributes.getSupplier((EntityType<? extends LivingEntity>) type);
                if (supplier.hasAttribute(Attributes.MAX_HEALTH)) {
                    return supplier.getValue(Attributes.MAX_HEALTH);
                }
            }
        } catch (RuntimeException ignored) {
            // Fall through to the instance read.
        }
        try {
            Entity entity = type.create(level);
            if (entity instanceof LivingEntity living) {
                return living.getMaxHealth();
            }
        } catch (RuntimeException ignored) {
            // Fall through to the default.
        }
        return FALLBACK_MAX_HEALTH;
    }

    // ---------- state access ----------

    @Nullable
    public static ResourceLocation getBoundTypeId(ItemStack stack) {
        Optional<ResourceLocation> bound = stack.getOrDefault(SimpleAutoFarm.CARD_ENTITY.get(), Optional.empty());
        return bound.orElse(null);
    }

    public static int getKills(ItemStack stack) {
        return stack.getOrDefault(SimpleAutoFarm.CARD_KILLS.get(), 0);
    }

    public static int getRequired(ItemStack stack) {
        return stack.getOrDefault(SimpleAutoFarm.CARD_REQUIRED.get(), 0);
    }

    public static boolean isBound(ItemStack stack) {
        return getBoundTypeId(stack) != null && getRequired(stack) > 0;
    }

    public static boolean isActivated(ItemStack stack) {
        return isBound(stack) && getKills(stack) >= getRequired(stack);
    }

    @Nullable
    public static EntityType<?> getBoundType(ItemStack stack) {
        ResourceLocation id = getBoundTypeId(stack);
        return id == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
    }

    /** Mob farm hook: only a finished card counts as a sample. */
    @Nullable
    public static EntityType<?> getActiveSampleType(ItemStack stack) {
        return isActivated(stack) ? getBoundType(stack) : null;
    }
}
