package com.simpleautofarm.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.world.item.ItemStack;

/**
 * Item-stack codecs for the deep-stack output handler.
 *
 * <p>Vanilla's {@link ItemStack#CODEC} clamps {@code count} to {@code [1, 99]}, so an
 * output stack larger than 99 fails to save. This is a byte-for-byte equivalent of
 * {@link ItemStack#CODEC} except the count is unbounded ({@link Codec#INT}), the same
 * approach Sophisticated Backpacks uses for its deep stacks.
 */
public final class StackCodecs {

    private StackCodecs() {
    }

    public static final Codec<ItemStack> OVERSIZED_ITEM_STACK_CODEC = Codec.lazyInitialized(
            () -> RecordCodecBuilder.create(
                    instance -> instance.group(
                                    ItemStack.ITEM_NON_AIR_CODEC.fieldOf("id").forGetter(ItemStack::getItemHolder),
                                    Codec.INT.fieldOf("count").orElse(1).forGetter(ItemStack::getCount),
                                    DataComponentPatch.CODEC
                                            .optionalFieldOf("components", DataComponentPatch.EMPTY)
                                            .forGetter(ItemStack::getComponentsPatch)
                            )
                            .apply(instance, ItemStack::new)
            )
    );
}
