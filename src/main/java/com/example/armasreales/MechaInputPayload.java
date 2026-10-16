package com.example.armasreales;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Cliente -> servidor: teclas del mecha. action 0 = espacio (salto/jetpack), 1 = abrir inventario (tecla U), 2 = Ctrl + W (propulsores). */
public record MechaInputPayload(int action, boolean value) implements CustomPacketPayload {
    public static final int JUMP = 0;
    public static final int OPEN_INVENTORY = 1;
    public static final int SPRINT = 2;

    public static final CustomPacketPayload.Type<MechaInputPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(ArmasRealesMod.MOD_ID, "mecha_input"));

    public static final StreamCodec<ByteBuf, MechaInputPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, MechaInputPayload::action,
            ByteBufCodecs.BOOL, MechaInputPayload::value,
            MechaInputPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(MechaInputPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player && player.getVehicle() instanceof MechaEntity mecha) {
                mecha.handleInput(player, payload.action(), payload.value());
            }
        });
    }
}
