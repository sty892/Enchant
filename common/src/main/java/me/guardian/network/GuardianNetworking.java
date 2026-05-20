package me.guardian.network;

import me.guardian.GuardianMod;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;

public final class GuardianNetworking {
    private static boolean payloadTypesRegistered;

    private GuardianNetworking() {
    }

    public static synchronized void registerPayloadTypes() {
        if (payloadTypesRegistered) {
            return;
        }

        registerC2S(HandshakeC2SPayload.TYPE, HandshakeC2SPayload.CODEC);
        registerS2C(HandshakeOkS2CPayload.TYPE, HandshakeOkS2CPayload.CODEC);
        registerS2C(TriggerAreaPayloads.Sync.TYPE, TriggerAreaPayloads.Sync.CODEC);
        registerC2S(TriggerAreaPayloads.SetPoint.TYPE, TriggerAreaPayloads.SetPoint.CODEC);
        registerC2S(TriggerAreaPayloads.OpenEditor.TYPE, TriggerAreaPayloads.OpenEditor.CODEC);
        registerS2C(TriggerAreaPayloads.EditorData.TYPE, TriggerAreaPayloads.EditorData.CODEC);
        registerC2S(TriggerAreaPayloads.SaveEditor.TYPE, TriggerAreaPayloads.SaveEditor.CODEC);
        registerC2S(TriggerAreaPayloads.Delete.TYPE, TriggerAreaPayloads.Delete.CODEC);
        registerC2S(TriggerAreaPayloads.Reset.TYPE, TriggerAreaPayloads.Reset.CODEC);
        registerC2S(TriggerAreaPayloads.ToggleGuard.TYPE, TriggerAreaPayloads.ToggleGuard.CODEC);

        registerS2C(CameraPayloads.OpenEditor.TYPE, CameraPayloads.OpenEditor.CODEC);
        registerC2S(CameraPayloads.SaveEditor.TYPE, CameraPayloads.SaveEditor.CODEC);
        registerC2S(CameraPayloads.Delete.TYPE, CameraPayloads.Delete.CODEC);

        payloadTypesRegistered = true;
    }

    private static <T extends CustomPacketPayload> void registerC2S(
            CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec
    ) {
        try {
            PayloadTypeRegistry.playC2S().register(type, codec);
        } catch (IllegalArgumentException e) {
            GuardianMod.LOGGER.warn("Guardian payload {} was already registered for C2S; keeping existing codec.", type.id());
        }
    }

    private static <T extends CustomPacketPayload> void registerS2C(
            CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec
    ) {
        try {
            PayloadTypeRegistry.playS2C().register(type, codec);
        } catch (IllegalArgumentException e) {
            GuardianMod.LOGGER.warn("Guardian payload {} was already registered for S2C; keeping existing codec.", type.id());
        }
    }
}
