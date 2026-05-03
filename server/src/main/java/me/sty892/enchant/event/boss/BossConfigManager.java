package me.sty892.enchant.event.boss;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class BossConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve("guardian_mod");
    private static final Map<String, BossEventConfig> BOSS_CONFIGS = new HashMap<>();

    public static void load() {
        BOSS_CONFIGS.clear();
        loadBossFile("boss_overworld.json");
        loadBossFile("boss_nether.json");
        loadBossFile("boss_generic.json");
    }

    private static void loadBossFile(String filename) {
        File file = CONFIG_DIR.resolve(filename).toFile();
        if (file.exists()) {
            try (FileReader reader = new FileReader(file)) {
                BossEventConfig config = GSON.fromJson(reader, BossEventConfig.class);
                if (config != null && config.boss_id != null) {
                    BOSS_CONFIGS.put(config.boss_id, config);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static BossEventConfig getConfigForBoss(String bossId) {
        if (BOSS_CONFIGS.isEmpty()) load();
        return BOSS_CONFIGS.get(bossId);
    }
}
