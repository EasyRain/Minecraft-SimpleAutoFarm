package com.simpleautofarm.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

import java.util.function.Consumer;

/** The water source block's item, with a description tooltip (in the spirit of the sink from Cooking for Blockheads). */
public class WaterSourceItem extends BlockItem {

    public WaterSourceItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.accept(Component.translatable("tooltip.simpleautofarm.water_source.1").withStyle(ChatFormatting.GRAY));
        tooltipComponents.accept(Component.translatable("tooltip.simpleautofarm.water_source.2").withStyle(ChatFormatting.GRAY));
    }
}
