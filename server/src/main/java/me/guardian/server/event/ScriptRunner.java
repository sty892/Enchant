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
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public final class ScriptRunner {
    private static final Gson GSON = new Gson();
    private static final List<ScheduledCommand> SCHEDULED_COMMANDS = new ArrayList<>();

    private ScriptRunner() {
    }

    public static Path scriptsRoot() {
        return ConfigLoader.configRoot().resolve("scripts");
    }

    public static List<String> listScriptIds() {
        Set<String> ids = new TreeSet<>();
        Path root = scriptsRoot();
        if (Files.exists(root)) {
            try (var stream = Files.list(root)) {
                stream.filter(Files::isRegularFile)
                        .map(Path::getFileName)
                        .map(Path::toString)
                        .filter(name -> name.endsWith(".json"))
                        .map(name -> name.substring(0, name.length() - ".json".length()))
                        .forEach(ids::add);
            } catch (IOException e) {
                GuardianMod.LOGGER.warn("Failed to list guardian scripts", e);
            }
        }

        Path configRoot = ConfigLoader.configRoot();
        if (Files.exists(configRoot)) {
            try (var stream = Files.list(configRoot)) {
                stream.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".json"))
                        .filter(ScriptRunner::looksLikeCommandScript)
                        .map(Path::getFileName)
                        .map(Path::toString)
                        .map(name -> name.substring(0, name.length() - ".json".length()))
                        .filter(id -> !ids.contains(id))
                        .forEach(ids::add);
            } catch (IOException e) {
                GuardianMod.LOGGER.warn("Failed to list legacy guardian scripts", e);
            }
        }
        return new ArrayList<>(ids);
    }

    public static void tick(MinecraftServer server) {
        if (SCHEDULED_COMMANDS.isEmpty()) {
            return;
        }

        Iterator<ScheduledCommand> iterator = SCHEDULED_COMMANDS.iterator();
        while (iterator.hasNext()) {
            ScheduledCommand scheduled = iterator.next();
            scheduled.delayTicks--;
            if (scheduled.delayTicks <= 0) {
                executeCommand(scheduled.source, scheduled.command);
                iterator.remove();
            }
        }
    }

    public static int runScript(CommandSourceStack source, String scriptId) {
        return runScript(source, scriptId, Collections.emptyMap());
    }

    public static int runScript(CommandSourceStack source, String scriptId, Map<String, String> variables) {
        JsonObject script = loadScript(scriptId);
        if (script == null) {
            source.sendFailure(Component.literal("Guardian script not found or invalid: " + scriptId));
            return 0;
        }

        int executed = runCommands(sourceForCommands(source), readCommands(script), variables);
        int result = executed;
        source.sendSuccess(() -> Component.literal("Executed guardian script " + scriptId + " commands=" + result), true);
        return executed;
    }

    public static int runScript(ServerLevel level, BlockPos center, Entity sourceEntity, String scriptId) {
        return runScript(level, center, sourceEntity, scriptId, Collections.emptyMap());
    }

    public static int runScript(ServerLevel level, BlockPos center, Entity sourceEntity, String scriptId, Map<String, String> variables) {
        JsonObject script = loadScript(scriptId);
        if (script == null) {
            GuardianMod.LOGGER.warn("Guardian script not found or invalid: {}", scriptId);
            return 0;
        }
        return runCommands(sourceForCommands(level, center, sourceEntity), readCommands(script), variables);
    }

    public static int runInlineCommands(ServerLevel level, BlockPos center, Entity sourceEntity, JsonObject eventData) {
        return runInlineCommands(level, center, sourceEntity, eventData, Collections.emptyMap());
    }

    public static int runInlineCommands(ServerLevel level, BlockPos center, Entity sourceEntity, JsonObject eventData, Map<String, String> variables) {
        if (eventData == null || !eventData.has("commands") || !eventData.get("commands").isJsonArray()) {
            return 0;
        }
        return runCommands(sourceForCommands(level, center, sourceEntity), eventData.getAsJsonArray("commands"), variables);
    }

    private static JsonObject loadScript(String scriptId) {
        String normalizedId = normalizeScriptId(scriptId);
        if (normalizedId == null) {
            return null;
        }

        Path path = scriptPath(normalizedId);
        Path legacyPath = legacyScriptPath(normalizedId);
        if (path != null && Files.exists(path)) {
            if (legacyPath != null && Files.exists(legacyPath)) {
                GuardianMod.LOGGER.warn(
                        "Guardian script {} exists in scripts/ and in the config root; using scripts/{} and ignoring {}",
                        normalizedId,
                        normalizedId + ".json",
                        legacyPath
                );
            }
            return loadScript(path, normalizedId);
        }

        if (legacyPath == null || Files.notExists(legacyPath)) {
            return null;
        }

        if (!looksLikeCommandScript(legacyPath)) {
            GuardianMod.LOGGER.warn(
                    "Ignoring legacy guardian config root file {} for script {}; command scripts must contain a commands array",
                    legacyPath,
                    normalizedId
            );
            return null;
        }

        GuardianMod.LOGGER.warn(
                "Loading guardian script {} from legacy config root path {}; move command scripts to config/guardian_mod/scripts",
                normalizedId,
                legacyPath
        );
        return loadScript(legacyPath, normalizedId);
    }

    private static JsonObject loadScript(Path path, String scriptId) {
        try {
            String raw = Files.readString(path, StandardCharsets.UTF_8);
            JsonObject object;
            try {
                object = GSON.fromJson(raw, JsonObject.class);
            } catch (RuntimeException parseError) {
                object = parseLenientCommandScript(raw, scriptId);
            }
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

    private static String normalizeScriptId(String scriptId) {
        String normalizedId = scriptId == null ? "" : scriptId.trim();
        if (normalizedId.endsWith(".json")) {
            normalizedId = normalizedId.substring(0, normalizedId.length() - ".json".length());
        }
        if (normalizedId.isBlank() || !normalizedId.toLowerCase(Locale.ROOT).matches("[a-z0-9_./-]+")) {
            return null;
        }
        return normalizedId;
    }

    private static Path scriptPath(String normalizedId) {
        Path root = scriptsRoot().normalize();
        Path path = root.resolve(normalizedId + ".json").normalize();
        return path.startsWith(root) ? path : null;
    }

    private static Path legacyScriptPath(String normalizedId) {
        Path root = ConfigLoader.configRoot().normalize();
        Path path = root.resolve(normalizedId + ".json").normalize();
        return path.startsWith(root) ? path : null;
    }

    private static boolean looksLikeCommandScript(Path path) {
        try {
            String raw = Files.readString(path, StandardCharsets.UTF_8);
            return raw.contains("\"commands\"");
        } catch (IOException e) {
            GuardianMod.LOGGER.warn("Failed to inspect guardian script candidate {}", path, e);
            return false;
        }
    }

    private static JsonArray readCommands(JsonObject script) {
        return script.getAsJsonArray("commands");
    }

    private static JsonObject parseLenientCommandScript(String raw, String scriptId) {
        JsonArray commands = new JsonArray();
        boolean inCommands = false;

        for (String line : raw.split("\\R")) {
            String trimmed = line.trim();
            if (!inCommands) {
                if (trimmed.contains("\"commands\"") && trimmed.contains("[")) {
                    inCommands = true;
                }
                continue;
            }

            if (trimmed.startsWith("]")) {
                break;
            }
            if (trimmed.isBlank()) {
                continue;
            }
            if (trimmed.endsWith(",")) {
                trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
            }

            int firstQuote = trimmed.indexOf('"');
            int lastQuote = trimmed.lastIndexOf('"');
            if (firstQuote >= 0 && lastQuote > firstQuote) {
                commands.add(trimmed.substring(firstQuote + 1, lastQuote)
                        .replace("\\\"", "\"")
                        .replace("\\\\", "\\"));
            } else {
                commands.add(trimmed);
            }
        }

        if (commands.isEmpty()) {
            GuardianMod.LOGGER.warn("Failed to leniently parse commands for guardian script {}", scriptId);
            return null;
        }

        JsonObject object = new JsonObject();
        object.add("commands", commands);
        GuardianMod.LOGGER.warn("Guardian script {} used lenient command parsing; escape JSON quotes or keep one command per line.", scriptId);
        return object;
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

    private static int runCommands(CommandSourceStack source, JsonArray commands, Map<String, String> variables) {
        int executed = 0;
        for (JsonElement element : commands) {
            CommandEntry entry = readCommandEntry(element);
            if (entry == null) {
                continue;
            }

            if (entry.delayTicks > 0) {
                SCHEDULED_COMMANDS.add(new ScheduledCommand(source, applyVariables(entry.command, variables), entry.delayTicks));
                executed++;
            } else if (executeCommand(source, applyVariables(entry.command, variables))) {
                executed++;
            }
        }
        return executed;
    }

    private static String applyVariables(String command, Map<String, String> variables) {
        String result = command;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            String value = entry.getValue() == null ? "" : entry.getValue();
            result = result.replace("[" + entry.getKey() + "]", value)
                    .replace("{" + entry.getKey() + "}", value)
                    .replace("%" + entry.getKey() + "%", value);
        }
        return result;
    }

    private static CommandEntry readCommandEntry(JsonElement element) {
        String command = "";
        int delayTicks = 0;

        if (element.isJsonPrimitive()) {
            command = element.getAsString();
        } else if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.has("command") && object.get("command").isJsonPrimitive()) {
                command = object.get("command").getAsString();
            }
            if (object.has("delay_ticks") && object.get("delay_ticks").isJsonPrimitive()) {
                delayTicks = Math.max(0, object.get("delay_ticks").getAsInt());
            } else if (object.has("delay_seconds") && object.get("delay_seconds").isJsonPrimitive()) {
                delayTicks = Math.max(0, Math.round(object.get("delay_seconds").getAsFloat() * 20.0F));
            }
        } else {
            GuardianMod.LOGGER.warn("Ignoring non-string guardian script command: {}", element);
            return null;
        }

        command = command.trim();
        if (command.isEmpty()) {
            return null;
        }
        if (command.startsWith("/")) {
            command = command.substring(1);
        }
        return new CommandEntry(command, delayTicks);
    }

    private static boolean executeCommand(CommandSourceStack source, String command) {
        try {
            source.getServer().getCommands().performPrefixedCommand(source, command);
            return true;
        } catch (RuntimeException e) {
            GuardianMod.LOGGER.error("Guardian script command failed: {}", command, e);
            return false;
        }
    }

    private record CommandEntry(String command, int delayTicks) {
    }

    private static final class ScheduledCommand {
        private final CommandSourceStack source;
        private final String command;
        private int delayTicks;

        private ScheduledCommand(CommandSourceStack source, String command, int delayTicks) {
            this.source = source;
            this.command = command;
            this.delayTicks = delayTicks;
        }
    }
}
