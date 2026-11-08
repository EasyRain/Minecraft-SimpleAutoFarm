package com.simpleautofarm.client;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

/**
 * Client-side lookup of "which fluid does the machine at this position show".
 *
 * <p>Filled only by {@code FluidFarmDisplayPayload}; the block entity is never touched, so the GUI's
 * slots keep working. Bounded to the last 256 entries, which is far more than the handful of machines a
 * player ever sees at once.
 */
@OnlyIn(Dist.CLIENT)
public final class FluidFarmDisplayCache {

    private static final int MAX_ENTRIES = 256;

    private static final Map<BlockPos, Identifier> FLUIDS = new LinkedHashMap<>(16, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<BlockPos, Identifier> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    private FluidFarmDisplayCache() {
    }

    /** Stores the fluid shown at {@code pos}; {@code null} clears the entry. */
    public static void put(BlockPos pos, @Nullable Identifier fluid) {
        if (fluid == null) {
            FLUIDS.remove(pos);
        } else {
            FLUIDS.put(pos.immutable(), fluid);
        }
    }

    /** The fluid id last sent for {@code pos}, or {@code null} when nothing was sent yet. */
    @Nullable
    public static Identifier get(BlockPos pos) {
        return FLUIDS.get(pos);
    }

    /** Drops everything, used when leaving a world. */
    public static void clear() {
        FLUIDS.clear();
    }
}
