package me.guardian.server.boss;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import me.guardian.GuardianMod;
import me.guardian.config.ConfigLoader;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class BossEventManager {
    private static final Map<String, JsonObject> BOSS_CONFIGS = new HashMap<>();
    private static final Gson GSON = new Gson();

    public static void initialize() {
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
}
