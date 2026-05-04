package me.guardian.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public final class GuardianNetworking {
    private static boolean payloadTypesRegistered;

    private GuardianNetworking() {
    }

    public static synchronized void registerPayloadTypes() {
        if (payloadTypesRegistered) {
            return;
        }

        PayloadTypeRegistry.playC2S().register(HandshakeC2SPayload.TYPE, HandshakeC2SPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(HandshakeOkS2CPayload.TYPE, HandshakeOkS2CPayload.CODEC);
        payloadTypesRegistered = true;
    }
}
