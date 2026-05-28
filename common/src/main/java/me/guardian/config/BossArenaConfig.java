package me.guardian.config;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import me.guardian.GuardianMod;

import java.io.IOException;

/**
 * Boss arena configuration — loaded from {@code boss_overworld.json}.
 * Contains settings that govern the arena gate behavior:
 * <ul>
 *   <li>{@code gate_wipe_reopen_seconds} — how many real-time seconds after a wipe before gates
 *       reopen (default 3600 = 1 hour).</li>
 * </ul>
 */
public final class BossArenaConfig {

    private static final Gson GSON = new Gson();
    private static final String FILE_NAME = "boss_overworld.json";
    private static final long DEFAULT_REOPEN_SECONDS = 3600L;

    /** Cached reopen delay in game-ticks (20 ticks/sec). */
    private static volatile long gateWipeReopenTicks = DEFAULT_REOPEN_SECONDS * 20L;

    private BossArenaConfig() {
    }

    /** Returns the gate wipe reopen delay in game-ticks. */
    public static long getGateWipeReopenTicks() {
        return gateWipeReopenTicks;
    }

    /**
     * Reads {@code gate_wipe_reopen_seconds} from {@code boss_overworld.json}.
     * Safe to call at server startup; falls back to the default on any error.
     */
    public static void reload() {
        try {
            String raw = ConfigManager.readRaw(FILE_NAME);
            JsonObject root = GSON.fromJson(raw, JsonObject.class);
            if (root == null) {
                gateWipeReopenTicks = DEFAULT_REOPEN_SECONDS * 20L;
                return;
            }
            long seconds = DEFAULT_REOPEN_SECONDS;
            if (root.has("gate_wipe_reopen_seconds") && root.get("gate_wipe_reopen_seconds").isJsonPrimitive()) {
                try {
                    seconds = root.get("gate_wipe_reopen_seconds").getAsLong();
                    seconds = Math.max(60L, seconds); // minimum 1 minute
                } catch (RuntimeException e) {
                    GuardianMod.LOGGER.warn("Invalid gate_wipe_reopen_seconds in {}", FILE_NAME);
                }
            }
            gateWipeReopenTicks = seconds * 20L;
            GuardianMod.LOGGER.info("BossArenaConfig: gate wipe reopen = {}s ({} ticks)", seconds, gateWipeReopenTicks);
        } catch (IOException e) {
            gateWipeReopenTicks = DEFAULT_REOPEN_SECONDS * 20L;
            GuardianMod.LOGGER.warn("Failed to read {} for arena config, using defaults", FILE_NAME, e);
        }
    }
}
