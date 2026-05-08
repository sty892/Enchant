package me.guardian.server.event;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.guardian.GuardianMod;
import me.guardian.config.ConfigLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ScriptRunner {
    private static final Gson GSON = new Gson();

    private ScriptRunner() {
    }

    public static Path scriptsRoot() {
        return ConfigLoader.configRoot().resolve("scripts");
    }

    public static List<String> listScriptIds() {
        List<String> ids = new ArrayList<>();
        Path root = scriptsRoot();
        if (Files.notExists(root)) {
            return ids;
        }

        try (var stream = Files.list(root)) {
            stream.filter(Files::isRegularFile)
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .filter(name -> name.endsWith(".json"))
                    .map(name -> name.substring(0, name.length() - ".json".length()))
                    .sorted()
                    .forEach(ids::add);
        } catch (IOException e) {
            GuardianMod.LOGGER.warn("Failed to list guardian scripts", e);
        }
        return ids;
    }

    public static int runScript(CommandSourceStack source, String scriptId) {
        JsonObject script = loadScript(scriptId);
        if (script == null) {
            source.sendFailure(Component.literal("Guardian script not found or invalid: " + scriptId));
            return 0;
        }

        int executed = runCommands(sourceForCommands(source), readCommands(script));
        int result = executed;
        source.sendSuccess(() -> Component.literal("Executed guardian script " + scriptId + " commands=" + result), true);
        return executed;
    }

    public static int runScript(ServerLevel level, BlockPos center, Entity sourceEntity, String scriptId) {
        JsonObject script = loadScript(scriptId);
        if (script == null) {
            GuardianMod.LOGGER.warn("Guardian script not found or invalid: {}", scriptId);
            return 0;
        }
        return runCommands(sourceForCommands(level, center, sourceEntity), readCommands(script));
    }

    public static int runInlineCommands(ServerLevel level, BlockPos center, Entity sourceEntity, JsonObject eventData) {
        if (eventData == null || !eventData.has("commands") || !eventData.get("commands").isJsonArray()) {
            return 0;
        }
        return runCommands(sourceForCommands(level, center, sourceEntity), eventData.getAsJsonArray("commands"));
    }

    private static JsonObject loadScript(String scriptId) {
        Path path = scriptPath(scriptId);
        if (path == null || Files.notExists(path)) {
            return null;
        }

        try {
            JsonObject object = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), JsonObject.class);
            if (object == null || !object.has("commands") || !object.get("commands").isJsonArray()) {
                GuardianMod.LOGGER.warn("Guardian script {} must contain a commands array", scriptId);
                return null;
            }
            return object;
        } catch (IOException | RuntimeException e) {
            GuardianMod.LOGGER.error("Failed to load guardian script {}", scriptId, e);
            return null;
        }
    }

    private static Path scriptPath(String scriptId) {
        String normalizedId = scriptId == null ? "" : scriptId.trim();
        if (normalizedId.endsWith(".json")) {
            normalizedId = normalizedId.substring(0, normalizedId.length() - ".json".length());
        }
        if (normalizedId.isBlank() || !normalizedId.toLowerCase(Locale.ROOT).matches("[a-z0-9_./-]+")) {
            return null;
        }

        Path root = scriptsRoot().normalize();
        Path path = root.resolve(normalizedId + ".json").normalize();
        return path.startsWith(root) ? path : null;
    }

    private static JsonArray readCommands(JsonObject script) {
        return script.getAsJsonArray("commands");
    }

    private static CommandSourceStack sourceForCommands(CommandSourceStack source) {
        if (source.getEntity() != null) {
            return source.withPermission(PermissionSet.ALL_PERMISSIONS);
        }

        MinecraftServer server = source.getServer();
        ServerLevel level = server.overworld();
        Vec3 position = source.getPosition();
        return source.withLevel(level)
                .withPosition(position)
                .withRotation(Vec2.ZERO)
                .withPermission(PermissionSet.ALL_PERMISSIONS);
    }

    private static CommandSourceStack sourceForCommands(ServerLevel level, BlockPos center, Entity entity) {
        MinecraftServer server = level.getServer();
        Vec3 position = entity == null ? Vec3.atCenterOf(center) : entity.position();
        Vec2 rotation = entity == null ? Vec2.ZERO : entity.getRotationVector();
        return new CommandSourceStack(
                server,
                position,
                rotation,
                level,
                PermissionSet.ALL_PERMISSIONS,
                "GuardianMod",
                Component.literal("GuardianMod"),
                server,
                entity
        );
    }

    private static int runCommands(CommandSourceStack source, JsonArray commands) {
        int executed = 0;
        for (JsonElement element : commands) {
            if (!element.isJsonPrimitive()) {
                GuardianMod.LOGGER.warn("Ignoring non-string guardian script command: {}", element);
                continue;
            }

            String command = element.getAsString().trim();
            if (command.isEmpty()) {
                continue;
            }
            if (command.startsWith("/")) {
                command = command.substring(1);
            }

            try {
                source.getServer().getCommands().performPrefixedCommand(source, command);
                executed++;
            } catch (RuntimeException e) {
                GuardianMod.LOGGER.error("Guardian script command failed: {}", command, e);
            }
        }
        return executed;
    }
}
