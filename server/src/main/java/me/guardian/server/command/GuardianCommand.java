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
import me.guardian.entity.CameraMarkerEntity;
import me.guardian.entity.GenericBossEntity;
import me.guardian.entity.ModEntities;
import me.guardian.entity.NetherGuardianEntity;
import me.guardian.entity.OverworldGuardianEntity;
import me.guardian.event.GuardianEventExecutor;
import me.guardian.server.altar.AltarRitualManager;
import me.guardian.server.boss.BossEventManager;
import me.guardian.server.cutscene.CutsceneManager;
import net.minecraft.commands.arguments.EntityArgument;
import me.guardian.server.event.GuardianJsonEventActions;
import me.guardian.server.event.KeyFoundEventHandler;
import me.guardian.server.event.ScriptRunner;
import me.guardian.server.restriction.DiamondRestrictionHandler;
import me.guardian.server.state.GuardianWorldState;
import me.guardian.server.structure.StructureSpawner;
import me.guardian.server.trigger.TriggerAreaManager;
import me.guardian.server.trigger.TriggerAreaState;
import me.guardian.trigger.TriggerArea;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
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
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
    private static final String[] ATTACK_ID_SUGGESTIONS = {
            "anti_shield",
            "counter_leap",
            "fissure",
            "rockfall",
            "shockwave",
            "melee",
            "molten_fissure",
            "meteor_rain",
            "whip_grab",
            "minion_aegis",
            "soul_vortex",
            "death_beams"
    };
    private static final String[] STRUCTURE_ID_SUGGESTIONS = {
            "altar",
            "boss_overworld_arena",
            "boss_nether_arena"
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
                                        .executes(context -> spawnBoss(context.getSource(), StringArgumentType.getString(context, "boss_id"), null))
                                        .then(Commands.argument("pos", Vec3Argument.vec3())
                                                .executes(context -> spawnBoss(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "boss_id"),
                                                        Vec3Argument.getVec3(context, "pos"))))))
                        .then(Commands.literal("kill")
                                .then(Commands.literal("all")
                                        .executes(context -> killAllBosses(context.getSource()))))
                        .then(Commands.literal("attack")
                                .then(Commands.argument("attack_id", StringArgumentType.word())
                                        .suggests((context, builder) -> suggest(ATTACK_ID_SUGGESTIONS, builder))
                                        .executes(context -> forceBossAttack(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "attack_id"))))))
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
                .then(Commands.literal("keys")
                        .then(Commands.literal("found")
                                .then(Commands.literal("reset")
                                        .executes(context -> resetFoundKeys(context.getSource()))))
                        .then(Commands.literal("whitelist")
                                .then(Commands.literal("add")
                                        .then(Commands.argument("player", StringArgumentType.word())
                                                .suggests((context, builder) -> suggestOnlinePlayers(context.getSource(), builder))
                                                .executes(context -> addKeyWhitelist(context.getSource(), StringArgumentType.getString(context, "player")))))
                                .then(Commands.literal("remove")
                                        .then(Commands.argument("player", StringArgumentType.word())
                                                .suggests((context, builder) -> suggestOnlinePlayers(context.getSource(), builder))
                                                .executes(context -> removeKeyWhitelist(context.getSource(), StringArgumentType.getString(context, "player")))))
                                .then(Commands.literal("list")
                                        .executes(context -> listKeyWhitelist(context.getSource())))))
                .then(Commands.literal("trigger")
                        .then(Commands.literal("delete")
                                .then(Commands.literal("nearest")
                                        .executes(context -> deleteNearestTrigger(context.getSource())))
                                .then(Commands.argument("id", StringArgumentType.string())
                                        .suggests((context, builder) -> suggestTriggerIds(context.getSource(), builder))
                                        .executes(context -> deleteTrigger(context.getSource(), StringArgumentType.getString(context, "id"))))))
                .then(Commands.literal("event")
                        .then(Commands.literal("run")
                                .then(Commands.argument("script_id", StringArgumentType.word())
                                        .suggests((context, builder) -> suggestScripts(builder))
                                        .executes(context -> ScriptRunner.runScript(context.getSource(), StringArgumentType.getString(context, "script_id")))))
                        .then(Commands.literal("run_json")
                                .then(Commands.argument("event_file", StringArgumentType.string())
                                        .suggests((context, builder) -> suggestEventFiles(builder))
                                        .executes(context -> runJsonEvent(context.getSource(), StringArgumentType.getString(context, "event_file")))))
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
                                .then(Commands.argument("structure_id", StringArgumentType.string())
                                        .suggests((context, builder) -> suggest(STRUCTURE_ID_SUGGESTIONS, builder))
                                        .executes(context -> placeStructure(context.getSource(), StringArgumentType.getString(context, "structure_id"), null))
                                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                .executes(context -> placeStructure(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "structure_id"),
                                                        BlockPosArgument.getLoadedBlockPos(context, "pos")))))))
                .then(Commands.literal("reload")
                        .executes(context -> reload(context.getSource()))));

        dispatcher.register(Commands.literal("camera")
                .requires(GuardianCommand::canUse)
                .then(Commands.literal("play")
                        .then(Commands.argument("cutscene_id", StringArgumentType.string())
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> playCutscene(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "cutscene_id"),
                                                EntityArgument.getPlayer(context, "player")
                                        )))))
                .then(Commands.literal("register")
                        .then(Commands.argument("cutscene_id", StringArgumentType.string())
                                .executes(context -> registerCamera(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "cutscene_id")
                                )))));
    }

    private static boolean canUse(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        return player == null || source.getServer().getPlayerList().isOp(player.nameAndId());
    }

    private static int playCutscene(CommandSourceStack source, String cutsceneId, ServerPlayer player) {
        CutsceneManager.startCutscene(player, cutsceneId);
        source.sendSuccess(() -> Component.literal("§aStarting cutscene " + cutsceneId + " for player " + player.getScoreboardName()), true);
        return 1;
    }

    private static int registerCamera(CommandSourceStack source, String cutsceneId) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = (ServerLevel) player.level();

        int nextIndex = 1;
        for (ServerLevel lvl : source.getServer().getAllLevels()) {
            for (Entity entity : lvl.getAllEntities()) {
                if (entity instanceof CameraMarkerEntity marker) {
                    if (cutsceneId.equals(marker.getCutsceneId())) {
                        if (marker.getIndex() >= nextIndex) {
                            nextIndex = marker.getIndex() + 1;
                        }
                    }
                }
            }
        }

        CameraMarkerEntity marker = new CameraMarkerEntity(ModEntities.CAMERA_MARKER, level);
        marker.setCutsceneId(cutsceneId);
        marker.setIndex(nextIndex);
        marker.setDuration(5.0f);
        marker.setPos(player.getX(), player.getEyeY(), player.getZ());
        marker.setYRot(player.getYRot());
        marker.setXRot(player.getXRot());
        level.addFreshEntity(marker);

        int index = nextIndex;
        source.sendSuccess(() -> Component.literal("§aRegistered camera marker for cutscene " + cutsceneId + " at index " + index), true);
        return 1;
    }

    private static int spawnBoss(CommandSourceStack source, String bossId, Vec3 requestedPos) throws CommandSyntaxException {
        Identifier id = parseGuardianBossId(bossId);
        if (id == null || !GuardianMod.MOD_ID.equals(id.getNamespace()) || !id.getPath().startsWith("boss_")) {
            source.sendFailure(Component.translatable("command.guardian_mod.unknown_boss", bossId));
            return 0;
        }

        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
        if (type == null) {
            source.sendFailure(Component.translatable("command.guardian_mod.unknown_boss", bossId));
            return 0;
        }

        ServerLevel level = source.getLevel();
        Entity entity = type.create(level, EntitySpawnReason.COMMAND);
        if (entity == null) {
            source.sendFailure(Component.translatable("command.guardian_mod.boss_create_failed", bossId));
            return 0;
        }

        Vec3 pos = requestedPos == null ? source.getPlayerOrException().position() : requestedPos;
        entity.setPos(pos.x, pos.y, pos.z);
        level.addFreshEntity(entity);
        source.sendSuccess(() -> Component.translatable("command.guardian_mod.boss_spawned", bossId), true);
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
        source.sendSuccess(() -> Component.translatable("command.guardian_mod.bosses_killed", result), true);
        return killed;
    }

    private static int forceBossAttack(CommandSourceStack source, String attackId) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = source.getLevel();
        LivingEntity boss = nearestActiveBoss(level, player.position());
        if (boss == null) {
            source.sendFailure(Component.translatable("command.guardian_mod.boss_attack.no_boss"));
            return 0;
        }

        boolean started = boss instanceof OverworldGuardianEntity overworld
                ? overworld.forceAttack(level, attackId)
                : boss instanceof NetherGuardianEntity nether && nether.forceAttack(level, attackId);
        if (!started) {
            source.sendFailure(Component.translatable("command.guardian_mod.boss_attack.failed", attackId));
            return 0;
        }

        source.sendSuccess(() -> Component.translatable("command.guardian_mod.boss_attack.started", attackId), true);
        return 1;
    }

    private static LivingEntity nearestActiveBoss(ServerLevel level, Vec3 origin) {
        LivingEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Entity entity : level.getAllEntities()) {
            if (!(entity instanceof LivingEntity living) || !living.isAlive() || !isGuardianBoss(entity)) {
                continue;
            }
            double distance = living.position().distanceToSqr(origin);
            if (distance < nearestDistance) {
                nearest = living;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private static boolean isGuardianBoss(Entity entity) {
        return entity instanceof OverworldGuardianEntity
                || entity instanceof NetherGuardianEntity
                || entity instanceof GenericBossEntity;
    }

    private static int printState(CommandSourceStack source) {
        GuardianWorldState state = GuardianWorldState.get(source.getLevel());
        source.sendSuccess(() -> Component.translatable("command.guardian_mod.state",
                state.isOverworldBossDefeated(), state.isNetherBossDefeated()), false);
        return 1;
    }

    private static int resetState(CommandSourceStack source) {
        GuardianWorldState state = GuardianWorldState.get(source.getLevel());
        state.setOverworldBossDefeated(false);
        state.setNetherBossDefeated(false);
        source.sendSuccess(() -> Component.translatable("command.guardian_mod.state_reset"), true);
        return 1;
    }

    private static int setStage(CommandSourceStack source, int stage) {
        GuardianWorldState state = GuardianWorldState.get(source.getLevel());
        state.setOverworldBossDefeated(stage >= 1);
        state.setNetherBossDefeated(stage >= 2);
        source.sendSuccess(() -> Component.translatable("command.guardian_mod.stage_forced", stage), true);
        return 1;
    }

    private static int reload(CommandSourceStack source) {
        ConfigManager.initialize();
        BossEventManager.reload();
        AltarRitualManager.invalidateConfigCache();
        DiamondRestrictionHandler.reloadConfig();
        source.getServer().reloadResources(source.getServer().getPackRepository().getSelectedIds());
        source.sendSuccess(() -> Component.translatable("command.guardian_mod.configs_reloaded"), true);
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
        source.sendSuccess(() -> Component.translatable("command.guardian_mod.keyholes_reset", radius, result), true);
        return changed;
    }

    private static int resetFoundKeys(CommandSourceStack source) {
        int count = KeyFoundEventHandler.resetFoundKeys(source.getServer());
        source.sendSuccess(() -> Component.translatable("command.guardian_mod.found_keys_reset", count), true);
        return count;
    }

    private static int deleteTrigger(CommandSourceStack source, String idRaw) {
        UUID id;
        try {
            id = UUID.fromString(idRaw);
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.translatable("command.guardian_mod.trigger_invalid_id", idRaw));
            return 0;
        }
        boolean removed = TriggerAreaManager.deleteArea(source.getServer(), id);
        if (!removed) {
            source.sendFailure(Component.translatable("command.guardian_mod.trigger_not_found", id.toString()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.guardian_mod.trigger_deleted", id.toString()), true);
        return 1;
    }

    private static int deleteNearestTrigger(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        String dimension = player.level().dimension().identifier().toString();
        TriggerArea nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (TriggerArea area : TriggerAreaState.get(player.level()).areas) {
            if (!area.dimension.equals(dimension)) {
                continue;
            }
            double distance = areaCenterDistanceSqr(area, player.getX(), player.getY(), player.getZ());
            if (distance < nearestDistance) {
                nearest = area;
                nearestDistance = distance;
            }
        }
        if (nearest == null) {
            source.sendFailure(Component.translatable("command.guardian_mod.trigger_not_found_dimension"));
            return 0;
        }
        UUID id = nearest.id;
        if (!TriggerAreaManager.deleteArea(source.getServer(), id)) {
            source.sendFailure(Component.translatable("command.guardian_mod.trigger_not_found", id.toString()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.guardian_mod.trigger_deleted_nearest", id.toString()), true);
        return 1;
    }

    private static double areaCenterDistanceSqr(TriggerArea area, double x, double y, double z) {
        double centerX = (area.min.getX() + area.max.getX() + 1.0D) * 0.5D;
        double centerY = (area.min.getY() + area.max.getY() + 1.0D) * 0.5D;
        double centerZ = (area.min.getZ() + area.max.getZ() + 1.0D) * 0.5D;
        double dx = centerX - x;
        double dy = centerY - y;
        double dz = centerZ - z;
        return dx * dx + dy * dy + dz * dz;
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
            source.sendSuccess(() -> Component.translatable("command.guardian_mod.keyhole_state", current,
                    Component.translatable(found[current] && filled[current]
                            ? "command.guardian_mod.keyhole_state.filled"
                            : "command.guardian_mod.keyhole_state.empty")), false);
        }
        return 1;
    }

    private static int testKeyInsertEvent(CommandSourceStack source, int slot) throws CommandSyntaxException {
        JsonObject config = readKeysConfig();
        JsonObject event = findKeyInsertEvent(config, slot);
        if (event == null) {
            source.sendFailure(Component.translatable("command.guardian_mod.no_key_insert_event", slot));
            return 0;
        }
        ServerPlayer player = source.getPlayerOrException();
        GuardianEventExecutor.execute(source.getLevel(), event, player.blockPosition(), player);
        source.sendSuccess(() -> Component.translatable("command.guardian_mod.key_insert_event_executed", slot), true);
        return 1;
    }

    private static int runJsonEvent(CommandSourceStack source, String eventFile) {
        JsonObject event = readEventFile(eventFile);
        if (event == null) {
            source.sendFailure(Component.translatable("command.guardian_mod.event_file_failed", eventFile));
            return 0;
        }

        BlockPos center = BlockPos.containing(source.getPosition());
        int scheduled = GuardianJsonEventActions.run(source.getServer(), source.getLevel(), center, event);
        source.sendSuccess(() -> Component.translatable("command.guardian_mod.event_actions_scheduled", scheduled), true);
        return scheduled > 0 ? 1 : 0;
    }

    private static int testAllKeysEvent(CommandSourceStack source) throws CommandSyntaxException {
        JsonObject config = readKeysConfig();
        if (!config.has("on_all_inserted") || !config.get("on_all_inserted").isJsonObject()) {
            source.sendFailure(Component.translatable("command.guardian_mod.no_all_inserted_event"));
            return 0;
        }
        ServerPlayer player = source.getPlayerOrException();
        GuardianEventExecutor.execute(source.getLevel(), config.getAsJsonObject("on_all_inserted"), player.blockPosition(), player);
        source.sendSuccess(() -> Component.translatable("command.guardian_mod.all_inserted_event_executed"), true);
        return 1;
    }

    private static int printAltarStats(CommandSourceStack source) throws CommandSyntaxException {
        AltarRitualManager.sendStats(source.getPlayerOrException());
        return 1;
    }

    private static int resetAltarStats(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        AltarRitualManager.resetPlayerUpgrades(player);
        source.sendSuccess(() -> Component.translatable("command.guardian_mod.altar_upgrades_reset"), true);
        return 1;
    }

    private static int placeStructure(CommandSourceStack source, String structureId, BlockPos requestedPos) {
        Identifier id = StructureSpawner.parseStructureId(structureId);
        if (id == null) {
            source.sendFailure(Component.translatable("command.guardian_mod.structure_invalid_id", structureId));
            return 0;
        }

        BlockPos pos = requestedPos == null ? BlockPos.containing(source.getPosition()) : requestedPos;
        boolean placed = StructureSpawner.place(source.getLevel(), pos, id.toString());
        if (!placed) {
            source.sendFailure(Component.translatable("command.guardian_mod.structure_place_failed", id.toString(), StructureSpawner.resourcePath(id)));
            return 0;
        }

        source.sendSuccess(() -> Component.translatable("command.guardian_mod.structure_placed", id.toString(), pos.toShortString()), true);
        return 1;
    }

    private static int addWhitelist(CommandSourceStack source, String playerNameOrUuid) {
        UUID uuid = resolveUuid(source.getServer(), playerNameOrUuid);
        if (uuid == null) {
            source.sendFailure(Component.translatable("command.guardian_mod.player_or_uuid_required", playerNameOrUuid));
            return 0;
        }

        JsonObject config = readGuardianConfig();
        Set<UUID> whitelist = readWhitelist(config);
        whitelist.add(uuid);
        writeWhitelist(config, whitelist);
        DiamondRestrictionHandler.reloadConfig();
        source.sendSuccess(() -> Component.translatable("command.guardian_mod.diamond_whitelist_added", uuid.toString()), true);
        return 1;
    }

    private static int addKeyWhitelist(CommandSourceStack source, String playerNameOrUuid) {
        UUID uuid = resolveUuid(source.getServer(), playerNameOrUuid);
        if (uuid == null) {
            source.sendFailure(Component.translatable("command.guardian_mod.player_or_uuid_required", playerNameOrUuid));
            return 0;
        }

        JsonObject config = readGuardianConfig();
        Set<UUID> whitelist = readUuidList(config, "key_whitelist");
        whitelist.add(uuid);
        writeUuidList(config, "key_whitelist", whitelist);
        source.sendSuccess(() -> Component.translatable("command.guardian_mod.key_whitelist_added", uuid.toString()), true);
        return 1;
    }

    private static int removeWhitelist(CommandSourceStack source, String playerNameOrUuid) {
        UUID uuid = resolveUuid(source.getServer(), playerNameOrUuid);
        if (uuid == null) {
            source.sendFailure(Component.translatable("command.guardian_mod.player_or_uuid_required", playerNameOrUuid));
            return 0;
        }

        JsonObject config = readGuardianConfig();
        Set<UUID> whitelist = readWhitelist(config);
        boolean removed = whitelist.remove(uuid);
        writeWhitelist(config, whitelist);
        DiamondRestrictionHandler.reloadConfig();
        source.sendSuccess(() -> Component.translatable(removed
                ? "command.guardian_mod.diamond_whitelist_removed"
                : "command.guardian_mod.diamond_whitelist_missing", uuid.toString()), true);
        return removed ? 1 : 0;
    }

    private static int removeKeyWhitelist(CommandSourceStack source, String playerNameOrUuid) {
        UUID uuid = resolveUuid(source.getServer(), playerNameOrUuid);
        if (uuid == null) {
            source.sendFailure(Component.translatable("command.guardian_mod.player_or_uuid_required", playerNameOrUuid));
            return 0;
        }

        JsonObject config = readGuardianConfig();
        Set<UUID> whitelist = readUuidList(config, "key_whitelist");
        boolean removed = whitelist.remove(uuid);
        writeUuidList(config, "key_whitelist", whitelist);
        source.sendSuccess(() -> Component.translatable(removed
                ? "command.guardian_mod.key_whitelist_removed"
                : "command.guardian_mod.key_whitelist_missing", uuid.toString()), true);
        return removed ? 1 : 0;
    }

    private static int listWhitelist(CommandSourceStack source) {
        Set<UUID> whitelist = readWhitelist(readGuardianConfig());
        source.sendSuccess(() -> Component.translatable("command.guardian_mod.diamond_whitelist_list", uuidListString(whitelist)), false);
        return whitelist.size();
    }

    private static int listKeyWhitelist(CommandSourceStack source) {
        Set<UUID> whitelist = readUuidList(readGuardianConfig(), "key_whitelist");
        source.sendSuccess(() -> Component.translatable("command.guardian_mod.key_whitelist_list", uuidListString(whitelist)), false);
        return whitelist.size();
    }

    private static String uuidListString(Set<UUID> uuids) {
        return uuids.stream()
                .map(UUID::toString)
                .sorted()
                .toList()
                .toString();
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
            object.add("key_whitelist", new JsonArray());
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

    private static Set<UUID> readUuidList(JsonObject config, String key) {
        Set<UUID> whitelist = new HashSet<>();
        JsonArray entries = config.has(key) && config.get(key).isJsonArray()
                ? config.getAsJsonArray(key)
                : new JsonArray();
        for (JsonElement entry : entries) {
            try {
                whitelist.add(UUID.fromString(entry.getAsString()));
            } catch (IllegalArgumentException ignored) {
                GuardianMod.LOGGER.warn("Ignoring invalid guardian_config {} entry: {}", key, entry);
            }
        }
        return whitelist;
    }

    private static void writeWhitelist(JsonObject config, Set<UUID> whitelist) {
        writeUuidList(config, "op_diamond_whitelist", whitelist);
    }

    private static void writeUuidList(JsonObject config, String key, Set<UUID> whitelist) {
        JsonArray entries = new JsonArray();
        whitelist.stream().map(UUID::toString).sorted().forEach(entries::add);
        config.add(key, entries);
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

    private static JsonObject readEventFile(String eventFile) {
        String normalizedName = eventFile.endsWith(".json") ? eventFile : eventFile + ".json";
        Path root = ConfigLoader.configRoot().toAbsolutePath().normalize();
        Path target = root.resolve(normalizedName).normalize();
        if (!target.startsWith(root)) {
            GuardianMod.LOGGER.warn("Rejected guardian event path outside config root: {}", eventFile);
            return null;
        }

        try {
            JsonObject event = GSON.fromJson(Files.readString(target), JsonObject.class);
            return event == null ? new JsonObject() : event;
        } catch (IOException | RuntimeException e) {
            GuardianMod.LOGGER.warn("Failed to read guardian event file {}", target, e);
            return null;
        }
    }

    private static CompletableFuture<Suggestions> suggestEventFiles(SuggestionsBuilder builder) {
        String remaining = builder.getRemainingLowerCase();
        try {
            Path root = ConfigLoader.configRoot();
            if (Files.isDirectory(root)) {
                try (var paths = Files.list(root)) {
                    paths.filter(path -> path.getFileName().toString().endsWith(".json"))
                            .map(path -> path.getFileName().toString().replaceFirst("\\.json$", ""))
                            .filter(name -> name.toLowerCase(java.util.Locale.ROOT).startsWith(remaining))
                            .forEach(builder::suggest);
                }
            }
        } catch (IOException e) {
            GuardianMod.LOGGER.warn("Failed to suggest guardian event files", e);
        }
        return builder.buildFuture();
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

    private static CompletableFuture<Suggestions> suggestTriggerIds(CommandSourceStack source, SuggestionsBuilder builder) {
        String remaining = builder.getRemainingLowerCase();
        for (TriggerArea area : TriggerAreaState.get(source.getLevel()).areas) {
            String id = area.id.toString();
            if (id.startsWith(remaining)) {
                builder.suggest(id);
            }
        }
        return builder.buildFuture();
    }
}
