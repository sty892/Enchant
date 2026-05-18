package me.guardian.network;

import me.guardian.GuardianMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class TriggerAreaPayloads {
    private TriggerAreaPayloads() {
    }

    public record Sync(List<String> areas) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Sync> TYPE = new CustomPacketPayload.Type<>(id("trigger_sync"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Sync> CODEC = StreamCodec.ofMember(Sync::write, Sync::read);

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeVarInt(areas.size());
            for (String area : areas) {
                buf.writeUtf(area, 32767);
            }
        }

        private static Sync read(RegistryFriendlyByteBuf buf) {
            int count = buf.readVarInt();
            List<String> areas = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                areas.add(buf.readUtf(32767));
            }
            return new Sync(areas);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record SetPoint(BlockPos pos, boolean second) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<SetPoint> TYPE = new CustomPacketPayload.Type<>(id("trigger_set_point"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SetPoint> CODEC = StreamCodec.ofMember(SetPoint::write, SetPoint::read);

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeBoolean(second);
        }

        private static SetPoint read(RegistryFriendlyByteBuf buf) {
            return new SetPoint(buf.readBlockPos(), buf.readBoolean());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record OpenEditor(UUID areaId) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<OpenEditor> TYPE = new CustomPacketPayload.Type<>(id("trigger_open_editor"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenEditor> CODEC = StreamCodec.ofMember(OpenEditor::write, OpenEditor::read);

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeUUID(areaId);
        }

        private static OpenEditor read(RegistryFriendlyByteBuf buf) {
            return new OpenEditor(buf.readUUID());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record EditorData(String area) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<EditorData> TYPE = new CustomPacketPayload.Type<>(id("trigger_editor_data"));
        public static final StreamCodec<RegistryFriendlyByteBuf, EditorData> CODEC = StreamCodec.ofMember(EditorData::write, EditorData::read);

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeUtf(area, 32767);
        }

        private static EditorData read(RegistryFriendlyByteBuf buf) {
            return new EditorData(buf.readUtf(32767));
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record SaveEditor(String area) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<SaveEditor> TYPE = new CustomPacketPayload.Type<>(id("trigger_save_editor"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SaveEditor> CODEC = StreamCodec.ofMember(SaveEditor::write, SaveEditor::read);

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeUtf(area, 32767);
        }

        private static SaveEditor read(RegistryFriendlyByteBuf buf) {
            return new SaveEditor(buf.readUtf(32767));
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Delete(UUID areaId) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Delete> TYPE = new CustomPacketPayload.Type<>(id("trigger_delete"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Delete> CODEC = StreamCodec.ofMember(Delete::write, Delete::read);

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeUUID(areaId);
        }

        private static Delete read(RegistryFriendlyByteBuf buf) {
            return new Delete(buf.readUUID());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Reset(UUID areaId) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Reset> TYPE = new CustomPacketPayload.Type<>(id("trigger_reset"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Reset> CODEC = StreamCodec.ofMember(Reset::write, Reset::read);

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeUUID(areaId);
        }

        private static Reset read(RegistryFriendlyByteBuf buf) {
            return new Reset(buf.readUUID());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record ToggleGuard(UUID areaId) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<ToggleGuard> TYPE = new CustomPacketPayload.Type<>(id("trigger_toggle_guard"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ToggleGuard> CODEC = StreamCodec.ofMember(ToggleGuard::write, ToggleGuard::read);

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeUUID(areaId);
        }

        private static ToggleGuard read(RegistryFriendlyByteBuf buf) {
            return new ToggleGuard(buf.readUUID());
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
