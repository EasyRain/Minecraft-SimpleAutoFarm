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
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;

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
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        int tier = getTier();
        switch (type) {
            case SPEED -> {
                tooltipComponents.add(Component.translatable("tooltip.simpleautofarm.upgrade.speed.farm",
                        UpgradeEffects.speedFarmSeconds(tier),
                        UpgradeEffects.speedFarmEnergyPercent(tier)).withStyle(ChatFormatting.GRAY));
                tooltipComponents.add(Component.translatable("tooltip.simpleautofarm.upgrade.speed.generator",
                        UpgradeEffects.speedGenEnergyPercent(tier),
                        UpgradeEffects.speedGenEnergyPercent(tier),
                        UpgradeEffects.speedGenFuelPercent(tier)).withStyle(ChatFormatting.GRAY));
            }
            case EFFICIENCY -> {
                tooltipComponents.add(Component.translatable("tooltip.simpleautofarm.upgrade.efficiency.farm",
                        UpgradeEffects.efficiencyFarmEnergyPercent(tier),
                        UpgradeEffects.efficiencyInputPercent(tier),
                        UpgradeEffects.efficiencyCachePercent(tier)).withStyle(ChatFormatting.GRAY));
                tooltipComponents.add(Component.translatable("tooltip.simpleautofarm.upgrade.efficiency.generator",
                        UpgradeEffects.efficiencyFuelDivisor(tier),
                        UpgradeEffects.efficiencyCachePercent(tier)).withStyle(ChatFormatting.GRAY));
            }
            case YIELD -> {
                tooltipComponents.add(Component.translatable("tooltip.simpleautofarm.upgrade.yield.farm",
                        UpgradeEffects.yieldMultiplier(tier),
                        UpgradeEffects.yieldEnergyPercent(tier),
                        UpgradeEffects.yieldStackLimit(tier)).withStyle(ChatFormatting.GRAY));
            }
            case CREATIVE -> {
                tooltipComponents.add(Component.translatable("tooltip.simpleautofarm.upgrade.creative").withStyle(ChatFormatting.LIGHT_PURPLE));
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
