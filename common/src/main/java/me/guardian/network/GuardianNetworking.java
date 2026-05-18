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
        PayloadTypeRegistry.playS2C().register(TriggerAreaPayloads.Sync.TYPE, TriggerAreaPayloads.Sync.CODEC);
        PayloadTypeRegistry.playC2S().register(TriggerAreaPayloads.SetPoint.TYPE, TriggerAreaPayloads.SetPoint.CODEC);
        PayloadTypeRegistry.playC2S().register(TriggerAreaPayloads.OpenEditor.TYPE, TriggerAreaPayloads.OpenEditor.CODEC);
        PayloadTypeRegistry.playS2C().register(TriggerAreaPayloads.EditorData.TYPE, TriggerAreaPayloads.EditorData.CODEC);
        PayloadTypeRegistry.playC2S().register(TriggerAreaPayloads.SaveEditor.TYPE, TriggerAreaPayloads.SaveEditor.CODEC);
        PayloadTypeRegistry.playC2S().register(TriggerAreaPayloads.Delete.TYPE, TriggerAreaPayloads.Delete.CODEC);
        PayloadTypeRegistry.playC2S().register(TriggerAreaPayloads.Reset.TYPE, TriggerAreaPayloads.Reset.CODEC);
        PayloadTypeRegistry.playC2S().register(TriggerAreaPayloads.ToggleGuard.TYPE, TriggerAreaPayloads.ToggleGuard.CODEC);
        payloadTypesRegistered = true;
    }
}
