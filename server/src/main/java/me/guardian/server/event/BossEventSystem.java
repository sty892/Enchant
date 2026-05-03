package me.guardian.server.event;

import com.google.gson.JsonObject;
import me.guardian.GuardianMod;
import me.guardian.entity.ModEntities;
import me.guardian.item.ModItems;
import me.guardian.server.state.GuardianWorldState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

public class BossEventSystem {

    public static void executeEvent(ServerLevel level, JsonObject eventData, BlockPos center) {
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
            GuardianMod.LOGGER.info("Executing spawn_structure: " + structureId);
            // In Module 12 we will implement real structure spawning
        }

        // give_fragment
        if (eventData.has("give_fragment")) {
            String itemId = eventData.get("give_fragment").getAsString();
            // Typically gives to nearest player or all participants
            for (ServerPlayer player : level.players()) {
                if (player.distanceToSqr(center.getX(), center.getY(), center.getZ()) < 10000) {
                    // Item identification and giving
                    if (itemId.contains("fragment_overworld")) player.getInventory().add(new ItemStack(ModItems.FRAGMENT_OVERWORLD));
                    else if (itemId.contains("fragment_nether")) player.getInventory().add(new ItemStack(ModItems.FRAGMENT_NETHER));
                    else if (itemId.contains("fragment_generic")) player.getInventory().add(new ItemStack(ModItems.FRAGMENT_GENERIC));
                }
            }
        }

        // set_flag
        if (eventData.has("set_flag")) {
            String flag = eventData.get("set_flag").getAsString();
            GuardianWorldState state = GuardianWorldState.get(level);
            if (flag.equals("overworldBossDefeated")) state.overworldBossDefeated = true;
            else if (flag.equals("netherBossDefeated")) state.netherBossDefeated = true;
            state.setDirty();
        }

        // broadcast_title
        if (eventData.has("broadcast_title")) {
            String title = eventData.get("broadcast_title").getAsString();
            level.getServer().getPlayerList().broadcastSystemMessage(Component.literal(title), false);
        }
        
        // play_animation (Placeholder for packet)
        if (eventData.has("play_animation")) {
            String animation = eventData.get("play_animation").getAsString();
            GuardianMod.LOGGER.info("Playing animation: " + animation);
        }
    }
}
