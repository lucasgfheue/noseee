package com.example.armasreales;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Cliente -> servidor: asiento elegido en el menú del tanque. */
public record SeatPayload(int tankId, int seat) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SeatPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(ArmasRealesMod.MOD_ID, "asiento_tanque"));

    public static final StreamCodec<ByteBuf, SeatPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SeatPayload::tankId,
            ByteBufCodecs.VAR_INT, SeatPayload::seat,
            SeatPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SeatPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && player.level().getEntity(payload.tankId()) instanceof TankEntity tank) {
                tank.requestSeat(player, payload.seat());
            }
        });
    }
}
