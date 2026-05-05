package me.guardian.server.restriction;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.guardian.GuardianMod;
import me.guardian.config.ConfigManager;
import me.guardian.server.state.GuardianWorldState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class DiamondRestrictionHandler {
    private static final Gson GSON = new Gson();
    private static final Component RESTRICTED_MESSAGE = Component.literal("Нельзя получить алмазы пока не убит Хранитель Верхнего Мира");
    private static final Identifier[] DIAMOND_ADVANCEMENTS = {
            Identifier.withDefaultNamespace("story/mine_diamond"),
            Identifier.withDefaultNamespace("story/shiny_gear")
    };

    private DiamondRestrictionHandler() {
    }

    public static void initialize() {
        PlayerBlockBreakEvents.BEFORE.register(DiamondRestrictionHandler::beforeBlockBreak);
        ServerTickEvents.END_SERVER_TICK.register(DiamondRestrictionHandler::onServerTick);
    }

    private static boolean beforeBlockBreak(Level world, Player player, net.minecraft.core.BlockPos pos, BlockState state, net.minecraft.world.level.block.entity.BlockEntity blockEntity) {
        if (!(world instanceof ServerLevel serverLevel) || !isDiamondOre(state)) {
            return true;
        }
        if (!isRestricted(serverLevel, player.getUUID())) {
            return true;
        }

        notifyRestricted(player);
        return false;
    }

    private static void onServerTick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) {
            return;
        }

        ServerLevel overworld = server.overworld();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (isRestricted(overworld, player.getUUID())) {
                removeDiamondItems(player);
                revokeDiamondAdvancements(server, player);
            }
        }
    }

    private static boolean isRestricted(ServerLevel level, UUID playerUuid) {
        GuardianConfig config = GuardianConfig.load();
        if (!config.diamondRestrictionEnabled || config.whitelist.contains(playerUuid)) {
            return false;
        }
        return !GuardianWorldState.get(level).overworldBossDefeated;
    }

    private static void removeDiamondItems(ServerPlayer player) {
        boolean removed = false;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (isDiamondItem(stack)) {
                player.getInventory().setItem(slot, ItemStack.EMPTY);
                removed = true;
            }
        }

        if (removed) {
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
            notifyRestricted(player);
        }
    }

    private static void revokeDiamondAdvancements(MinecraftServer server, ServerPlayer player) {
        for (Identifier advancementId : DIAMOND_ADVANCEMENTS) {
            AdvancementHolder holder = server.getAdvancements().get(advancementId);
            if (holder == null) {
                continue;
            }

            AdvancementProgress progress = player.getAdvancements().getOrStartProgress(holder);
            if (!progress.hasProgress()) {
                continue;
            }

            List<String> completedCriteria = new ArrayList<>();
            for (String criterion : progress.getCompletedCriteria()) {
                completedCriteria.add(criterion);
            }
            for (String criterion : completedCriteria) {
                player.getAdvancements().revoke(holder, criterion);
            }
        }
    }

    private static void notifyRestricted(Player player) {
        player.displayClientMessage(RESTRICTED_MESSAGE, true);
    }

    private static boolean isDiamondOre(BlockState state) {
        return state.is(Blocks.DIAMOND_ORE) || state.is(Blocks.DEEPSLATE_DIAMOND_ORE);
    }

    private static boolean isDiamondItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!"minecraft".equals(id.getNamespace())) {
            return false;
        }

        String path = id.getPath();
        return "diamond".equals(path) || path.startsWith("diamond_") || "deepslate_diamond_ore".equals(path);
    }

    private record GuardianConfig(boolean diamondRestrictionEnabled, Set<UUID> whitelist) {
        private static GuardianConfig load() {
            try {
                JsonObject json = GSON.fromJson(ConfigManager.readRaw("guardian_config.json"), JsonObject.class);
                boolean enabled = !json.has("diamond_restriction_enabled") || json.get("diamond_restriction_enabled").getAsBoolean();
                Set<UUID> whitelist = new HashSet<>();
                if (json.has("op_diamond_whitelist") && json.get("op_diamond_whitelist").isJsonArray()) {
                    JsonArray entries = json.getAsJsonArray("op_diamond_whitelist");
                    for (JsonElement entry : entries) {
                        try {
                            whitelist.add(UUID.fromString(entry.getAsString()));
                        } catch (IllegalArgumentException ignored) {
                            GuardianMod.LOGGER.warn("Ignoring invalid UUID in guardian_config.json op_diamond_whitelist: {}", entry);
                        }
                    }
                }
                return new GuardianConfig(enabled, whitelist);
            } catch (IOException | RuntimeException e) {
                GuardianMod.LOGGER.warn("Failed to read guardian_config.json; diamond restriction remains enabled with empty whitelist", e);
                return new GuardianConfig(true, Set.of());
            }
        }
    }
}
