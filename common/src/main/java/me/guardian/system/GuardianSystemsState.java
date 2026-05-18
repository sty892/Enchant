package me.guardian.system;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public final class GuardianSystemsState extends SavedData {
    public static final String STATE_NAME = "guardian_mod_systems";
    public static final Codec<GuardianSystemsState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("trigger_systems_enabled", true).forGetter(GuardianSystemsState::triggerSystemsEnabled)
    ).apply(instance, GuardianSystemsState::new));
    public static final SavedDataType<GuardianSystemsState> TYPE = new SavedDataType<>(STATE_NAME, GuardianSystemsState::new, CODEC, null);

    private boolean triggerSystemsEnabled;

    public GuardianSystemsState() {
        this(true);
    }

    public GuardianSystemsState(boolean triggerSystemsEnabled) {
        this.triggerSystemsEnabled = triggerSystemsEnabled;
    }

    public static GuardianSystemsState get(MinecraftServer server) {
        return get(server.overworld());
    }

    public static GuardianSystemsState get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public static boolean triggerSystemsEnabled(Level level) {
        return !(level instanceof ServerLevel serverLevel) || get(serverLevel).triggerSystemsEnabled();
    }

    public boolean triggerSystemsEnabled() {
        return triggerSystemsEnabled;
    }

    public boolean toggleTriggerSystems() {
        triggerSystemsEnabled = !triggerSystemsEnabled;
        setDirty();
        return triggerSystemsEnabled;
    }
}
