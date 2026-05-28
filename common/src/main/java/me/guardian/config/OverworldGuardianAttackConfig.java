package me.guardian.config;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.guardian.GuardianMod;
import net.minecraft.server.level.ServerLevel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class OverworldGuardianAttackConfig {
    public static final String FILE_NAME = "overworld_attack_timing.json";

    private static final Gson GSON = new Gson();
    private static final Map<String, AttackTiming> DEFAULTS = Map.ofEntries(
            Map.entry("right_hand_wave", new AttackTiming(22, 34, 5.5D)),
            Map.entry("left_hand_wave", new AttackTiming(22, 34, 5.5D)),
            Map.entry("two_hand_wave", new AttackTiming(32, 44, 7.5D)),
            Map.entry("hands_slam_line", new AttackTiming(34, 50, 14.0D)),
            Map.entry("stomp_players", new AttackTiming(38, 54, 7.5D)),
            Map.entry("bomb_traps", new AttackTiming(20, 44, 12.0D)),
            Map.entry("statue_revival", new AttackTiming(40, 60, 24.0D)),
            Map.entry("healing_shield", new AttackTiming(1, 1, 24.0D)),
            Map.entry("charge_ram", new AttackTiming(30, 60, 25.0D)),
            Map.entry("ground_vines", new AttackTiming(40, 55, 30.0D)),
            Map.entry("vine_pull", new AttackTiming(25, 35, 20.0D)),
            Map.entry("arena_walls", new AttackTiming(20, 40, 16.0D)),
            Map.entry("leap_attack", new AttackTiming(100, 130, 16.0D))
    );

    private static volatile Map<String, AttackTiming> timings = DEFAULTS;
    private static volatile int autoReloadTicks = 20;
    private static long lastCheckGameTime = -1L;
    private static long lastModifiedMillis = -1L;

    private OverworldGuardianAttackConfig() {
    }

    public static AttackTiming get(String attackId) {
        return timings.getOrDefault(attackId, DEFAULTS.getOrDefault(attackId, new AttackTiming(1, 1, 16.0D)));
    }

    public static void tickAutoReload(ServerLevel level) {
        long gameTime = level.getGameTime();
        int interval = Math.max(1, autoReloadTicks);
        if (lastCheckGameTime >= 0 && gameTime - lastCheckGameTime < interval) {
            return;
        }
        lastCheckGameTime = gameTime;

        Path path = ConfigLoader.configRoot().resolve(FILE_NAME);
        try {
            if (Files.notExists(path)) {
                return;
            }
            long modified = Files.getLastModifiedTime(path).toMillis();
            if (modified != lastModifiedMillis) {
                reload();
            }
        } catch (IOException e) {
            GuardianMod.LOGGER.warn("Failed to check {} modification time", FILE_NAME, e);
        }
    }

    public static void reload() {
        try {
            JsonObject root = GSON.fromJson(ConfigManager.readRaw(FILE_NAME), JsonObject.class);
            if (root == null) {
                timings = DEFAULTS;
                return;
            }

            autoReloadTicks = readInt(root, "auto_reload_ticks", 20, 1, 20 * 60);
            Map<String, AttackTiming> loaded = new HashMap<>(DEFAULTS);
            if (root.has("attacks") && root.get("attacks").isJsonObject()) {
                JsonObject attacks = root.getAsJsonObject("attacks");
                for (Map.Entry<String, JsonElement> entry : attacks.entrySet()) {
                    if (!entry.getValue().isJsonObject()) {
                        continue;
                    }
                    AttackTiming fallback = loaded.getOrDefault(entry.getKey(), new AttackTiming(1, 1, 16.0D));
                    loaded.put(entry.getKey(), readTiming(entry.getValue().getAsJsonObject(), fallback));
                }
            }
            timings = Map.copyOf(loaded);
            Path path = ConfigLoader.configRoot().resolve(FILE_NAME);
            lastModifiedMillis = Files.exists(path) ? Files.getLastModifiedTime(path).toMillis() : -1L;
            GuardianMod.LOGGER.info("Reloaded {}", FILE_NAME);
        } catch (IOException | RuntimeException e) {
            timings = DEFAULTS;
            GuardianMod.LOGGER.warn("Failed to reload {}; using default overworld attack timings", FILE_NAME, e);
        }
    }

    private static AttackTiming readTiming(JsonObject object, AttackTiming fallback) {
        int hitTick = readInt(object, "hit_tick", fallback.hitTick(), 1, 20 * 60);
        int durationTicks = readInt(object, "duration_ticks", Math.max(fallback.durationTicks(), hitTick), hitTick, 20 * 60);
        double maxStartDistance = readDouble(object, "max_start_distance", fallback.maxStartDistance(), 0.0D, 128.0D);
        return new AttackTiming(hitTick, durationTicks, maxStartDistance);
    }

    private static int readInt(JsonObject object, String key, int fallback, int min, int max) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()) {
            return fallback;
        }
        try {
            int value = object.get(key).getAsInt();
            return Math.max(min, Math.min(max, value));
        } catch (RuntimeException e) {
            GuardianMod.LOGGER.warn("Invalid int value for {} in {}: {}", key, FILE_NAME, object.get(key));
            return fallback;
        }
    }

    private static double readDouble(JsonObject object, String key, double fallback, double min, double max) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()) {
            return fallback;
        }
        try {
            double value = object.get(key).getAsDouble();
            return Math.max(min, Math.min(max, value));
        } catch (RuntimeException e) {
            GuardianMod.LOGGER.warn("Invalid double value for {} in {}: {}", key, FILE_NAME, object.get(key));
            return fallback;
        }
    }

    public record AttackTiming(int hitTick, int durationTicks, double maxStartDistance) {
    }
}
