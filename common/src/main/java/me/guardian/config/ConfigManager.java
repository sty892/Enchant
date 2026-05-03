package me.guardian.config;

import me.guardian.GuardianMod;

import java.io.IOException;
import java.util.Map;

public final class ConfigManager {
    private static final Map<String, String> DEFAULT_CONFIGS = Map.of(
            "boss_overworld.json", """
                    {
                      "id": "guardian_mod:boss_overworld",
                      "displayName": "Overworld Guardian",
                      "enabled": true,
                      "runtimeAssetsOnly": true,
                      "todo": "Implement server-authoritative boss runtime."
                    }
                    """,
            "boss_nether.json", """
                    {
                      "id": "guardian_mod:boss_nether",
                      "displayName": "Nether Guardian",
                      "enabled": true,
                      "runtimeAssetsOnly": true,
                      "todo": "Implement server-authoritative boss runtime."
                    }
                    """,
            "boss_generic.json", """
                    {
                      "id": "guardian_mod:boss_generic",
                      "displayName": "Generic Guardian",
                      "enabled": true,
                      "runtimeAssetsOnly": true,
                      "todo": "Shared boss defaults for later implementation."
                    }
                    """,
            "altar_config.json", """
                    {
                      "enabled": true,
                      "todo": "Implement altar ritual after foundation build is stable."
                    }
                    """,
            "keys_config.json", """
                    {
                      "requiredKeys": 3,
                      "todo": "Implement key progression after foundation build is stable."
                    }
                    """,
            "guardian_config.json", """
                    {
                      "configVersion": 1,
                      "bossAssetsInClientJar": false,
                      "runtimeConfigDirectory": "config/guardian_mod"
                    }
                    """
    );

    private ConfigManager() {
    }

    public static void initialize() {
        DEFAULT_CONFIGS.forEach((fileName, json) -> {
            try {
                ConfigLoader.writeDefaultIfMissing(fileName, json);
            } catch (IOException e) {
                GuardianMod.LOGGER.error("Failed to create default config {}", fileName, e);
            }
        });
    }

    public static String readRaw(String fileName) throws IOException {
        return ConfigLoader.read(fileName);
    }
}
