package com.simpleautofarm.item;

import com.simpleautofarm.block.FarmBlockEntity;
import com.simpleautofarm.block.GeneratorBlockEntity;
import com.simpleautofarm.util.UpgradeEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/**
 * A machine upgrade item with a type (speed / efficiency / yield) and a tier (1..4).
 * The actual effects are computed in the block entities based on the installed upgrades.
 */
public class UpgradeItem extends Item {

    private final UpgradeType type;
    private final int tier;

    public UpgradeItem(UpgradeType type, int tier, Properties properties) {
        super(properties);
        this.type = type;
        this.tier = tier;
    }

    public UpgradeType getType() {
        return type;
    }

    /** 1-based tier (1..4). */
    public int getTier() {
        return tier;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        int tier = getTier();
        switch (type) {
            case SPEED -> {
                header(tooltipComponents, "machine.farm");
                line(tooltipComponents, "farm.seconds", UpgradeEffects.speedFarmSeconds(tier));
                line(tooltipComponents, "farm.energy_up", UpgradeEffects.speedFarmEnergyPercent(tier));
                header(tooltipComponents, "machine.generator");
                line(tooltipComponents, "generator.fe", UpgradeEffects.speedGenEnergyPercent(tier));
                line(tooltipComponents, "generator.energy_out", UpgradeEffects.speedGenEnergyPercent(tier));
                line(tooltipComponents, "generator.fuel_up", UpgradeEffects.speedGenFuelPercent(tier));
            }
            case EFFICIENCY -> {
                header(tooltipComponents, "machine.farm");
                line(tooltipComponents, "farm.energy_down", UpgradeEffects.efficiencyFarmEnergyPercent(tier));
                line(tooltipComponents, "farm.energy_in", UpgradeEffects.efficiencyInputPercent(tier));
                line(tooltipComponents, "energy_cache", UpgradeEffects.efficiencyCachePercent(tier));
                header(tooltipComponents, "machine.generator");
                line(tooltipComponents, "generator.fuel_div", UpgradeEffects.efficiencyFuelDivisor(tier));
                line(tooltipComponents, "energy_cache", UpgradeEffects.efficiencyCachePercent(tier));
            }
            case YIELD -> {
                header(tooltipComponents, "machine.farm");
                line(tooltipComponents, "farm.output", UpgradeEffects.yieldMultiplier(tier));
                line(tooltipComponents, "farm.energy_up", UpgradeEffects.yieldEnergyPercent(tier));
                line(tooltipComponents, "farm.stack_limit", UpgradeEffects.yieldStackLimit(tier));
            }
            case CREATIVE -> tooltipComponents.accept(Component.translatable("tooltip.simpleautofarm.upgrade.creative")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            case MOTION -> {
                // The combined upgrade carries both upgrades' lines. On the farm its energy bonus
                // and penalty cancel out, so no energy line is listed there; on the generator they
                // do not cancel, so both the speed and the efficiency numbers show up.
                header(tooltipComponents, "machine.farm");
                line(tooltipComponents, "farm.seconds", UpgradeEffects.speedFarmSeconds(tier));
                line(tooltipComponents, "farm.energy_in", UpgradeEffects.efficiencyInputPercent(tier));
                line(tooltipComponents, "energy_cache", UpgradeEffects.efficiencyCachePercent(tier));
                header(tooltipComponents, "machine.generator");
                line(tooltipComponents, "generator.fe", UpgradeEffects.speedGenEnergyPercent(tier));
                line(tooltipComponents, "generator.energy_out", UpgradeEffects.speedGenEnergyPercent(tier));
                line(tooltipComponents, "generator.fuel_both",
                        UpgradeEffects.speedGenFuelPercent(tier), UpgradeEffects.efficiencyFuelDivisor(tier));
                line(tooltipComponents, "energy_cache", UpgradeEffects.efficiencyCachePercent(tier));
            }
            case FORTUNE -> {
                header(tooltipComponents, "machine.farm");
                line(tooltipComponents, "farm.fortune_output", UpgradeEffects.fortuneMaxMultiplier(tier));
                line(tooltipComponents, "farm.energy_up", UpgradeEffects.fortuneEnergyPercent(tier));
                line(tooltipComponents, "farm.fortune_stack", UpgradeEffects.fortuneStackFactor(tier));
            }
        }
        // The combined upgrade replaces Speed + Efficiency, so it conflicts with both of them;
        // Speed and Efficiency never conflict with each other.
        if (type == UpgradeType.SPEED || type == UpgradeType.EFFICIENCY) {
            tooltipComponents.accept(Component.translatable("tooltip.simpleautofarm.upgrade.exclusive.other")
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else if (type == UpgradeType.MOTION) {
            tooltipComponents.accept(Component.translatable("tooltip.simpleautofarm.upgrade.exclusive.motion")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    /** Machine header line, e.g. "Farm:". */
    private static void header(Consumer<Component> tooltip, String key) {
        tooltip.accept(Component.translatable("tooltip.simpleautofarm.upgrade." + key).withStyle(ChatFormatting.GRAY));
    }

    /** One effect line; the indent lives in the translation itself, so keep every line short. */
    private static void line(Consumer<Component> tooltip, String key, Object... args) {
        tooltip.accept(Component.translatable("tooltip.simpleautofarm.upgrade." + key, args).withStyle(ChatFormatting.GRAY));
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || !player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (level.isClientSide()) {
            return level.getBlockEntity(pos) instanceof FarmBlockEntity || level.getBlockEntity(pos) instanceof GeneratorBlockEntity
                    ? InteractionResult.SUCCESS
                    : InteractionResult.PASS;
        }
        if (level.getBlockEntity(pos) instanceof FarmBlockEntity farm) {
            return farm.tryInsertUpgrade(player, stack, this) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
        }
        if (level.getBlockEntity(pos) instanceof GeneratorBlockEntity generator) {
            return generator.tryInsertUpgrade(player, stack, this) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
        }
        return InteractionResult.PASS;
    }
}
