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
    public static final Codec<GuardianWorldState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("overworldBossDefeated", false).forGetter(state -> state.overworldBossDefeated),
            Codec.BOOL.optionalFieldOf("netherBossDefeated", false).forGetter(state -> state.netherBossDefeated),
            Codec.STRING.listOf().optionalFieldOf("foundKeys", List.of()).forGetter(state -> new ArrayList<>(state.foundKeys))
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

    public static GuardianWorldState get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public GuardianWorldState() {
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
}
