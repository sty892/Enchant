package me.guardian.server.event;

import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

public class BossEventSystem {
    public static void executeEvent(ServerLevel level, JsonObject eventData, BlockPos center) {
        executeEvent(level, eventData, center, null, Collections.emptyMap());
    }

    public static void executeEvent(ServerLevel level, JsonObject eventData, BlockPos center, Entity source, Map<UUID, Float> damageContributors) {
        ScriptRunner.runInlineCommands(level, center, source, eventData);
    }
}
