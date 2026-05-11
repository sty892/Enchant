package me.guardian.server.event;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.guardian.GuardianMod;
import me.guardian.config.ConfigManager;
import me.guardian.server.state.GuardianWorldState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class KeyFoundEventHandler {
    private static final Gson GSON = new Gson();
    private static final Map<UUID, Map<Identifier, Integer>> KNOWN_COUNTS = new HashMap<>();
    private static JsonObject cachedConfig;
    private static Set<UUID> cachedWhitelist = Set.of();
    private static int reloadTicks;

    private KeyFoundEventHandler() {
    }

    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(KeyFoundEventHandler::tick);
    }

    private static void tick(MinecraftServer server) {
        reloadTicks--;
        if (reloadTicks <= 0) {
            cachedConfig = readKeysConfig();
            cachedWhitelist = readKeyWhitelist();
            reloadTicks = 20;
        }
        if (cachedConfig == null || !cachedConfig.has("keys") || !cachedConfig.get("keys").isJsonArray()) {
            return;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            tickPlayer(player, cachedConfig.getAsJsonArray("keys"));
        }
    }

    private static void tickPlayer(ServerPlayer player, JsonArray keys) {
        if (cachedWhitelist.contains(player.getUUID())) {
            snapshotCounts(player, keys);
            return;
        }

        Map<Identifier, Integer> previousCounts = KNOWN_COUNTS.computeIfAbsent(player.getUUID(), uuid -> new HashMap<>());
        for (JsonElement element : keys) {
            if (!element.isJsonObject()) {
                continue;
            }

            JsonObject keyConfig = element.getAsJsonObject();
            Identifier itemId = readItemId(keyConfig);
            if (itemId == null) {
                continue;
            }

            int currentCount = countItem(player, itemId);
            int previousCount = previousCounts.getOrDefault(itemId, currentCount);
            GuardianWorldState state = GuardianWorldState.get(player.level());
            if (currentCount > previousCount && state.foundKeys.add(itemId.toString())) {
                state.setDirty();
                runFoundEvent(player, keyConfig);
            }
            previousCounts.put(itemId, currentCount);
        }
    }

    public static int resetFoundKeys(MinecraftServer server) {
        GuardianWorldState state = GuardianWorldState.get(server.overworld());
        int count = state.foundKeys.size();
        state.foundKeys.clear();
        state.setDirty();
        KNOWN_COUNTS.clear();
        return count;
    }

    private static void snapshotCounts(ServerPlayer player, JsonArray keys) {
        Map<Identifier, Integer> previousCounts = KNOWN_COUNTS.computeIfAbsent(player.getUUID(), uuid -> new HashMap<>());
        for (JsonElement element : keys) {
            if (!element.isJsonObject()) {
                continue;
            }
            Identifier itemId = readItemId(element.getAsJsonObject());
            if (itemId != null) {
                previousCounts.put(itemId, countItem(player, itemId));
            }
        }
    }

    private static void runFoundEvent(ServerPlayer player, JsonObject keyConfig) {
        ServerLevel level = player.level();
        BlockPos pos = player.blockPosition();
        if (keyConfig.has("on_found") && keyConfig.get("on_found").isJsonObject()) {
            BossEventSystem.executeEvent(level, keyConfig.getAsJsonObject("on_found"), pos, player, java.util.Collections.emptyMap());
        }
        if (keyConfig.has("on_found_script") && keyConfig.get("on_found_script").isJsonPrimitive()) {
            JsonObject event = new JsonObject();
            event.addProperty("script", keyConfig.get("on_found_script").getAsString());
            BossEventSystem.executeEvent(level, event, pos, player, java.util.Collections.emptyMap());
        }
    }

    private static int countItem(ServerPlayer player, Identifier itemId) {
        Item item = BuiltInRegistries.ITEM.getOptional(itemId).orElse(null);
        if (item == null) {
            return 0;
        }

        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static Identifier readItemId(JsonObject keyConfig) {
        if (!keyConfig.has("item_id") || !keyConfig.get("item_id").isJsonPrimitive()) {
            return null;
        }
        return Identifier.tryParse(keyConfig.get("item_id").getAsString());
    }

    private static JsonObject readKeysConfig() {
        try {
            return GSON.fromJson(ConfigManager.readRaw("keys_config.json"), JsonObject.class);
        } catch (IOException | RuntimeException e) {
            GuardianMod.LOGGER.warn("Failed to read keys_config.json", e);
            return null;
        }
    }

    private static Set<UUID> readKeyWhitelist() {
        Set<UUID> whitelist = new HashSet<>();
        try {
            JsonObject config = GSON.fromJson(ConfigManager.readRaw("guardian_config.json"), JsonObject.class);
            if (config == null || !config.has("key_whitelist") || !config.get("key_whitelist").isJsonArray()) {
                return whitelist;
            }
            for (JsonElement entry : config.getAsJsonArray("key_whitelist")) {
                try {
                    whitelist.add(UUID.fromString(entry.getAsString()));
                } catch (IllegalArgumentException ignored) {
                    GuardianMod.LOGGER.warn("Ignoring invalid key whitelist entry: {}", entry);
                }
            }
        } catch (IOException | RuntimeException e) {
            GuardianMod.LOGGER.warn("Failed to read key whitelist", e);
        }
        return whitelist;
    }
}
