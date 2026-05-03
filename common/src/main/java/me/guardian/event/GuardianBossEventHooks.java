package me.guardian.event;

import me.guardian.GuardianMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.Map;
import java.util.UUID;

public final class GuardianBossEventHooks {
    private static SpawnHook spawnHook = (bossKey, level, center, source) ->
            GuardianMod.LOGGER.warn("Boss spawn hook is not registered; skipping {}", bossKey);
    private static DeathHook deathHook = (bossKey, level, center, source, damageContributors) ->
            GuardianMod.LOGGER.warn("Boss death hook is not registered; skipping {}", bossKey);

    private GuardianBossEventHooks() {
    }

    public static void setSpawnHook(SpawnHook spawnHook) {
        GuardianBossEventHooks.spawnHook = spawnHook;
    }

    public static void setDeathHook(DeathHook deathHook) {
        GuardianBossEventHooks.deathHook = deathHook;
    }

    public static void triggerOnSpawn(String bossKey, ServerLevel level, BlockPos center, Entity source) {
        spawnHook.trigger(bossKey, level, center, source);
    }

    public static void triggerOnDeath(String bossKey, ServerLevel level, BlockPos center, Entity source, Map<UUID, Float> damageContributors) {
        deathHook.trigger(bossKey, level, center, source, damageContributors);
    }

    @FunctionalInterface
    public interface SpawnHook {
        void trigger(String bossKey, ServerLevel level, BlockPos center, Entity source);
    }

    @FunctionalInterface
    public interface DeathHook {
        void trigger(String bossKey, ServerLevel level, BlockPos center, Entity source, Map<UUID, Float> damageContributors);
    }
}
