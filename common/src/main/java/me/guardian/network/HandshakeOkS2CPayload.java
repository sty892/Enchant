package me.guardian.network;

import me.guardian.GuardianMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record HandshakeOkS2CPayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<HandshakeOkS2CPayload> TYPE = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(GuardianMod.MOD_ID, "handshake_ok"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HandshakeOkS2CPayload> CODEC = StreamCodec.unit(new HandshakeOkS2CPayload());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
