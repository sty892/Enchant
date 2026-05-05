package me.guardian.server.command;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import me.guardian.GuardianMod;
import me.guardian.config.ConfigLoader;
import me.guardian.config.ConfigManager;
import me.guardian.entity.GenericBossEntity;
import me.guardian.entity.NetherGuardianEntity;
import me.guardian.entity.OverworldGuardianEntity;
import me.guardian.server.boss.BossEventManager;
import me.guardian.server.state.GuardianWorldState;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class GuardianCommand {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String GUARDIAN_CONFIG = "guardian_config.json";
    private static final String[] BOSS_ID_SUGGESTIONS = {
            "guardian_mod:boss_overworld",
            "guardian_mod:boss_nether",
            "guardian_mod:boss_generic",
            "boss_overworld",
            "boss_nether",
            "boss_generic"
    };

    private GuardianCommand() {
    }

    public static void initialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("guardian")
                .requires(GuardianCommand::canUse)
                .then(Commands.literal("boss")
                        .then(Commands.literal("spawn")
                                .then(Commands.argument("boss_id", StringArgumentType.string())
                                        .suggests((context, builder) -> suggest(BOSS_ID_SUGGESTIONS, builder))
                                        .executes(context -> spawnBoss(context.getSource(), StringArgumentType.getString(context, "boss_id")))))
                        .then(Commands.literal("kill")
                                .then(Commands.literal("all")
                                        .executes(context -> killAllBosses(context.getSource())))))
                .then(Commands.literal("whitelist")
                        .then(Commands.literal("add")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .suggests((context, builder) -> suggestOnlinePlayers(context.getSource(), builder))
                                        .executes(context -> addWhitelist(context.getSource(), StringArgumentType.getString(context, "player")))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .suggests((context, builder) -> suggestOnlinePlayers(context.getSource(), builder))
                                        .executes(context -> removeWhitelist(context.getSource(), StringArgumentType.getString(context, "player")))))
                        .then(Commands.literal("list")
                                .executes(context -> listWhitelist(context.getSource()))))
                .then(Commands.literal("state")
                        .executes(context -> printState(context.getSource())))
                .then(Commands.literal("reset")
                        .executes(context -> resetState(context.getSource())))
                .then(Commands.literal("stage")
                        .then(Commands.argument("stage", IntegerArgumentType.integer(1, 2))
                                .suggests((context, builder) -> suggest(new String[]{"1", "2"}, builder))
                                .executes(context -> setStage(context.getSource(), IntegerArgumentType.getInteger(context, "stage")))))
                .then(Commands.literal("reload")
                        .executes(context -> reload(context.getSource()))));
    }

    private static boolean canUse(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        return player == null || source.getServer().getPlayerList().isOp(player.nameAndId());
    }

    private static int spawnBoss(CommandSourceStack source, String bossId) throws CommandSyntaxException {
        Identifier id = parseGuardianBossId(bossId);
        if (id == null || !GuardianMod.MOD_ID.equals(id.getNamespace()) || !id.getPath().startsWith("boss_")) {
            source.sendFailure(Component.literal("Unknown guardian boss id: " + bossId));
            return 0;
        }

        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
        if (type == null) {
            source.sendFailure(Component.literal("Unknown guardian boss id: " + bossId));
            return 0;
        }

        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = source.getLevel();
        Entity entity = type.create(level, EntitySpawnReason.COMMAND);
        if (entity == null) {
            source.sendFailure(Component.literal("Failed to create boss: " + bossId));
            return 0;
        }

        entity.setPos(player.getX(), player.getY(), player.getZ());
        level.addFreshEntity(entity);
        source.sendSuccess(() -> Component.literal("Spawned " + bossId), true);
        return 1;
    }

    private static Identifier parseGuardianBossId(String bossId) {
        String id = bossId.trim();
        if (id.indexOf(':') < 0) {
            return Identifier.fromNamespaceAndPath(GuardianMod.MOD_ID, id);
        }
        return Identifier.tryParse(id);
    }

    private static int killAllBosses(CommandSourceStack source) {
        List<Entity> bosses = new ArrayList<>();
        for (ServerLevel level : source.getServer().getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (isGuardianBoss(entity)) {
                    bosses.add(entity);
                }
            }
        }

        int killed = 0;
        for (Entity entity : bosses) {
            if (entity.level() instanceof ServerLevel level) {
                if (entity instanceof LivingEntity living) {
                    living.kill(level);
                } else {
                    entity.kill(level);
                }
                killed++;
            }
        }
        int result = killed;
        source.sendSuccess(() -> Component.literal("Killed guardian bosses: " + result), true);
        return killed;
    }

    private static boolean isGuardianBoss(Entity entity) {
        return entity instanceof OverworldGuardianEntity
                || entity instanceof NetherGuardianEntity
                || entity instanceof GenericBossEntity;
    }

    private static int printState(CommandSourceStack source) {
        GuardianWorldState state = GuardianWorldState.get(source.getLevel());
        source.sendSuccess(() -> Component.literal("GuardianWorldState overworldBossDefeated=" + state.overworldBossDefeated
                + ", netherBossDefeated=" + state.netherBossDefeated), false);
        return 1;
    }

    private static int resetState(CommandSourceStack source) {
        GuardianWorldState state = GuardianWorldState.get(source.getLevel());
        state.overworldBossDefeated = false;
        state.netherBossDefeated = false;
        state.setDirty();
        source.sendSuccess(() -> Component.literal("GuardianWorldState reset"), true);
        return 1;
    }

    private static int setStage(CommandSourceStack source, int stage) {
        GuardianWorldState state = GuardianWorldState.get(source.getLevel());
        state.overworldBossDefeated = stage >= 1;
        state.netherBossDefeated = stage >= 2;
        state.setDirty();
        source.sendSuccess(() -> Component.literal("Guardian stage forced to " + stage), true);
        return 1;
    }

    private static int reload(CommandSourceStack source) {
        ConfigManager.initialize();
        BossEventManager.reload();
        source.getServer().reloadResources(source.getServer().getPackRepository().getSelectedIds());
        source.sendSuccess(() -> Component.literal("Guardian configs reloaded"), true);
        return 1;
    }

    private static int addWhitelist(CommandSourceStack source, String playerNameOrUuid) {
        UUID uuid = resolveUuid(source.getServer(), playerNameOrUuid);
        if (uuid == null) {
            source.sendFailure(Component.literal("Player must be online or specified by UUID: " + playerNameOrUuid));
            return 0;
        }

        JsonObject config = readGuardianConfig();
        Set<UUID> whitelist = readWhitelist(config);
        whitelist.add(uuid);
        writeWhitelist(config, whitelist);
        source.sendSuccess(() -> Component.literal("Added diamond whitelist UUID: " + uuid), true);
        return 1;
    }

    private static int removeWhitelist(CommandSourceStack source, String playerNameOrUuid) {
        UUID uuid = resolveUuid(source.getServer(), playerNameOrUuid);
        if (uuid == null) {
            source.sendFailure(Component.literal("Player must be online or specified by UUID: " + playerNameOrUuid));
            return 0;
        }

        JsonObject config = readGuardianConfig();
        Set<UUID> whitelist = readWhitelist(config);
        boolean removed = whitelist.remove(uuid);
        writeWhitelist(config, whitelist);
        source.sendSuccess(() -> Component.literal((removed ? "Removed" : "UUID was not present") + " diamond whitelist UUID: " + uuid), true);
        return removed ? 1 : 0;
    }

    private static int listWhitelist(CommandSourceStack source) {
        Set<UUID> whitelist = readWhitelist(readGuardianConfig());
        source.sendSuccess(() -> Component.literal("Diamond whitelist UUIDs: " + whitelist), false);
        return whitelist.size();
    }

    private static UUID resolveUuid(MinecraftServer server, String playerNameOrUuid) {
        try {
            return UUID.fromString(playerNameOrUuid);
        } catch (IllegalArgumentException ignored) {
            ServerPlayer player = server.getPlayerList().getPlayerByName(playerNameOrUuid);
            return player == null ? null : player.getUUID();
        }
    }

    private static JsonObject readGuardianConfig() {
        try {
            return GSON.fromJson(ConfigLoader.read(GUARDIAN_CONFIG), JsonObject.class);
        } catch (IOException | RuntimeException e) {
            GuardianMod.LOGGER.warn("Failed to read guardian_config.json, recreating defaults", e);
            JsonObject object = new JsonObject();
            object.addProperty("diamond_restriction_enabled", true);
            object.add("op_diamond_whitelist", new JsonArray());
            return object;
        }
    }

    private static Set<UUID> readWhitelist(JsonObject config) {
        Set<UUID> whitelist = new HashSet<>();
        JsonArray entries = config.has("op_diamond_whitelist") && config.get("op_diamond_whitelist").isJsonArray()
                ? config.getAsJsonArray("op_diamond_whitelist")
                : new JsonArray();
        for (JsonElement entry : entries) {
            try {
                whitelist.add(UUID.fromString(entry.getAsString()));
            } catch (IllegalArgumentException ignored) {
                GuardianMod.LOGGER.warn("Ignoring invalid guardian_config whitelist entry: {}", entry);
            }
        }
        return whitelist;
    }

    private static void writeWhitelist(JsonObject config, Set<UUID> whitelist) {
        JsonArray entries = new JsonArray();
        whitelist.stream().map(UUID::toString).sorted().forEach(entries::add);
        config.add("op_diamond_whitelist", entries);
        try {
            ConfigLoader.write(GUARDIAN_CONFIG, GSON.toJson(config));
        } catch (IOException e) {
            GuardianMod.LOGGER.error("Failed to write guardian_config.json", e);
        }
    }

    private static CompletableFuture<Suggestions> suggest(String[] values, SuggestionsBuilder builder) {
        String remaining = builder.getRemainingLowerCase();
        for (String value : values) {
            if (value.toLowerCase(java.util.Locale.ROOT).startsWith(remaining)) {
                builder.suggest(value);
            }
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestOnlinePlayers(CommandSourceStack source, SuggestionsBuilder builder) {
        String remaining = builder.getRemainingLowerCase();
        for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
            String name = player.getScoreboardName();
            if (name.toLowerCase(java.util.Locale.ROOT).startsWith(remaining)) {
                builder.suggest(name);
            }
        }
        return builder.buildFuture();
    }
}
