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
                tooltipComponents.accept(Component.translatable("tooltip.simpleautofarm.upgrade.speed.farm",
                        UpgradeEffects.speedFarmSeconds(tier),
                        UpgradeEffects.speedFarmEnergyPercent(tier)).withStyle(ChatFormatting.GRAY));
                tooltipComponents.accept(Component.translatable("tooltip.simpleautofarm.upgrade.speed.generator",
                        UpgradeEffects.speedGenEnergyPercent(tier),
                        UpgradeEffects.speedGenEnergyPercent(tier),
                        UpgradeEffects.speedGenFuelPercent(tier)).withStyle(ChatFormatting.GRAY));
            }
            case EFFICIENCY -> {
                tooltipComponents.accept(Component.translatable("tooltip.simpleautofarm.upgrade.efficiency.farm",
                        UpgradeEffects.efficiencyFarmEnergyPercent(tier),
                        UpgradeEffects.efficiencyInputPercent(tier),
                        UpgradeEffects.efficiencyCachePercent(tier)).withStyle(ChatFormatting.GRAY));
                tooltipComponents.accept(Component.translatable("tooltip.simpleautofarm.upgrade.efficiency.generator",
                        UpgradeEffects.efficiencyFuelDivisor(tier),
                        UpgradeEffects.efficiencyCachePercent(tier)).withStyle(ChatFormatting.GRAY));
            }
            case YIELD -> {
                tooltipComponents.accept(Component.translatable("tooltip.simpleautofarm.upgrade.yield.farm",
                        UpgradeEffects.yieldMultiplier(tier),
                        UpgradeEffects.yieldEnergyPercent(tier),
                        UpgradeEffects.yieldStackLimit(tier)).withStyle(ChatFormatting.GRAY));
            }
            case CREATIVE -> {
                tooltipComponents.accept(Component.translatable("tooltip.simpleautofarm.upgrade.creative").withStyle(ChatFormatting.LIGHT_PURPLE));
            }
            case MOTION -> {
                // The combined upgrade carries both lines: the farm numbers are the efficiency side
                // (its energy bonus and penalty cancel out, so energy is not listed), the generator
                // numbers list the speed side as well because there they do not cancel.
                tooltipComponents.accept(Component.translatable("tooltip.simpleautofarm.upgrade.motion.farm",
                        UpgradeEffects.speedFarmSeconds(tier),
                        UpgradeEffects.efficiencyInputPercent(tier),
                        UpgradeEffects.efficiencyCachePercent(tier)).withStyle(ChatFormatting.GRAY));
                tooltipComponents.accept(Component.translatable("tooltip.simpleautofarm.upgrade.motion.generator",
                        UpgradeEffects.speedGenEnergyPercent(tier),
                        UpgradeEffects.speedGenEnergyPercent(tier),
                        UpgradeEffects.speedGenFuelPercent(tier),
                        UpgradeEffects.efficiencyFuelDivisor(tier),
                        UpgradeEffects.efficiencyCachePercent(tier)).withStyle(ChatFormatting.GRAY));
            }
            case FORTUNE -> {
                tooltipComponents.accept(Component.translatable("tooltip.simpleautofarm.upgrade.fortune.farm",
                        UpgradeEffects.fortuneMaxMultiplier(tier),
                        UpgradeEffects.fortuneEnergyPercent(tier),
                        UpgradeEffects.fortuneStackFactor(tier)).withStyle(ChatFormatting.GRAY));
            }
        }
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
