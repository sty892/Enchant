package me.guardian.network;

import me.guardian.GuardianMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record HandshakeC2SPayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<HandshakeC2SPayload> TYPE = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(GuardianMod.MOD_ID, "handshake"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HandshakeC2SPayload> CODEC = StreamCodec.unit(new HandshakeC2SPayload());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
