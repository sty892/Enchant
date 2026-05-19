package me.guardian.server.state;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GuardianWorldState extends SavedData {
    public static final String STATE_NAME = "guardian_mod_world_state";

    public static final class CutsceneSession {
        public final String playerUuid;
        public final String cutsceneId;
        public int cameraIndex; // index in the camera sequence
        public int ticksLeft;
        public final String originalGameMode;
        public final double originalX;
        public final double originalY;
        public final double originalZ;
        public final float originalYaw;
        public final float originalPitch;
        public final String originalDimension;

        public static final Codec<CutsceneSession> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.STRING.fieldOf("playerUuid").forGetter(s -> s.playerUuid),
                Codec.STRING.fieldOf("cutsceneId").forGetter(s -> s.cutsceneId),
                Codec.INT.fieldOf("cameraIndex").forGetter(s -> s.cameraIndex),
                Codec.INT.fieldOf("ticksLeft").forGetter(s -> s.ticksLeft),
                Codec.STRING.fieldOf("originalGameMode").forGetter(s -> s.originalGameMode),
                Codec.DOUBLE.fieldOf("originalX").forGetter(s -> s.originalX),
                Codec.DOUBLE.fieldOf("originalY").forGetter(s -> s.originalY),
                Codec.DOUBLE.fieldOf("originalZ").forGetter(s -> s.originalZ),
                Codec.FLOAT.fieldOf("originalYaw").forGetter(s -> s.originalYaw),
                Codec.FLOAT.fieldOf("originalPitch").forGetter(s -> s.originalPitch),
                Codec.STRING.fieldOf("originalDimension").forGetter(s -> s.originalDimension)
        ).apply(inst, CutsceneSession::new));

        public CutsceneSession(String playerUuid, String cutsceneId, int cameraIndex, int ticksLeft,
                               String originalGameMode, double originalX, double originalY, double originalZ,
                               float originalYaw, float originalPitch, String originalDimension) {
            this.playerUuid = playerUuid;
            this.cutsceneId = cutsceneId;
            this.cameraIndex = cameraIndex;
            this.ticksLeft = ticksLeft;
            this.originalGameMode = originalGameMode;
            this.originalX = originalX;
            this.originalY = originalY;
            this.originalZ = originalZ;
            this.originalYaw = originalYaw;
            this.originalPitch = originalPitch;
            this.originalDimension = originalDimension;
        }
    }

    public static final Codec<GuardianWorldState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("overworldBossDefeated", false).forGetter(state -> state.overworldBossDefeated),
            Codec.BOOL.optionalFieldOf("netherBossDefeated", false).forGetter(state -> state.netherBossDefeated),
            Codec.STRING.listOf().optionalFieldOf("foundKeys", List.of()).forGetter(state -> new ArrayList<>(state.foundKeys)),
            Codec.STRING.listOf().optionalFieldOf("triggeredEvents", List.of()).forGetter(state -> new ArrayList<>(state.triggeredEvents)),
            CutsceneSession.CODEC.listOf().optionalFieldOf("activeSessions", List.of()).forGetter(state -> new ArrayList<>(state.activeSessions))
    ).apply(instance, GuardianWorldState::new));

    public static final SavedDataType<GuardianWorldState> TYPE = new SavedDataType<>(
            STATE_NAME,
            GuardianWorldState::new,
            CODEC,
            null
    );

    private boolean overworldBossDefeated = false;
    private boolean netherBossDefeated = false;
    private final Set<String> foundKeys = new HashSet<>();
    private final Set<String> triggeredEvents = new HashSet<>();
    private final List<CutsceneSession> activeSessions = new ArrayList<>();

    public static GuardianWorldState get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public GuardianWorldState() {
    }

    public GuardianWorldState(boolean overworldBossDefeated, boolean netherBossDefeated, List<String> foundKeys, List<String> triggeredEvents, List<CutsceneSession> activeSessions) {
        this.overworldBossDefeated = overworldBossDefeated;
        this.netherBossDefeated = netherBossDefeated;
        this.foundKeys.addAll(foundKeys);
        this.triggeredEvents.addAll(triggeredEvents);
        this.activeSessions.addAll(activeSessions);
    }

    public GuardianWorldState(boolean overworldBossDefeated, boolean netherBossDefeated) {
        this.overworldBossDefeated = overworldBossDefeated;
        this.netherBossDefeated = netherBossDefeated;
    }

    public GuardianWorldState(boolean overworldBossDefeated, boolean netherBossDefeated, List<String> foundKeys) {
        this.overworldBossDefeated = overworldBossDefeated;
        this.netherBossDefeated = netherBossDefeated;
        this.foundKeys.addAll(foundKeys);
    }

    public boolean isOverworldBossDefeated() {
        return overworldBossDefeated;
    }

    public boolean isNetherBossDefeated() {
        return netherBossDefeated;
    }

    public Set<String> getFoundKeys() {
        return Set.copyOf(foundKeys);
    }

    public Set<String> getTriggeredEvents() {
        return Set.copyOf(triggeredEvents);
    }

    public List<CutsceneSession> getActiveSessions() {
        return activeSessions;
    }

    public void setOverworldBossDefeated(boolean value) {
        overworldBossDefeated = value;
        setDirty();
    }

    public void setNetherBossDefeated(boolean value) {
        netherBossDefeated = value;
        setDirty();
    }

    public boolean addFoundKey(String key) {
        boolean added = foundKeys.add(key);
        if (added) {
            setDirty();
        }
        return added;
    }

    public int clearFoundKeys() {
        int count = foundKeys.size();
        if (count > 0) {
            foundKeys.clear();
            setDirty();
        }
        return count;
    }

    public boolean addTriggeredEvent(String playerUuid, String triggerType) {
        boolean added = triggeredEvents.add(playerUuid + ":" + triggerType);
        if (added) {
            setDirty();
        }
        return added;
    }

    public boolean hasTriggeredEvent(String playerUuid, String triggerType) {
        return triggeredEvents.contains(playerUuid + ":" + triggerType);
    }

    public void clearTriggeredEvents() {
        if (!triggeredEvents.isEmpty()) {
            triggeredEvents.clear();
            setDirty();
        }
    }

    public void markChanged() {
        setDirty();
    }
}
