package com.simpleautofarm.item;

import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;

/**
 * A random material card: a sample that does not name one plant or ore but draws a fresh one of its
 * category for every batch, so a single card keeps a machine producing a little of everything.
 *
 * <p>Like the random entity cards it is never consumed and always glows. The card only fits the machine
 * that owns its category: the ore card the ore farm, the crop/tree/plant cards the crop farm.
 */
public class MaterialCardItem extends Item {

    /** What a card draws from. */
    public enum Kind {
        ORE, CROP, TREE, PLANT
    }

    private final Kind kind;

    public MaterialCardItem(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    public Kind getKind() {
        return kind;
    }

    /** Always glowing, same as the random entity cards. */
    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.simpleautofarm.material_card."
                + kind.name().toLowerCase(Locale.ROOT)).withStyle(ChatFormatting.GRAY));
    }

    /** The kind of a material card, or {@code null} for every other item. */
    @Nullable
    public static Kind kindOf(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof MaterialCardItem card)) {
            return null;
        }
        return card.getKind();
    }
}
