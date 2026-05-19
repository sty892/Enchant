package me.guardian.network;

import me.guardian.GuardianMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public final class CameraPayloads {
    private CameraPayloads() {
    }

    public record OpenEditor(int entityId, String cutsceneId, int index, float duration) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<OpenEditor> TYPE = new CustomPacketPayload.Type<>(id("camera_open_editor"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenEditor> CODEC = StreamCodec.ofMember(OpenEditor::write, OpenEditor::read);

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeInt(entityId);
            buf.writeUtf(cutsceneId, 32767);
            buf.writeInt(index);
            buf.writeFloat(duration);
        }

        private static OpenEditor read(RegistryFriendlyByteBuf buf) {
            return new OpenEditor(
                    buf.readInt(),
                    buf.readUtf(32767),
                    buf.readInt(),
                    buf.readFloat()
            );
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record SaveEditor(int entityId, String cutsceneId, int index, float duration) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<SaveEditor> TYPE = new CustomPacketPayload.Type<>(id("camera_save_editor"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SaveEditor> CODEC = StreamCodec.ofMember(SaveEditor::write, SaveEditor::read);

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeInt(entityId);
            buf.writeUtf(cutsceneId, 32767);
            buf.writeInt(index);
            buf.writeFloat(duration);
        }

        private static SaveEditor read(RegistryFriendlyByteBuf buf) {
            return new SaveEditor(
                    buf.readInt(),
                    buf.readUtf(32767),
                    buf.readInt(),
                    buf.readFloat()
            );
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Delete(int entityId) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Delete> TYPE = new CustomPacketPayload.Type<>(id("camera_delete"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Delete> CODEC = StreamCodec.ofMember(Delete::write, Delete::read);

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeInt(entityId);
        }

        private static Delete read(RegistryFriendlyByteBuf buf) {
            return new Delete(buf.readInt());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(GuardianMod.MOD_ID, path);
    }
}
