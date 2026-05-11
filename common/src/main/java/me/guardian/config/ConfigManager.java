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
                      "on_spawn_script": "boss_overworld_spawn",
                      "on_death_script": "boss_overworld_death"
                    }
                    """,
            "boss_nether.json", """
                    {
                      "boss_id": "guardian_mod:boss_nether",
                      "on_spawn_script": "boss_nether_spawn",
                      "on_death_script": "boss_nether_death"
                    }
                    """,
            "boss_generic.json", """
                    {
                      "boss_id": "guardian_mod:boss_generic",
                      "on_death_script": "boss_generic_death"
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
                        { "item_id": "guardian_mod:key_1", "keyhole_id": "guardian_mod:keyhole_1", "slot": 1, "on_found_script": "key_found", "on_insert_script": "key_inserted", "on_insert": {} },
                        { "item_id": "guardian_mod:key_2", "keyhole_id": "guardian_mod:keyhole_2", "slot": 2, "on_found_script": "key_found", "on_insert_script": "key_inserted", "on_insert": {} },
                        { "item_id": "guardian_mod:key_3", "keyhole_id": "guardian_mod:keyhole_3", "slot": 3, "on_found_script": "key_found", "on_insert_script": "key_inserted", "on_insert": {} },
                        { "item_id": "guardian_mod:key_4", "keyhole_id": "guardian_mod:keyhole_4", "slot": 4, "on_found_script": "key_found", "on_insert_script": "key_inserted", "on_insert": {} },
                        { "item_id": "guardian_mod:key_5", "keyhole_id": "guardian_mod:keyhole_5", "slot": 5, "on_found_script": "key_found", "on_insert_script": "key_inserted", "on_insert": {} },
                        { "item_id": "guardian_mod:key_6", "keyhole_id": "guardian_mod:keyhole_6", "slot": 6, "on_found_script": "key_found", "on_insert_script": "key_inserted", "on_insert": {} },
                        { "item_id": "guardian_mod:key_7", "keyhole_id": "guardian_mod:keyhole_7", "slot": 7, "on_found_script": "key_found", "on_insert_script": "key_inserted", "on_insert": {} },
                        { "item_id": "guardian_mod:key_8", "keyhole_id": "guardian_mod:keyhole_8", "slot": 8, "on_found_script": "key_found", "on_insert_script": "key_inserted", "on_insert": {} }
                      ],
                      "on_all_inserted_script": "all_keyholes_filled",
                      "on_all_inserted": {}
                    }
                    """,
            "season_start.json", """
                    {
                      "event_id": "guardian_mod:season_start",
                      "actions": [
                        {
                          "broadcast_message": {
                            "text": "Хранитель Верхнего Мира пробуждается...",
                            "delay_ticks": 0
                          }
                        },
                        {
                          "broadcast_actionbar": {
                            "text": "Держитесь подальше от центра!",
                            "delay_ticks": 0
                          }
                        },
                        {
                          "play_boss_animation": {
                            "boss_id": "guardian_mod:boss_overworld",
                            "animation": "spawn",
                            "pos": {
                              "x": 0,
                              "y": 80,
                              "z": 0
                            },
                            "duration_ticks": 100,
                            "delay_ticks": 0
                          }
                        },
                        {
                          "spawn_boss": {
                            "boss_id": "guardian_mod:boss_overworld",
                            "pos": {
                              "x": 0,
                              "y": 80,
                              "z": 0
                            },
                            "delay_ticks": 100,
                            "play_spawn_animation": true
                          }
                        },
                        {
                          "knockback_players": {
                            "center": {
                              "x": 0,
                              "y": 80,
                              "z": 0
                            },
                            "radius": 64.0,
                            "horizontal_strength": 1.2,
                            "vertical_strength": 0.35,
                            "delay_ticks": 100
                          }
                        },
                        {
                          "world_border_expand": {
                            "center": {
                              "x": 0,
                              "z": 0
                            },
                            "from": 25,
                            "to": 1000,
                            "duration_seconds": 30,
                            "delay_ticks": 100
                          }
                        }
                      ]
                    }
                    """,
            "guardian_config.json", """
                    {
                      "diamond_restriction_enabled": true,
                      "op_diamond_whitelist": [],
                      "key_whitelist": []
                    }
                    """
    );

    private static final Map<String, String> DEFAULT_SCRIPTS = Map.of(
            "season_start.json", """
                    {
                      "commands": [
                        "say Guardian season start",
                        "summon guardian_mod:boss_overworld 0 70 0",
                        {
                          "delay_ticks": 80,
                          "command": "worldborder set 500 50s"
                        }
                      ]
                    }
                    """,
            "boss_overworld_spawn.json", """
                    {
                      "commands": [
                        "say Overworld Guardian has spawned"
                      ]
                    }
                    """,
            "boss_overworld_death.json", """
                    {
                      "commands": [
                        "say Overworld Guardian defeated",
                        "title @a title {\\"text\\":\\"Overworld Guardian defeated\\"}",
                        "worldborder set 10000 120",
                        "guardian stage 1",
                        "give @p guardian_mod:fragment_overworld",
                        "guardian structure place guardian_mod:altar"
                      ]
                    }
                    """,
            "boss_nether_spawn.json", """
                    {
                      "commands": [
                        "say Nether Guardian has spawned"
                      ]
                    }
                    """,
            "boss_nether_death.json", """
                    {
                      "commands": [
                        "say Nether Guardian defeated",
                        "title @a title {\\"text\\":\\"Nether Guardian defeated\\"}",
                        "give @p guardian_mod:fragment_nether"
                      ]
                    }
                    """,
            "boss_generic_death.json", """
                    {
                      "commands": [
                        "say Generic Guardian defeated",
                        "give @p guardian_mod:fragment_generic"
                      ]
                    }
                    """,
            "key_found.json", """
                    {
                      "commands": [
                        "say Player [nickname] found a key"
                      ]
                    }
                    """,
            "key_inserted.json", """
                    {
                      "commands": [
                        "say Player [nickname] inserted a key"
                      ]
                    }
                    """,
            "all_keyholes_filled.json", """
                    {
                      "commands": [
                        "say All keyholes are filled"
                      ]
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
        DEFAULT_SCRIPTS.forEach((fileName, json) -> {
            try {
                ConfigLoader.writeDefaultIfMissing("scripts/" + fileName, json);
            } catch (IOException e) {
                GuardianMod.LOGGER.error("Failed to create default script {}", fileName, e);
            }
        });
        try {
            Files.createDirectories(ConfigLoader.configRoot().resolve("structures"));
            Files.createDirectories(ConfigLoader.configRoot().resolve("scripts"));
        } catch (IOException e) {
            GuardianMod.LOGGER.error("Failed to create guardian config runtime directories", e);
        }
    }

    public static String readRaw(String fileName) throws IOException {
        return ConfigLoader.read(fileName);
    }
}
