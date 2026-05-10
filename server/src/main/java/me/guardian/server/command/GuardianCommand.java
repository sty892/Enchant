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
import me.guardian.block.KeyholeBlock;
import me.guardian.config.ConfigLoader;
import me.guardian.config.ConfigManager;
import me.guardian.entity.GenericBossEntity;
import me.guardian.entity.NetherGuardianEntity;
import me.guardian.entity.OverworldGuardianEntity;
import me.guardian.event.GuardianEventExecutor;
import me.guardian.server.altar.AltarRitualManager;
import me.guardian.server.boss.BossEventManager;
import me.guardian.server.event.ScriptRunner;
import me.guardian.server.state.GuardianWorldState;
import me.guardian.server.structure.StructureSpawner;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
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
import net.minecraft.world.level.block.state.BlockState;

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
                        .then(Commands.argument("stage", IntegerArgumentType.integer(0, 2))
                                .suggests((context, builder) -> suggest(new String[]{"0", "1", "2"}, builder))
                                .executes(context -> setStage(context.getSource(), IntegerArgumentType.getInteger(context, "stage")))))
                .then(Commands.literal("keyholes")
                        .then(Commands.literal("reset")
                                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 64))
                                        .executes(context -> resetKeyholes(context.getSource(), IntegerArgumentType.getInteger(context, "radius")))))
                        .then(Commands.literal("state")
                                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 64))
                                        .executes(context -> printKeyholeState(context.getSource(), IntegerArgumentType.getInteger(context, "radius"))))))
                .then(Commands.literal("event")
                        .then(Commands.literal("run")
                                .then(Commands.argument("script_id", StringArgumentType.word())
                                        .suggests((context, builder) -> suggestScripts(builder))
                                        .executes(context -> ScriptRunner.runScript(context.getSource(), StringArgumentType.getString(context, "script_id")))))
                        .then(Commands.literal("test")
                                .then(Commands.literal("key_insert")
                                        .then(Commands.argument("slot", IntegerArgumentType.integer(1, 8))
                                                .suggests((context, builder) -> suggest(new String[]{"1", "2", "3", "4", "5", "6", "7", "8"}, builder))
                                                .executes(context -> testKeyInsertEvent(context.getSource(), IntegerArgumentType.getInteger(context, "slot")))))
                                .then(Commands.literal("all_keys")
                                        .executes(context -> testAllKeysEvent(context.getSource())))))
                .then(Commands.literal("altar")
                        .then(Commands.literal("stats")
                                .executes(context -> printAltarStats(context.getSource())))
                        .then(Commands.literal("reset")
                                .executes(context -> resetAltarStats(context.getSource()))))
                .then(Commands.literal("structure")
                        .then(Commands.literal("place")
                                .then(Commands.argument("structure_id", IdentifierArgument.id())
                                        .executes(context -> placeStructure(context.getSource(), IdentifierArgument.getId(context, "structure_id").toString(), null))
                                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                .executes(context -> placeStructure(
                                                        context.getSource(),
                                                        IdentifierArgument.getId(context, "structure_id").toString(),
                                                        BlockPosArgument.getLoadedBlockPos(context, "pos")))))))
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

    private static int resetKeyholes(CommandSourceStack source, int radius) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = source.getLevel();
        int changed = 0;
        for (BlockPos pos : BlockPos.betweenClosed(player.blockPosition().offset(-radius, -radius, -radius), player.blockPosition().offset(radius, radius, radius))) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof KeyholeBlock && state.hasProperty(KeyholeBlock.FILLED) && state.getValue(KeyholeBlock.FILLED)) {
                level.setBlock(pos, state.setValue(KeyholeBlock.FILLED, false), 3);
                changed++;
            }
        }
        int result = changed;
        source.sendSuccess(() -> Component.literal("Reset keyholes in radius " + radius + ": " + result), true);
        return changed;
    }

    private static int printKeyholeState(CommandSourceStack source, int radius) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = source.getLevel();
        boolean[] found = new boolean[9];
        boolean[] filled = new boolean[9];
        for (BlockPos pos : BlockPos.betweenClosed(player.blockPosition().offset(-radius, -radius, -radius), player.blockPosition().offset(radius, radius, radius))) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof KeyholeBlock keyhole && state.hasProperty(KeyholeBlock.FILLED)) {
                int slot = keyhole.slot();
                found[slot] = true;
                filled[slot] |= state.getValue(KeyholeBlock.FILLED);
            }
        }
        for (int slot = 1; slot <= 8; slot++) {
            int current = slot;
            source.sendSuccess(() -> Component.literal("keyhole_" + current + ": " + (found[current] && filled[current] ? "filled" : "empty")), false);
        }
        return 1;
    }

    private static int testKeyInsertEvent(CommandSourceStack source, int slot) throws CommandSyntaxException {
        JsonObject config = readKeysConfig();
        JsonObject event = findKeyInsertEvent(config, slot);
        if (event == null) {
            source.sendFailure(Component.literal("No on_insert event for key " + slot));
            return 0;
        }
        ServerPlayer player = source.getPlayerOrException();
        GuardianEventExecutor.execute(source.getLevel(), event, player.blockPosition(), player);
        source.sendSuccess(() -> Component.literal("Executed on_insert for key " + slot), true);
        return 1;
    }

    private static int testAllKeysEvent(CommandSourceStack source) throws CommandSyntaxException {
        JsonObject config = readKeysConfig();
        if (!config.has("on_all_inserted") || !config.get("on_all_inserted").isJsonObject()) {
            source.sendFailure(Component.literal("No on_all_inserted event configured"));
            return 0;
        }
        ServerPlayer player = source.getPlayerOrException();
        GuardianEventExecutor.execute(source.getLevel(), config.getAsJsonObject("on_all_inserted"), player.blockPosition(), player);
        source.sendSuccess(() -> Component.literal("Executed on_all_inserted"), true);
        return 1;
    }

    private static int printAltarStats(CommandSourceStack source) throws CommandSyntaxException {
        AltarRitualManager.sendStats(source.getPlayerOrException());
        return 1;
    }

    private static int resetAltarStats(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        AltarRitualManager.resetPlayerUpgrades(player);
        source.sendSuccess(() -> Component.literal("Guardian altar upgrades reset"), true);
        return 1;
    }

    private static int placeStructure(CommandSourceStack source, String structureId, BlockPos requestedPos) {
        BlockPos pos = requestedPos == null ? BlockPos.containing(source.getPosition()) : requestedPos;
        boolean placed = StructureSpawner.place(source.getLevel(), pos, structureId);
        if (!placed) {
            source.sendFailure(Component.literal("Failed to place guardian structure: " + structureId));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Placed guardian structure " + structureId + " at " + pos.toShortString()), true);
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

    private static JsonObject readKeysConfig() {
        try {
            JsonObject config = GSON.fromJson(ConfigLoader.read("keys_config.json"), JsonObject.class);
            return config == null ? new JsonObject() : config;
        } catch (IOException | RuntimeException e) {
            GuardianMod.LOGGER.warn("Failed to read keys_config.json", e);
            return new JsonObject();
        }
    }

    private static JsonObject findKeyInsertEvent(JsonObject config, int slot) {
        if (!config.has("keys") || !config.get("keys").isJsonArray()) {
            return null;
        }
        for (JsonElement element : config.getAsJsonArray("keys")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject key = element.getAsJsonObject();
            if (configuredSlot(key) == slot && key.has("on_insert") && key.get("on_insert").isJsonObject()) {
                return key.getAsJsonObject("on_insert");
            }
        }
        return null;
    }

    private static int configuredSlot(JsonObject key) {
        if (key.has("slot")) {
            return key.get("slot").getAsInt();
        }
        if (key.has("keyhole_slot")) {
            return key.get("keyhole_slot").getAsInt();
        }
        if (key.has("keyhole_stage")) {
            return key.get("keyhole_stage").getAsInt();
        }
        if (key.has("keyhole_id")) {
            String value = key.get("keyhole_id").getAsString();
            int separator = value.lastIndexOf('_');
            if (separator >= 0 && separator + 1 < value.length()) {
                try {
                    return Integer.parseInt(value.substring(separator + 1));
                } catch (NumberFormatException ignored) {
                    return -1;
                }
            }
        }
        return -1;
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

    private static CompletableFuture<Suggestions> suggestScripts(SuggestionsBuilder builder) {
        String remaining = builder.getRemainingLowerCase();
        for (String scriptId : ScriptRunner.listScriptIds()) {
            if (scriptId.toLowerCase(java.util.Locale.ROOT).startsWith(remaining)) {
                builder.suggest(scriptId);
            }
        }
        return builder.buildFuture();
    }
}
