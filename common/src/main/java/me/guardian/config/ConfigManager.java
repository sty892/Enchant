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
                      "on_death_script": "boss_overworld_death",
                      "on_death": {
                        "world_border_expand": { "to": 10000, "duration_seconds": 120 },
                        "spawn_structure": "guardian_mod:altar",
                        "give_fragment": "guardian_mod:fragment_overworld",
                        "set_flag": "overworldBossDefeated",
                        "allow_diamonds": true,
                        "broadcast_title": "Overworld Guardian defeated"
                      }
                    }
                    """,
            "boss_nether.json", """
                    {
                      "boss_id": "guardian_mod:boss_nether",
                      "on_spawn_script": "boss_nether_spawn",
                      "on_death_script": "boss_nether_death",
                      "on_death": {
                        "give_fragment": "guardian_mod:fragment_nether",
                        "set_flag": "netherBossDefeated",
                        "broadcast_title": "Nether Guardian defeated"
                      }
                    }
                    """,
            "boss_generic.json", """
                    {
                      "boss_id": "guardian_mod:boss_generic",
                      "on_death_script": "boss_generic_death",
                      "on_death": {
                        "give_fragment": "guardian_mod:fragment_generic"
                      }
                    }
                    """,
            "overworld_attack_timing.json", """
                    {
                      "auto_reload_ticks": 20,
                      "attacks": {
                        "right_hand_wave": { "hit_tick": 22, "duration_ticks": 34, "max_start_distance": 5.5 },
                        "left_hand_wave": { "hit_tick": 22, "duration_ticks": 34, "max_start_distance": 5.5 },
                        "two_hand_wave": { "hit_tick": 32, "duration_ticks": 44, "max_start_distance": 7.5 },
                        "hands_slam_line": { "hit_tick": 34, "duration_ticks": 50, "max_start_distance": 14.0 },
                        "stomp_players": { "hit_tick": 38, "duration_ticks": 54, "max_start_distance": 7.5 },
                        "bomb_traps": { "hit_tick": 20, "duration_ticks": 44, "max_start_distance": 12.0 },
                        "statue_revival": { "hit_tick": 1, "duration_ticks": 1, "max_start_distance": 24.0 },
                        "healing_shield": { "hit_tick": 1, "duration_ticks": 1, "max_start_distance": 24.0 }
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
                        "guardian stage 1"
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
                        "say Nether Guardian defeated"
                      ]
                    }
                    """,
            "boss_generic_death.json", """
                    {
                      "commands": [
                        "say Generic Guardian defeated"
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
