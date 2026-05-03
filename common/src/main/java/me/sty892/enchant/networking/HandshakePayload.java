package me.sty892.enchant.networking;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

public record HandshakePayload() implements CustomPayload {
    public static final Id<HandshakePayload> ID = new Id<>(NetworkingConstants.HANDSHAKE_ID);
    public static final PacketCodec<PacketByteBuf, HandshakePayload> CODEC = PacketCodec.unit(new HandshakePayload());

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public static void register() {
        PayloadTypeRegistry.playC2S().register(ID, CODEC);
    }
}
