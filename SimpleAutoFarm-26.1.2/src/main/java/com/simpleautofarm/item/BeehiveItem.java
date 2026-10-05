package com.simpleautofarm.item;

import com.simpleautofarm.pb.PbCompat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

import java.util.function.Consumer;

/**
 * The beehive item. It carries a single tooltip line: the "Productive Bees missing" warning, which
 * is the only thing the player cannot see for themselves — how the machine works is explained by
 * the screen itself (the food page suffix, the slot hints and the JEI recipes).
 */
public class BeehiveItem extends BlockItem {

    public BeehiveItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, display, tooltipComponents, tooltipFlag);
        if (!PbCompat.isLoaded()) {
            tooltipComponents.accept(Component.translatable("tooltip.simpleautofarm.beehive.requires_pb").withStyle(ChatFormatting.RED));
        }
    }
}
