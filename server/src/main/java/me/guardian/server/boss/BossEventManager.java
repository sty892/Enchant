package me.guardian.server.boss;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import me.guardian.GuardianMod;
import me.guardian.config.ConfigLoader;
import me.guardian.server.event.BossEventSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BossEventManager {
    private static final Map<String, JsonObject> BOSS_CONFIGS = new HashMap<>();
    private static final Gson GSON = new Gson();

    public static void initialize() {
        reload();
    }

    public static void reload() {
        BOSS_CONFIGS.clear();
        loadBossConfig("overworld", "boss_overworld.json");
        loadBossConfig("nether", "boss_nether.json");
        loadBossConfig("generic", "boss_generic.json");
        GuardianMod.LOGGER.info("BossEventManager initialized with " + BOSS_CONFIGS.size() + " boss configurations.");
    }

    private static void loadBossConfig(String key, String fileName) {
        try {
            String json = ConfigLoader.read(fileName);
            BOSS_CONFIGS.put(key, GSON.fromJson(json, JsonObject.class));
        } catch (IOException e) {
            GuardianMod.LOGGER.warn("Failed to load boss config (might be missing): " + fileName);
        } catch (Exception e) {
            GuardianMod.LOGGER.error("Failed to parse boss config: " + fileName, e);
        }
    }

    public static JsonObject getBossConfig(String key) {
        return BOSS_CONFIGS.get(key);
    }

    public static JsonObject getBossEvent(String key, String eventName) {
        JsonObject config = getBossConfig(key);
        if (config == null || !config.has(eventName) || !config.get(eventName).isJsonObject()) {
            return null;
        }
        return config.getAsJsonObject(eventName);
    }

    public static void triggerOnSpawn(String key, ServerLevel level, BlockPos center, Entity source) {
        BossEventSystem.executeEvent(level, getBossEvent(key, "on_spawn"), center, source, Collections.emptyMap());
    }

    public static void triggerOnDeath(String key, ServerLevel level, BlockPos center, Entity source, Map<UUID, Float> damageContributors) {
        BossEventSystem.executeEvent(level, getBossEvent(key, "on_death"), center, source, damageContributors);
    }
}
