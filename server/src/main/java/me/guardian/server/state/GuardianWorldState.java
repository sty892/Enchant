package me.guardian.server.state;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public class GuardianWorldState extends SavedData {
    public static final String STATE_NAME = "guardian_mod_world_state";
    public static final Codec<GuardianWorldState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("overworldBossDefeated", false).forGetter(state -> state.overworldBossDefeated),
            Codec.BOOL.optionalFieldOf("netherBossDefeated", false).forGetter(state -> state.netherBossDefeated)
    ).apply(instance, GuardianWorldState::new));
    public static final SavedDataType<GuardianWorldState> TYPE = new SavedDataType<>(
            STATE_NAME,
            GuardianWorldState::new,
            CODEC,
            null
    );

    public boolean overworldBossDefeated = false;
    public boolean netherBossDefeated = false;

    public static GuardianWorldState get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public GuardianWorldState() {
    }

    public GuardianWorldState(boolean overworldBossDefeated, boolean netherBossDefeated) {
        this.overworldBossDefeated = overworldBossDefeated;
        this.netherBossDefeated = netherBossDefeated;
    }
}
