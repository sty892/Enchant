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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BossEventSystem {
    public static void executeEvent(ServerLevel level, JsonObject eventData, BlockPos center) {
        executeEvent(level, eventData, center, null, Collections.emptyMap());
    }

    public static void executeEvent(ServerLevel level, JsonObject eventData, BlockPos center, Entity source, Map<UUID, Float> damageContributors) {
        if (eventData == null) return;

        Map<String, String> variables = variables(source);
        ScriptRunner.runInlineCommands(level, center, source, eventData, variables);
        if (eventData.has("script")) {
            if (eventData.get("script").isJsonPrimitive()) {
                ScriptRunner.runScript(level, center, source, eventData.get("script").getAsString(), variables);
            }
        } else if (eventData.has("script_id") && eventData.get("script_id").isJsonPrimitive()) {
            ScriptRunner.runScript(level, center, source, eventData.get("script_id").getAsString(), variables);
        }

        if (eventData.has("world_border_expand")) {
            expandWorldBorder(level, eventData);
        }

        if (eventData.has("spawn_structure")) {
            spawnStructure(level, eventData, center);
        }

        if (eventData.has("give_fragment") && eventData.get("give_fragment").isJsonPrimitive()) {
            String itemId = eventData.get("give_fragment").getAsString();
            giveFragment(level, itemId, center, source, damageContributors);
        }

        if (eventData.has("set_flag") && eventData.get("set_flag").isJsonPrimitive()) {
            String flag = eventData.get("set_flag").getAsString();
            GuardianWorldState state = GuardianWorldState.get(level);
            if (flag.equals("overworldBossDefeated")) state.setOverworldBossDefeated(true);
            else if (flag.equals("netherBossDefeated")) state.setNetherBossDefeated(true);
        }

        if (eventData.has("allow_diamonds") && eventData.get("allow_diamonds").isJsonPrimitive() && eventData.get("allow_diamonds").getAsBoolean()) {
            GuardianWorldState state = GuardianWorldState.get(level);
            state.setOverworldBossDefeated(true);
        }

        if (eventData.has("broadcast_title") && eventData.get("broadcast_title").isJsonPrimitive()) {
            String title = eventData.get("broadcast_title").getAsString();
            broadcastTitle(level, title);
        }

        if (eventData.has("play_animation") && eventData.get("play_animation").isJsonPrimitive()) {
            String animation = eventData.get("play_animation").getAsString();
            triggerSourceAnimation(source, animation);
        }
    }

    private static void expandWorldBorder(ServerLevel level, JsonObject eventData) {
        if (!eventData.get("world_border_expand").isJsonObject()) {
            GuardianMod.LOGGER.warn("world_border_expand must be an object: {}", eventData.get("world_border_expand"));
            return;
        }
        JsonObject border = eventData.getAsJsonObject("world_border_expand");
        if (!border.has("to") || !border.has("duration_seconds") || !border.get("to").isJsonPrimitive() || !border.get("duration_seconds").isJsonPrimitive()) {
            GuardianMod.LOGGER.warn("world_border_expand requires numeric to and duration_seconds fields: {}", border);
            return;
        }
        try {
            double to = border.get("to").getAsDouble();
            long duration = Math.max(0L, border.get("duration_seconds").getAsLong());
            level.getWorldBorder().lerpSizeBetween(level.getWorldBorder().getSize(), to, duration * 20L, level.getGameTime());
        } catch (RuntimeException e) {
            GuardianMod.LOGGER.warn("Invalid world_border_expand data: {}", border, e);
        }
    }

    private static void triggerSourceAnimation(Entity source, String animation) {
        if (source instanceof OverworldGuardianEntityAccessor overworld) {
            overworld.guardian_mod$triggerAttackAnimation(animation);
        } else if (source instanceof me.guardian.entity.NetherGuardianEntity nether) {
            nether.triggerAttackAnimation(animation);
        } else {
            GuardianMod.LOGGER.info("play_animation ignored for source {}", source == null ? "none" : source.getUUID());
        }
    }

    private static BlockPos readOffset(JsonObject eventData) {
        if (!eventData.has("spawn_structure_offset") || !eventData.get("spawn_structure_offset").isJsonObject()) {
            return BlockPos.ZERO;
        }

        return readBlockPos(eventData.getAsJsonObject("spawn_structure_offset"));
    }

    private static void spawnStructure(ServerLevel level, JsonObject eventData, BlockPos center) {
        String structureId = null;
        BlockPos offset = readOffset(eventData);

        if (eventData.get("spawn_structure").isJsonPrimitive()) {
            try {
                structureId = eventData.get("spawn_structure").getAsString();
            } catch (RuntimeException e) {
                GuardianMod.LOGGER.warn("spawn_structure has invalid id value: {}", eventData.get("spawn_structure"));
                return;
            }
        } else if (eventData.get("spawn_structure").isJsonObject()) {
            JsonObject structure = eventData.getAsJsonObject("spawn_structure");
            if (structure.has("id") && structure.get("id").isJsonPrimitive()) {
                try {
                    structureId = structure.get("id").getAsString();
                } catch (RuntimeException e) {
                    GuardianMod.LOGGER.warn("spawn_structure has invalid object id value: {}", structure.get("id"));
                    return;
                }
            }
            if (structure.has("offset") && structure.get("offset").isJsonObject()) {
                offset = readBlockPos(structure.getAsJsonObject("offset"));
            }
        } else {
            GuardianMod.LOGGER.warn("spawn_structure must be a string or object: {}", eventData.get("spawn_structure"));
            return;
        }

        Identifier id = StructureSpawner.parseStructureId(structureId);
        if (id == null) {
            GuardianMod.LOGGER.warn("spawn_structure has invalid or missing id: {}", eventData.get("spawn_structure"));
            return;
        }

        StructureSpawner.place(level, center.offset(offset), id.toString());
    }

    private static BlockPos readBlockPos(JsonObject object) {
        int x = readInt(object, "x", 0);
        int y = readInt(object, "y", 0);
        int z = readInt(object, "z", 0);
        return new BlockPos(x, y, z);
    }

    private static int readInt(JsonObject object, String key, int fallback) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()) {
            return fallback;
        }
        try {
            return object.get(key).getAsInt();
        } catch (RuntimeException e) {
            GuardianMod.LOGGER.warn("Invalid spawn_structure offset {} value: {}", key, object.get(key));
            return fallback;
        }
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
        ClientboundSetTitleTextPacket packet = new ClientboundSetTitleTextPacket(component);
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            player.connection.send(packet);
        }
    }

    private static Map<String, String> variables(Entity source) {
        Map<String, String> variables = new HashMap<>();
        if (source instanceof ServerPlayer player) {
            variables.put("nickname", player.getScoreboardName());
            variables.put("player", player.getScoreboardName());
            variables.put("uuid", player.getUUID().toString());
        }
        return variables;
    }

    private interface OverworldGuardianEntityAccessor {
        void guardian_mod$triggerAttackAnimation(String animation);
    }
}
