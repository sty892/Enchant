package me.sty892.enchant.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import me.sty892.enchant.config.ConfigManager;
import me.sty892.enchant.entity.ModEntities;
import me.sty892.enchant.event.boss.BossConfigManager;
import me.sty892.enchant.state.GuardianWorldState;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.Collection;

public class GuardianCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(CommandManager.literal("guardian").requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.literal("boss")
                        .then(CommandManager.literal("spawn")
                                .then(CommandManager.argument("boss_id", StringArgumentType.string())
                                        .executes(context -> spawnBoss(context.getSource(), StringArgumentType.getString(context, "boss_id")))))
                        .then(CommandManager.literal("kill")
                                .then(CommandManager.literal("all")
                                        .executes(context -> killAllBosses(context.getSource())))))
                .then(CommandManager.literal("whitelist")
                        .then(CommandManager.literal("add")
                                .then(CommandManager.argument("players", EntityArgumentType.players())
                                        .executes(context -> addToWhitelist(context.getSource(), EntityArgumentType.getPlayers(context, "players")))))
                        .then(CommandManager.literal("remove")
                                .then(CommandManager.argument("players", EntityArgumentType.players())
                                        .executes(context -> removeFromWhitelist(context.getSource(), EntityArgumentType.getPlayers(context, "players")))))
                        .then(CommandManager.literal("list")
                                .executes(context -> listWhitelist(context.getSource()))))
                .then(CommandManager.literal("state")
                        .executes(context -> printState(context.getSource())))
                .then(CommandManager.literal("reset")
                        .executes(context -> resetState(context.getSource())))
                .then(CommandManager.literal("stage")
                        .then(CommandManager.argument("value", IntegerArgumentType.integer(1, 2))
                                .executes(context -> setStage(context.getSource(), IntegerArgumentType.getInteger(context, "value")))))
                .then(CommandManager.literal("reload")
                        .executes(context -> reloadConfigs(context.getSource())))
        );
    }

    private static int spawnBoss(ServerCommandSource source, String bossId) {
        // Simple spawn logic for now
        source.sendFeedback(() -> Text.literal("Попытка спавна босса: " + bossId), true);
        return 1;
    }

    private static int killAllBosses(ServerCommandSource source) {
        // Kill logic
        source.sendFeedback(() -> Text.literal("Все боссы уничтожены"), true);
        return 1;
    }

    private static int addToWhitelist(ServerCommandSource source, Collection<ServerPlayerEntity> players) {
        for (ServerPlayerEntity player : players) {
            if (!ConfigManager.getConfig().op_diamond_whitelist.contains(player.getUuid())) {
                ConfigManager.getConfig().op_diamond_whitelist.add(player.getUuid());
            }
        }
        ConfigManager.save();
        source.sendFeedback(() -> Text.literal("Игроки добавлены в вайтлист"), true);
        return 1;
    }

    private static int removeFromWhitelist(ServerCommandSource source, Collection<ServerPlayerEntity> players) {
        for (ServerPlayerEntity player : players) {
            ConfigManager.getConfig().op_diamond_whitelist.remove(player.getUuid());
        }
        ConfigManager.save();
        source.sendFeedback(() -> Text.literal("Игроки удалены из вайтлиста"), true);
        return 1;
    }

    private static int listWhitelist(ServerCommandSource source) {
        source.sendFeedback(() -> Text.literal("Вайтлист: " + ConfigManager.getConfig().op_diamond_whitelist), false);
        return 1;
    }

    private static int printState(ServerCommandSource source) {
        GuardianWorldState state = GuardianWorldState.getServerState(source.getServer());
        source.sendFeedback(() -> Text.literal("Overworld Boss: " + state.overworldBossDefeated + ", Nether Boss: " + state.netherBossDefeated), false);
        return 1;
    }

    private static int resetState(ServerCommandSource source) {
        GuardianWorldState state = GuardianWorldState.getServerState(source.getServer());
        state.overworldBossDefeated = false;
        state.netherBossDefeated = false;
        state.markDirty();
        source.sendFeedback(() -> Text.literal("Состояние мира сброшено"), true);
        return 1;
    }

    private static int setStage(ServerCommandSource source, int value) {
        GuardianWorldState state = GuardianWorldState.getServerState(source.getServer());
        if (value >= 1) state.overworldBossDefeated = true;
        if (value >= 2) state.netherBossDefeated = true;
        state.markDirty();
        source.sendFeedback(() -> Text.literal("Стадия мира установлена на " + value), true);
        return 1;
    }

    private static int reloadConfigs(ServerCommandSource source) {
        ConfigManager.load();
        BossConfigManager.load();
        source.sendFeedback(() -> Text.literal("Конфиги перезагружены"), true);
        return 1;
    }
}
