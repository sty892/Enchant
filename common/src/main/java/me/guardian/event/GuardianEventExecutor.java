package me.guardian.event;

import com.google.gson.JsonObject;
import me.guardian.GuardianMod;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

public final class GuardianEventExecutor {
    private static EventExecutor executor = (level, eventData, center, source) ->
            GuardianMod.LOGGER.warn("Guardian event executor is not registered; skipping event {}", eventData);

    private GuardianEventExecutor() {
    }

    public static void setExecutor(EventExecutor executor) {
        GuardianEventExecutor.executor = executor;
    }

    public static void execute(Level level, JsonObject eventData, BlockPos center, Entity source) {
        if (eventData == null || eventData.isEmpty()) {
            return;
        }
        executor.execute(level, eventData, center, source);
    }

    @FunctionalInterface
    public interface EventExecutor {
        void execute(Level level, JsonObject eventData, BlockPos center, Entity source);
    }
}
