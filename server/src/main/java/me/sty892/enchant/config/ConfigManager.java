package me.sty892.enchant.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;

public class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve("guardian_mod");
    private static GuardianConfig config;
    private static AltarConfig altarConfig;

    public static void load() {
        File dir = CONFIG_DIR.toFile();
        if (!dir.exists()) dir.mkdirs();

        loadGuardianConfig();
        loadAltarConfig();
    }

    private static void loadGuardianConfig() {
        File file = CONFIG_DIR.resolve("guardian_config.json").toFile();
        if (!file.exists()) {
            config = new GuardianConfig();
            save();
        } else {
            try (FileReader reader = new FileReader(file)) {
                config = GSON.fromJson(reader, GuardianConfig.class);
                if (config == null) config = new GuardianConfig();
            } catch (IOException e) {
                config = new GuardianConfig();
            }
        }
    }

    private static void loadAltarConfig() {
        File file = CONFIG_DIR.resolve("altar_config.json").toFile();
        if (!file.exists()) {
            altarConfig = new AltarConfig();
            saveAltar();
        } else {
            try (FileReader reader = new FileReader(file)) {
                altarConfig = GSON.fromJson(reader, AltarConfig.class);
                if (altarConfig == null) altarConfig = new AltarConfig();
            } catch (IOException e) {
                altarConfig = new AltarConfig();
            }
        }
    }

    public static void save() {
        File file = CONFIG_DIR.resolve("guardian_config.json").toFile();
        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(config, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void saveAltar() {
        File file = CONFIG_DIR.resolve("altar_config.json").toFile();
        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(altarConfig, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static GuardianConfig getConfig() {
        if (config == null) load();
        return config;
    }

    public static AltarConfig getAltarConfig() {
        if (altarConfig == null) load();
        return altarConfig;
    }
}
