package me.guardian.server.event;

import com.google.gson.JsonObject;
import me.guardian.GuardianMod;
import me.guardian.server.state.GuardianWorldState;
import me.guardian.server.structure.StructureSpawner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

public class BossEventSystem {

    public static void executeEvent(ServerLevel level, JsonObject eventData, BlockPos center) {
        executeEvent(level, eventData, center, null, Collections.emptyMap());
    }

    public static void executeEvent(ServerLevel level, JsonObject eventData, BlockPos center, Entity source, Map<UUID, Float> damageContributors) {
        if (eventData == null) return;

        // world_border_expand
        if (eventData.has("world_border_expand")) {
            JsonObject border = eventData.getAsJsonObject("world_border_expand");
            double to = border.get("to").getAsDouble();
            long duration = border.get("duration_seconds").getAsLong();
            level.getWorldBorder().lerpSizeBetween(level.getWorldBorder().getSize(), to, duration * 20L, level.getGameTime());
        }

        // spawn_structure / spawn_structure_offset (Placeholder for now)
        if (eventData.has("spawn_structure")) {
            String structureId = eventData.get("spawn_structure").getAsString();
            BlockPos structurePos = center.offset(readOffset(eventData));
            StructureSpawner.place(level, structurePos, structureId);
        }

        // give_fragment
        if (eventData.has("give_fragment")) {
            String itemId = eventData.get("give_fragment").getAsString();
            giveFragment(level, itemId, center, source, damageContributors);
        }

        // set_flag
        if (eventData.has("set_flag")) {
            String flag = eventData.get("set_flag").getAsString();
            GuardianWorldState state = GuardianWorldState.get(level);
            if (flag.equals("overworldBossDefeated")) state.overworldBossDefeated = true;
            else if (flag.equals("netherBossDefeated")) state.netherBossDefeated = true;
            state.setDirty();
        }

        // allow_diamonds
        if (eventData.has("allow_diamonds") && eventData.get("allow_diamonds").getAsBoolean()) {
            GuardianWorldState state = GuardianWorldState.get(level);
            state.overworldBossDefeated = true;
            state.setDirty();
        }

        // broadcast_title
        if (eventData.has("broadcast_title")) {
            String title = eventData.get("broadcast_title").getAsString();
            broadcastTitle(level, title);
        }
        
        // play_animation (Placeholder for packet)
        if (eventData.has("play_animation")) {
            String animation = eventData.get("play_animation").getAsString();
            GuardianMod.LOGGER.info("play_animation placeholder: {} for source {}", animation, source == null ? "none" : source.getUUID());
        }
    }

    private static BlockPos readOffset(JsonObject eventData) {
        if (!eventData.has("spawn_structure_offset") || !eventData.get("spawn_structure_offset").isJsonObject()) {
            return BlockPos.ZERO;
        }

        JsonObject offset = eventData.getAsJsonObject("spawn_structure_offset");
        int x = offset.has("x") ? offset.get("x").getAsInt() : 0;
        int y = offset.has("y") ? offset.get("y").getAsInt() : 0;
        int z = offset.has("z") ? offset.get("z").getAsInt() : 0;
        return new BlockPos(x, y, z);
    }

    private static void giveFragment(ServerLevel level, String itemId, BlockPos center, Entity source, Map<UUID, Float> damageContributors) {
        Identifier id = Identifier.tryParse(itemId);
        if (id == null) {
            GuardianMod.LOGGER.warn("Invalid give_fragment item id: {}", itemId);
            return;
        }

        BuiltInRegistries.ITEM.getOptional(id).ifPresentOrElse(item -> {
            if (damageContributors != null && !damageContributors.isEmpty()) {
                for (UUID uuid : damageContributors.keySet()) {
                    ServerPlayer player = level.getServer().getPlayerList().getPlayer(uuid);
                    if (player != null) {
                        player.addItem(new ItemStack(item));
                    }
                }
                return;
            }

            if (source instanceof ServerPlayer player) {
                player.addItem(new ItemStack(item));
                return;
            }

            for (ServerPlayer player : level.players()) {
                if (player.distanceToSqr(center.getX(), center.getY(), center.getZ()) < 10000) {
                    player.addItem(new ItemStack(item));
                }
            }
        }, () -> GuardianMod.LOGGER.warn("Unknown give_fragment item id: {}", itemId));
    }

    private static void broadcastTitle(ServerLevel level, String title) {
        Component component = Component.literal(title);
        level.getServer().getPlayerList().broadcastSystemMessage(component, false);
        ClientboundSetTitleTextPacket packet = new ClientboundSetTitleTextPacket(component);
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            player.connection.send(packet);
        }
    }
}
