package com.simpleautofarm.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import com.simpleautofarm.SimpleAutoFarm;
import com.simpleautofarm.client.FluidFarmDisplayCache;

import java.util.Optional;

/**
 * Tells the client which fluid the fluid farm at a position shows, without sending any block entity data.
 *
 * <p>The payload carries only the position and the fluid id (empty means "shows nothing"), so it can never
 * overwrite the client's item handlers - which is what a {@code ClientboundBlockEntityDataPacket} did when
 * its {@code saveAdditional} included the machine's inventory. It is sent only when the displayed fluid
 * actually changes, to the players tracking the chunk.
 */
public record FluidFarmDisplayPayload(BlockPos pos, Optional<Identifier> fluid) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<FluidFarmDisplayPayload> TYPE =
            new CustomPacketPayload.Type<>(SimpleAutoFarm.prefix("fluid_farm_display"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FluidFarmDisplayPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, FluidFarmDisplayPayload::pos,
                    ByteBufCodecs.optional(Identifier.STREAM_CODEC), FluidFarmDisplayPayload::fluid,
                    FluidFarmDisplayPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Client handler: only updates the display cache; it never touches the block entity. */
    public static void handle(FluidFarmDisplayPayload payload, IPayloadContext context) {
        if (!FMLEnvironment.getDist().isClient()) {
            return;
        }
        context.enqueueWork(() -> FluidFarmDisplayCache.put(payload.pos(), payload.fluid().orElse(null)));
    }
}
