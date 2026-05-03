package me.sty892.enchant.networking;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

public record HandshakeOkPayload() implements CustomPayload {
    public static final Id<HandshakeOkPayload> ID = new Id<>(NetworkingConstants.HANDSHAKE_OK_ID);
    public static final PacketCodec<PacketByteBuf, HandshakeOkPayload> CODEC = PacketCodec.unit(new HandshakeOkPayload());

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public static void register() {
        PayloadTypeRegistry.playS2C().register(ID, CODEC);
    }
}
