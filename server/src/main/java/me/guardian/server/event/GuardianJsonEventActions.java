package me.guardian.server.event;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.guardian.GuardianMod;
import me.guardian.entity.GenericBossEntity;
import me.guardian.entity.NetherGuardianEntity;
import me.guardian.entity.OverworldGuardianEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class GuardianJsonEventActions {
    private GuardianJsonEventActions() {
    }

    public static int run(MinecraftServer server, ServerLevel defaultLevel, BlockPos defaultCenter, JsonObject event) {
        if (event == null) {
            GuardianMod.LOGGER.warn("Guardian event file is empty or invalid");
            return 0;
        }
        if (!event.has("actions") || !event.get("actions").isJsonArray()) {
            GuardianMod.LOGGER.warn("Guardian event {} has no actions array", eventId(event));
            return 0;
        }

        int scheduled = 0;
        JsonArray actions = event.getAsJsonArray("actions");
        for (JsonElement element : actions) {
            if (!element.isJsonObject()) {
                GuardianMod.LOGGER.warn("Guardian event {} has a non-object action: {}", eventId(event), element);
                continue;
            }

            JsonObject action = element.getAsJsonObject();
            ParsedAction parsed = parseAction(action);
            if (parsed == null) {
                continue;
            }

            long delayTicks = readLong(parsed.payload(), "delay_ticks", 0L);
            GuardianEventScheduler.schedule(server, delayTicks, () -> runAction(server, defaultLevel, defaultCenter, parsed));
            scheduled++;
        }
        return scheduled;
    }

    private static void runAction(MinecraftServer server, ServerLevel defaultLevel, BlockPos defaultCenter, ParsedAction parsed) {
        String type = parsed.name();
        JsonObject payload = parsed.payload();
        try {
            switch (type.toLowerCase(Locale.ROOT)) {
                case "spawn_boss" -> spawnBoss(server, defaultLevel, defaultCenter, payload);
                case "play_boss_animation" -> playBossAnimation(server, defaultLevel, defaultCenter, payload);
                case "knockback_players" -> knockbackPlayers(server, defaultLevel, defaultCenter, payload);
                case "world_border_expand" -> worldBorderExpand(server, defaultLevel, defaultCenter, payload);
                case "broadcast_message" -> broadcastMessage(server, payload);
                case "broadcast_actionbar" -> broadcastActionbar(server, payload);
                default -> GuardianMod.LOGGER.warn("Unknown guardian event action: {}", type);
            }
        } catch (RuntimeException e) {
            GuardianMod.LOGGER.warn("Guardian event action {} failed; continuing", type, e);
        }
    }

    private static ParsedAction parseAction(JsonObject action) {
        String type = readString(action, "action", "");
        if (type.isBlank()) {
            type = readString(action, "type", "");
        }
        if (!type.isBlank()) {
            return new ParsedAction(type, action);
        }

        if (action.isEmpty()) {
            GuardianMod.LOGGER.warn("Guardian event action is empty");
            return null;
        }
        if (action.size() != 1) {
            GuardianMod.LOGGER.warn("Guardian event action must use action/type or exactly one wrapper key: {}", action);
            return null;
        }

        Map.Entry<String, JsonElement> entry = action.entrySet().iterator().next();
        if (!entry.getValue().isJsonObject()) {
            GuardianMod.LOGGER.warn("Guardian event action wrapper {} must contain an object payload: {}", entry.getKey(), action);
            return null;
        }
        return new ParsedAction(entry.getKey(), entry.getValue().getAsJsonObject());
    }

    private static void spawnBoss(MinecraftServer server, ServerLevel defaultLevel, BlockPos defaultCenter, JsonObject action) {
        ServerLevel level = readLevel(server, defaultLevel, action);
        if (level == null) {
            GuardianMod.LOGGER.warn("spawn_boss missing level/config: {}", action);
            return;
        }

        String bossId = readString(action, "boss_id", readString(action, "entity_id", ""));
        Identifier id = parseId(bossId);
        if (id == null) {
            GuardianMod.LOGGER.warn("spawn_boss has invalid boss_id/entity_id: {}", bossId);
            return;
        }

        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
        if (type == null) {
            GuardianMod.LOGGER.warn("spawn_boss unknown entity type: {}", id);
            return;
        }

        Entity entity = type.create(level, EntitySpawnReason.COMMAND);
        if (entity == null) {
            GuardianMod.LOGGER.warn("spawn_boss failed to create entity: {}", id);
            return;
        }

        Vec3 pos = readVec3(action, "pos", Vec3.atCenterOf(defaultCenter));
        if (!action.has("pos")) {
            pos = readVec3(action, "center", pos);
        }
        entity.setPos(pos.x, pos.y, pos.z);
        entity.setYRot((float) readDouble(action, "yaw", entity.getYRot()));
        entity.setXRot((float) readDouble(action, "pitch", entity.getXRot()));
        level.addFreshEntity(entity);
    }

    private static void playBossAnimation(MinecraftServer server, ServerLevel defaultLevel, BlockPos defaultCenter, JsonObject action) {
        Entity boss = findBoss(server, defaultLevel, defaultCenter, action);
        if (boss == null) {
            GuardianMod.LOGGER.warn("play_boss_animation could not find target boss: {}", action);
            return;
        }

        String animation = readString(action, "animation", "");
        if (animation.isBlank()) {
            GuardianMod.LOGGER.warn("play_boss_animation missing animation name for boss {}", boss.getUUID());
            return;
        }

        GuardianMod.LOGGER.info("play_boss_animation requested: {} for boss {}", animation, boss.getUUID());
    }

    private static void knockbackPlayers(MinecraftServer server, ServerLevel defaultLevel, BlockPos defaultCenter, JsonObject action) {
        ServerLevel level = readLevel(server, defaultLevel, action);
        if (level == null) {
            GuardianMod.LOGGER.warn("knockback_players missing level/config: {}", action);
            return;
        }

        Vec3 center = readVec3(action, "center", Vec3.atCenterOf(defaultCenter));
        double radius = readDouble(action, "radius", 16.0D);
        double strength = readDouble(action, "horizontal_strength", readDouble(action, "strength", 1.0D));
        double yStrength = readDouble(action, "vertical_strength", readDouble(action, "y_strength", 0.35D));
        AABB area = new AABB(center, center).inflate(radius);
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, area, player -> player.distanceToSqr(center) <= radius * radius)) {
            Vec3 direction = player.position().subtract(center);
            if (direction.lengthSqr() < 0.0001D) {
                direction = new Vec3(0.0D, 0.0D, 1.0D);
            }
            Vec3 knockback = direction.horizontal().normalize().scale(strength).add(0.0D, yStrength, 0.0D);
            player.setDeltaMovement(player.getDeltaMovement().add(knockback));
            player.hurtMarked = true;
        }
    }

    private static void worldBorderExpand(MinecraftServer server, ServerLevel defaultLevel, BlockPos defaultCenter, JsonObject action) {
        ServerLevel level = readLevel(server, defaultLevel, action);
        if (level == null) {
            GuardianMod.LOGGER.warn("world_border_expand missing level/config: {}", action);
            return;
        }

        Vec3 center = readVec3(action, "center", Vec3.atCenterOf(defaultCenter));
        double from = readDouble(action, "from", level.getWorldBorder().getSize());
        double to = readDouble(action, "to", from);
        long durationSeconds = readLong(action, "duration_seconds", 0L);

        level.getWorldBorder().setCenter(center.x, center.z);
        level.getWorldBorder().setSize(from);
        level.getWorldBorder().lerpSizeBetween(from, to, durationSeconds * 20L, level.getGameTime());
    }

    private static void broadcastMessage(MinecraftServer server, JsonObject action) {
        String message = readString(action, "message", readString(action, "text", ""));
        if (message.isBlank()) {
            GuardianMod.LOGGER.warn("broadcast_message missing message/text: {}", action);
            return;
        }
        server.getPlayerList().broadcastSystemMessage(Component.literal(message), false);
    }

    private static void broadcastActionbar(MinecraftServer server, JsonObject action) {
        String message = readString(action, "message", readString(action, "text", ""));
        if (message.isBlank()) {
            GuardianMod.LOGGER.warn("broadcast_actionbar missing message/text: {}", action);
            return;
        }
        Component component = Component.literal(message);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.displayClientMessage(component, true);
        }
    }

    private static Entity findBoss(MinecraftServer server, ServerLevel defaultLevel, BlockPos defaultCenter, JsonObject action) {
        String uuidValue = readString(action, "boss_uuid", "");
        if (!uuidValue.isBlank()) {
            try {
                UUID uuid = UUID.fromString(uuidValue);
                for (ServerLevel level : server.getAllLevels()) {
                    Entity entity = level.getEntity(uuid);
                    if (entity != null && isGuardianBoss(entity)) {
                        return entity;
                    }
                }
            } catch (IllegalArgumentException e) {
                GuardianMod.LOGGER.warn("Invalid boss_uuid in play_boss_animation: {}", uuidValue);
            }
        }

        ServerLevel level = readLevel(server, defaultLevel, action);
        Vec3 center = readVec3(action, "center", Vec3.atCenterOf(defaultCenter));
        double radius = readDouble(action, "radius", 64.0D);
        if (level == null) {
            return null;
        }

        Entity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Entity entity : level.getAllEntities()) {
            if (!isGuardianBoss(entity)) {
                continue;
            }
            double distance = entity.distanceToSqr(center);
            if (distance <= radius * radius && distance < nearestDistance) {
                nearest = entity;
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

    private static ServerLevel readLevel(MinecraftServer server, ServerLevel defaultLevel, JsonObject action) {
        String world = readString(action, "world", readString(action, "dimension", ""));
        if (world.isBlank()) {
            return defaultLevel;
        }

        Identifier id = Identifier.tryParse(world);
        if (id == null) {
            GuardianMod.LOGGER.warn("Invalid event world/dimension id: {}", world);
            return null;
        }

        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, id);
        ServerLevel level = server.getLevel(key);
        if (level == null) {
            GuardianMod.LOGGER.warn("Unknown event world/dimension: {}", world);
        }
        return level;
    }

    private static Vec3 readVec3(JsonObject object, String key, Vec3 fallback) {
        if (!object.has(key) || !object.get(key).isJsonObject()) {
            return fallback;
        }
        JsonObject value = object.getAsJsonObject(key);
        return new Vec3(
                readDouble(value, "x", fallback.x),
                readDouble(value, "y", fallback.y),
                readDouble(value, "z", fallback.z)
        );
    }

    private static Identifier parseId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String id = value.trim();
        if (id.indexOf(':') < 0) {
            id = GuardianMod.MOD_ID + ":" + id;
        }
        return Identifier.tryParse(id);
    }

    private static String readString(JsonObject object, String key, String fallback) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()) {
            return fallback;
        }
        try {
            return object.get(key).getAsString();
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static double readDouble(JsonObject object, String key, double fallback) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()) {
            return fallback;
        }
        try {
            return object.get(key).getAsDouble();
        } catch (RuntimeException e) {
            GuardianMod.LOGGER.warn("Invalid numeric value for {}: {}", key, object.get(key));
            return fallback;
        }
    }

    private static long readLong(JsonObject object, String key, long fallback) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()) {
            return fallback;
        }
        try {
            return object.get(key).getAsLong();
        } catch (RuntimeException e) {
            GuardianMod.LOGGER.warn("Invalid long value for {}: {}", key, object.get(key));
            return fallback;
        }
    }

    private static String eventId(JsonObject event) {
        return readString(event, "event_id", "<unknown>");
    }

    private record ParsedAction(String name, JsonObject payload) {
    }
}
