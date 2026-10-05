package com.simpleautofarm.item;

import com.simpleautofarm.pb.PbCompat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

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
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        if (!PbCompat.isLoaded()) {
            tooltip.add(Component.translatable("tooltip.simpleautofarm.beehive.requires_pb").withStyle(ChatFormatting.RED));
        }
    }
}
