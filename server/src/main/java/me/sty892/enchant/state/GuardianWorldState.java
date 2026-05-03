package me.sty892.enchant.state;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.World;

public class GuardianWorldState extends PersistentState {
    public boolean overworldBossDefeated = false;
    public boolean netherBossDefeated = false;

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        nbt.putBoolean("overworldBossDefeated", overworldBossDefeated);
        nbt.putBoolean("netherBossDefeated", netherBossDefeated);
        return nbt;
    }

    public static GuardianWorldState readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        GuardianWorldState state = new GuardianWorldState();
        state.overworldBossDefeated = nbt.getBoolean("overworldBossDefeated");
        state.netherBossDefeated = nbt.getBoolean("netherBossDefeated");
        return state;
    }

    public static GuardianWorldState getServerState(MinecraftServer server) {
        PersistentStateManager persistentStateManager = server.getWorld(World.OVERWORLD).getPersistentStateManager();
        GuardianWorldState state = persistentStateManager.getOrCreate(
                new Type<>(GuardianWorldState::new, GuardianWorldState::readNbt, null),
                "guardian_mod_state"
        );
        state.markDirty();
        return state;
    }
}
