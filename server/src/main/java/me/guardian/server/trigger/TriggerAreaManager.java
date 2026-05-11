package me.guardian.server.trigger;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import me.guardian.event.GuardianTriggerHooks;
import me.guardian.item.ModItems;
import me.guardian.network.TriggerAreaPayloads;
import me.guardian.trigger.TriggerArea;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.commands.arguments.selector.EntitySelectorParser;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec2;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class TriggerAreaManager {
    private static final Map<UUID, BlockPos> FIRST_POINTS = new HashMap<>();
    private static final Map<UUID, Set<UUID>> INSIDE_AREAS = new HashMap<>();

    private TriggerAreaManager() {
    }

    public static void initialize() {
        GuardianTriggerHooks.setPointHook(TriggerAreaManager::setPoint);
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
            if (!level.isClientSide() && player.getItemInHand(hand).is(ModItems.TRIGGER_AREA_CREATOR)) {
                setPoint(player, pos, false);
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) ->
                !(player instanceof ServerPlayer serverPlayer) || !blocksBlockBreak(serverPlayer, pos));
        ServerTickEvents.END_SERVER_TICK.register(TriggerAreaManager::tick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sync(handler.player));
        ServerPlayNetworking.registerGlobalReceiver(TriggerAreaPayloads.OpenEditor.TYPE, (payload, context) -> {
            TriggerArea area = TriggerAreaState.get(context.player().level()).get(payload.areaId());
            if (area != null) {
                context.responseSender().sendPacket(new TriggerAreaPayloads.EditorData(area.serialize()));
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(TriggerAreaPayloads.SaveEditor.TYPE, (payload, context) -> {
            try {
                TriggerAreaState state = TriggerAreaState.get(context.player().level());
                TriggerArea edited = TriggerArea.deserialize(payload.area());
                if (state.get(edited.id) != null) {
                    state.put(edited);
                    syncAll(context.server());
                }
            } catch (RuntimeException ignored) {
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(TriggerAreaPayloads.Delete.TYPE, (payload, context) -> deleteArea(context.server(), payload.areaId()));
    }

    public static void setPoint(Player player, BlockPos pos, boolean second) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        if (!second) {
            FIRST_POINTS.put(serverPlayer.getUUID(), pos.immutable());
            serverPlayer.displayClientMessage(Component.literal("Trigger area first point set"), true);
            return;
        }

        BlockPos first = FIRST_POINTS.remove(serverPlayer.getUUID());
        if (first == null) {
            serverPlayer.displayClientMessage(Component.literal("Set the first trigger point with left click first"), true);
            return;
        }

        TriggerArea area = new TriggerArea(UUID.randomUUID(), serverPlayer.level().dimension().identifier().toString(), first, pos);
        TriggerAreaState.get(serverPlayer.level()).put(area);
        serverPlayer.displayClientMessage(Component.literal("Trigger area created"), true);
        syncAll(serverPlayer.level().getServer());
    }

    public static boolean blocksDamage(Entity target, DamageSource source) {
        if (!(target.level() instanceof ServerLevel level)) {
            return false;
        }
        Entity attacker = source.getEntity();
        for (TriggerArea area : TriggerAreaState.get(level).areas) {
            if (!area.globalRestrictions || (!area.contains(target) && (attacker == null || !area.contains(attacker)))) {
                continue;
            }
            if (isRestricted(area, target) || attacker != null && isRestricted(area, attacker)) {
                return true;
            }
        }
        return false;
    }

    private static boolean blocksBlockBreak(ServerPlayer player, BlockPos pos) {
        for (TriggerArea area : TriggerAreaState.get(player.level()).areas) {
            if (area.globalRestrictions && area.contains(player.level().dimension(), pos) && isRestricted(area, player)) {
                return true;
            }
        }
        return false;
    }

    private static void tick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            tickPlayer(player);
        }
    }

    private static void tickPlayer(ServerPlayer player) {
        Set<UUID> inside = INSIDE_AREAS.computeIfAbsent(player.getUUID(), uuid -> new HashSet<>());
        for (TriggerArea area : TriggerAreaState.get(player.level()).areas) {
            boolean contains = area.contains(player);
            boolean wasInside = inside.contains(area.id);
            if (contains && !wasInside) {
                inside.add(area.id);
                trigger(area, player);
            } else if (!contains && wasInside) {
                inside.remove(area.id);
            }
        }
    }

    private static void trigger(TriggerArea area, ServerPlayer player) {
        if ((area.runOnce && area.runCount > 0) || !matchesTrigger(area, player)) {
            return;
        }

        MinecraftServer server = player.level().getServer();
        CommandSourceStack source = new CommandSourceStack(
                server,
                player.position(),
                player.getRotationVector(),
                player.level(),
                PermissionSet.ALL_PERMISSIONS,
                player.getScoreboardName(),
                player.getDisplayName(),
                server,
                player
        );
        for (String command : area.commands) {
            String trimmed = command.trim();
            if (!trimmed.isEmpty()) {
                if (trimmed.startsWith("/")) {
                    trimmed = trimmed.substring(1);
                }
                server.getCommands().performPrefixedCommand(source, trimmed);
            }
        }
        area.runCount++;
        TriggerAreaState.get(player.level()).setDirty();
    }

    private static boolean matchesTrigger(TriggerArea area, Entity entity) {
        return "everyone".equals(area.triggerMode) || matchesSelectorList(area.triggerSelectors, entity);
    }

    private static boolean isRestricted(TriggerArea area, Entity entity) {
        return switch (area.restrictionMode) {
            case "only_matching" -> matchesSelectorList(area.restrictionSelectors, entity);
            case "except_matching" -> !matchesSelectorList(area.restrictionSelectors, entity);
            default -> true;
        };
    }

    private static boolean matchesSelectorList(String raw, Entity entity) {
        if (raw == null || raw.isBlank()) {
            return false;
        }

        boolean included = false;
        for (String entry : splitEntries(raw)) {
            boolean exclude = entry.startsWith("!");
            String selector = exclude ? entry.substring(1).trim() : entry;
            boolean matches = matchesSelector(selector, entity);
            if (exclude && matches) {
                return false;
            }
            included |= matches;
        }
        return included;
    }

    private static java.util.List<String> splitEntries(String raw) {
        java.util.List<String> entries = new java.util.ArrayList<>();
        StringBuilder entry = new StringBuilder();
        int bracketDepth = 0;
        for (int i = 0; i < raw.length(); i++) {
            char character = raw.charAt(i);
            if (character == '[') {
                bracketDepth++;
            } else if (character == ']' && bracketDepth > 0) {
                bracketDepth--;
            }
            if (character == ',' && bracketDepth == 0) {
                String value = entry.toString().trim();
                if (!value.isEmpty()) {
                    entries.add(value);
                }
                entry.setLength(0);
                continue;
            }
            entry.append(character);
        }
        String value = entry.toString().trim();
        if (!value.isEmpty()) {
            entries.add(value);
        }
        return entries;
    }

    private static boolean matchesSelector(String selector, Entity entity) {
        if (selector.isBlank()) {
            return false;
        }
        if (selector.startsWith("@")) {
            try {
                EntitySelector parsed = new EntitySelectorParser(new StringReader(selector), true).parse();
                return parsed.findEntities(sourceForSelector(entity)).contains(entity);
            } catch (CommandSyntaxException ignored) {
                return false;
            }
        }
        if (selector.startsWith("tag=")) {
            return entity.getTags().contains(selector.substring("tag=".length()));
        }
        if (selector.startsWith("team=")) {
            return entity.getTeam() != null && selector.substring("team=".length()).equals(entity.getTeam().getName());
        }
        if (selector.startsWith("name=")) {
            return selector.substring("name=".length()).equals(entity.getScoreboardName());
        }
        return selector.equals(entity.getScoreboardName());
    }

    private static CommandSourceStack sourceForSelector(Entity entity) {
        ServerLevel level = (ServerLevel) entity.level();
        return new CommandSourceStack(level.getServer(), entity.position(), Vec2.ZERO, level, PermissionSet.ALL_PERMISSIONS,
                "GuardianMod", Component.literal("GuardianMod"), level.getServer(), entity);
    }

    private static void sync(ServerPlayer player) {
        ServerPlayNetworking.send(player, new TriggerAreaPayloads.Sync(serializedAreas(TriggerAreaState.get(player.level()))));
    }

    public static boolean deleteArea(MinecraftServer server, UUID id) {
        boolean removed = TriggerAreaState.get(server.overworld()).remove(id);
        if (removed) {
            for (Set<UUID> inside : INSIDE_AREAS.values()) {
                inside.remove(id);
            }
            syncAll(server);
        }
        return removed;
    }

    public static void syncAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sync(player);
        }
    }

    private static java.util.List<String> serializedAreas(TriggerAreaState state) {
        return state.areas.stream().map(TriggerArea::serialize).toList();
    }
}
