package me.sty892.enchant.config;

import java.util.HashMap;
import java.util.Map;

public class AltarConfig {
    public Map<String, Integer> stage_1 = new HashMap<>();
    public Map<String, Integer> stage_2 = new HashMap<>();
    public String stage_threshold_flag = "netherBossDefeated";

    public AltarConfig() {
        stage_1.put("max_speed", 3);
        stage_1.put("max_protection", 3);
        stage_1.put("max_damage", 3);
        stage_1.put("max_recovery", 3);

        stage_2.put("max_speed", 7);
        stage_2.put("max_protection", 7);
        stage_2.put("max_damage", 7);
        stage_2.put("max_recovery", 7);
    }
}
