package me.guardian.config;

import me.guardian.GuardianMod;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;

public final class ConfigManager {
    private static final Map<String, String> DEFAULT_CONFIGS = Map.of(
            "boss_overworld.json", """
                    {
                      "boss_id": "guardian_mod:boss_overworld",
                      "on_spawn": {
                        "play_animation": "spawn",
                        "world_border_expand": { "to": 200, "duration_seconds": 30 }
                      },
                      "on_death": {
                        "world_border_expand": { "to": 500, "duration_seconds": 60 },
                        "spawn_structure": "guardian_mod:altar",
                        "spawn_structure_offset": { "x": 0, "y": 0, "z": 0 },
                        "run_command": "say Overworld Guardian defeated",
                        "give_fragment": "guardian_mod:fragment_overworld",
                        "set_flag": "overworldBossDefeated",
                        "broadcast_title": "Хранитель Верхнего Мира повержен!",
                        "allow_diamonds": true
                      }
                    }
                    """,
            "boss_nether.json", """
                    {
                      "boss_id": "guardian_mod:boss_nether",
                      "on_spawn": {
                        "play_animation": "spawn",
                        "world_border_expand": { "to": 800, "duration_seconds": 30 }
                      },
                      "on_death": {
                        "world_border_expand": { "to": 2000, "duration_seconds": 120 },
                        "spawn_structure": "guardian_mod:altar_nether",
                        "spawn_structure_offset": { "x": 0, "y": 0, "z": 0 },
                        "run_command": "say Nether Guardian defeated",
                        "give_fragment": "guardian_mod:fragment_nether",
                        "set_flag": "netherBossDefeated",
                        "broadcast_title": "Хранитель Нижнего Мира повержен!"
                      }
                    }
                    """,
            "boss_generic.json", """
                    {
                      "boss_id": "guardian_mod:boss_generic",
                      "on_death": {
                        "spawn_structure": "guardian_mod:generic_reward",
                        "run_command": "say Generic Guardian defeated",
                        "give_fragment": "guardian_mod:fragment_generic"
                      }
                    }
                    """,
            "altar_config.json", """
                    {
                      "radius": 5,
                      "ritual_ticks": 100,
                      "stage_1": {
                        "max_speed": 3,
                        "max_protection": 3,
                        "max_damage": 3,
                        "max_recovery": 3
                      },
                      "stage_2": {
                        "max_speed": 7,
                        "max_protection": 7,
                        "max_damage": 7,
                        "max_recovery": 7
                      },
                      "stage_threshold_flag": "netherBossDefeated"
                    }
                    """,
            "keys_config.json", """
                    {
                      "keys": [
                        { "item_id": "guardian_mod:key_1", "keyhole_id": "guardian_mod:keyhole_1", "slot": 1, "on_insert": {} },
                        { "item_id": "guardian_mod:key_2", "keyhole_id": "guardian_mod:keyhole_2", "slot": 2, "on_insert": {} },
                        { "item_id": "guardian_mod:key_3", "keyhole_id": "guardian_mod:keyhole_3", "slot": 3, "on_insert": {} },
                        { "item_id": "guardian_mod:key_4", "keyhole_id": "guardian_mod:keyhole_4", "slot": 4, "on_insert": {} },
                        { "item_id": "guardian_mod:key_5", "keyhole_id": "guardian_mod:keyhole_5", "slot": 5, "on_insert": {} },
                        { "item_id": "guardian_mod:key_6", "keyhole_id": "guardian_mod:keyhole_6", "slot": 6, "on_insert": {} },
                        { "item_id": "guardian_mod:key_7", "keyhole_id": "guardian_mod:keyhole_7", "slot": 7, "on_insert": {} },
                        { "item_id": "guardian_mod:key_8", "keyhole_id": "guardian_mod:keyhole_8", "slot": 8, "on_insert": {} }
                      ],
                      "on_all_inserted": {}
                    }
                    """,
            "guardian_config.json", """
                    {
                      "diamond_restriction_enabled": true,
                      "op_diamond_whitelist": []
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
        try {
            Files.createDirectories(ConfigLoader.configRoot().resolve("structures"));
        } catch (IOException e) {
            GuardianMod.LOGGER.error("Failed to create guardian config structures directory", e);
        }
    }

    public static String readRaw(String fileName) throws IOException {
        return ConfigLoader.read(fileName);
    }
}
