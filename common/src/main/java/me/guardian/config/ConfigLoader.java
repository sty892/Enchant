package me.guardian.config;

import me.guardian.GuardianMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ConfigLoader {
    private ConfigLoader() {
    }

    public static Path configRoot() {
        return FabricLoader.getInstance().getConfigDir().resolve(GuardianMod.MOD_ID);
    }

    public static void writeDefaultIfMissing(String fileName, String json) throws IOException {
        Path root = configRoot();
        Files.createDirectories(root);
        Path target = root.resolve(fileName);
        if (Files.notExists(target)) {
            Files.writeString(target, json, StandardCharsets.UTF_8);
        }
    }

    public static String read(String fileName) throws IOException {
        return Files.readString(configRoot().resolve(fileName), StandardCharsets.UTF_8);
    }

    public static void write(String fileName, String json) throws IOException {
        Path root = configRoot();
        Files.createDirectories(root);
        Files.writeString(root.resolve(fileName), json, StandardCharsets.UTF_8);
    }
}
