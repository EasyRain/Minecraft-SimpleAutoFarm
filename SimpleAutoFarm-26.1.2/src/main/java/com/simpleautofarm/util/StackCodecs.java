package com.simpleautofarm.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Item-stack codecs for the deep-stack output handler.
 *
 * <p>Vanilla's {@link ItemStack#MAP_CODEC} clamps {@code count} to {@code [1, 99]}, so an
 * output stack larger than 99 fails to save. These are equivalent codecs except the count
 * is unbounded ({@link Codec#INT}), the same approach Sophisticated Backpacks uses for its
 * deep stacks.
 */
public final class StackCodecs {

    private StackCodecs() {
    }

    /** Oversized {@link ItemStack} map codec (unbounded count). */
    public static final MapCodec<ItemStack> OVERSIZED_ITEM_STACK_MAP_CODEC = MapCodec.recursive(
            "ItemStack",
            subCodec -> RecordCodecBuilder.mapCodec(
                    instance -> instance.group(
                                    Item.CODEC_WITH_BOUND_COMPONENTS.fieldOf("id").forGetter(ItemStack::typeHolder),
                                    Codec.INT.fieldOf("count").orElse(1).forGetter(ItemStack::getCount),
                                    DataComponentPatch.CODEC
                                            .optionalFieldOf("components", DataComponentPatch.EMPTY)
                                            .forGetter(ItemStack::getComponentsPatch)
                            )
                            .apply(instance, ItemStack::new)
            )
    );

    /** Oversized equivalent of {@link ItemStackWithSlot#CODEC} (unbounded count). */
    public static final Codec<ItemStackWithSlot> OVERSIZED_ITEM_STACK_WITH_SLOT_CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                            ExtraCodecs.UNSIGNED_BYTE.fieldOf("Slot").orElse(0).forGetter(ItemStackWithSlot::slot),
                            OVERSIZED_ITEM_STACK_MAP_CODEC.forGetter(ItemStackWithSlot::stack)
                    )
                    .apply(instance, ItemStackWithSlot::new)
    );
}
