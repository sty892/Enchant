package me.guardian.server.trigger;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import me.guardian.event.GuardianTriggerHooks;
import me.guardian.item.ModItems;
import me.guardian.network.TriggerAreaPayloads;
import me.guardian.trigger.TriggerArea;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
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
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
            if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer && blocksEntityAttack(serverPlayer, entity)) {
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });
        UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
            if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer && blocksEntityInteraction(serverPlayer, entity)) {
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });
        ServerTickEvents.END_SERVER_TICK.register(TriggerAreaManager::tick);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING.register(server -> PENDING_COMMANDS.clear());
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sync(handler.player));
        ServerPlayNetworking.registerGlobalReceiver(TriggerAreaPayloads.OpenEditor.TYPE, (payload, context) -> {
            TriggerArea area = TriggerAreaState.get(context.player().level()).get(payload.areaId());
            if (area != null && !area.guarded) {
                context.responseSender().sendPacket(new TriggerAreaPayloads.EditorData(area.serialize()));
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(TriggerAreaPayloads.SaveEditor.TYPE, (payload, context) -> {
            try {
                TriggerAreaState state = TriggerAreaState.get(context.player().level());
                TriggerArea edited = TriggerArea.deserialize(payload.area());
                TriggerArea existing = state.get(edited.id);
                if (existing != null && !existing.guarded) {
                    edited.runCount = existing.runCount;
                    edited.guarded = existing.guarded;
                    state.put(edited);
                    syncAll(context.server());
                }
            } catch (RuntimeException ignored) {
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(TriggerAreaPayloads.Delete.TYPE, (payload, context) -> {
            if (!isGuarded(context.server(), payload.areaId())) {
                deleteArea(context.server(), payload.areaId());
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(TriggerAreaPayloads.Reset.TYPE, (payload, context) -> {
            if (!isGuarded(context.server(), payload.areaId())) {
                resetArea(context.server(), payload.areaId());
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(TriggerAreaPayloads.ToggleGuard.TYPE,
                (payload, context) -> {
                    if (context.player().isHolding(ModItems.TRIGGER_GUARD)) {
                        toggleGuard(context.server(), payload.areaId());
                    }
                });
    }

    public static void setPoint(Player player, BlockPos pos, boolean second) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        if (!second) {
            FIRST_POINTS.put(serverPlayer.getUUID(), pos.immutable());
            serverPlayer.displayClientMessage(Component.translatable("message.guardian_mod.trigger.first_point_set"), true);
            return;
        }

        BlockPos first = FIRST_POINTS.remove(serverPlayer.getUUID());
        if (first == null) {
            serverPlayer.displayClientMessage(Component.translatable("message.guardian_mod.trigger.set_first_first"), true);
            return;
        }

        TriggerArea area = new TriggerArea(UUID.randomUUID(), serverPlayer.level().dimension().identifier().toString(), first, pos);
        TriggerAreaState.get(serverPlayer.level()).put(area);
        serverPlayer.displayClientMessage(Component.translatable("message.guardian_mod.trigger.created"), true);
        syncAll(serverPlayer.level().getServer());
    }

    public static boolean blocksDamage(Entity target, DamageSource source) {
        if (!(target.level() instanceof ServerLevel level)) {
            return false;
        }
        Entity attacker = source.getEntity();
        for (TriggerArea area : TriggerAreaState.get(level).areas) {
            if (area.guarded || !area.isPrivate() || !area.restrictAttacking || (!area.intersects(target) && (attacker == null || !area.intersects(attacker)))) {
                continue;
            }
            if (privateAppliesTo(area, target) || attacker != null && privateAppliesTo(area, attacker)) {
                return true;
            }
        }
        return false;
    }

    private static boolean blocksBlockBreak(ServerPlayer player, BlockPos pos) {
        for (TriggerArea area : TriggerAreaState.get(player.level()).areas) {
            if (!area.guarded && area.isPrivate() && area.restrictBlockBreaking && area.contains(player.level().dimension(), pos) && privateAppliesTo(area, player)) {
                return true;
            }
        }
        return false;
    }

    private static boolean blocksEntityAttack(ServerPlayer player, Entity target) {
        for (TriggerArea area : TriggerAreaState.get(player.level()).areas) {
            if (!area.guarded && area.isPrivate() && area.restrictAttacking && (area.intersects(player) || area.intersects(target)) && privateAppliesTo(area, player)) {
                return true;
            }
        }
        return false;
    }

    private static boolean blocksEntityInteraction(ServerPlayer player, Entity target) {
        for (TriggerArea area : TriggerAreaState.get(player.level()).areas) {
            if (!area.guarded && area.isPrivate() && area.restrictInteractions && (area.intersects(player) || area.intersects(target)) && privateAppliesTo(area, player)) {
                return true;
            }
        }
        return false;
    }

    public static final class PendingCommand {
        public final CommandSourceStack source;
        public final String command;
        public int ticksRemaining;

        public PendingCommand(CommandSourceStack source, String command, int ticksRemaining) {
            this.source = source;
            this.command = command;
            this.ticksRemaining = ticksRemaining;
        }
    }

    private static final java.util.List<PendingCommand> PENDING_COMMANDS = new java.util.ArrayList<>();

    private static void tick(MinecraftServer server) {
        java.util.Iterator<PendingCommand> iterator = PENDING_COMMANDS.iterator();
        while (iterator.hasNext()) {
            PendingCommand pending = iterator.next();
            pending.ticksRemaining--;
            if (pending.ticksRemaining <= 0) {
                String trimmed = pending.command.trim();
                if (!trimmed.isEmpty()) {
                    if (trimmed.startsWith("/")) {
                        trimmed = trimmed.substring(1);
                    }
                    server.getCommands().performPrefixedCommand(pending.source, trimmed);
                }
                iterator.remove();
            }
        }

        Set<UUID> seenEntities = new HashSet<>();
        for (ServerLevel level : server.getAllLevels()) {
            if (!TriggerAreaState.get(level).areas.isEmpty()) {
                tickLevel(level, seenEntities);
            }
        }
        INSIDE_AREAS.keySet().retainAll(seenEntities);
    }

    private static void tickLevel(ServerLevel level, Set<UUID> seenEntities) {
        Set<UUID> processed = new HashSet<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity.isVehicle() && !entity.getPassengers().isEmpty()) {
                continue;
            }
            if (!processed.add(entity.getUUID())) {
                continue;
            }
            seenEntities.add(entity.getUUID());
            tickEntity(level, entity);
        }
    }

    private static void tickEntity(ServerLevel level, Entity entity) {
        Set<UUID> inside = INSIDE_AREAS.computeIfAbsent(entity.getUUID(), uuid -> new HashSet<>());
        for (TriggerArea area : TriggerAreaState.get(level).areas) {
            if (area.guarded) {
                inside.remove(area.id);
                continue;
            }
            boolean fullyInside = area.contains(entity);
            boolean fullyOutside = !area.intersects(entity);
            boolean wasInside = inside.contains(area.id);
            if (fullyInside && !wasInside) {
                inside.add(area.id);
                trigger(area, entity);
            } else if (fullyOutside && wasInside) {
                inside.remove(area.id);
            }
        }
    }

    private static void trigger(TriggerArea area, Entity entity) {
        if (area.guarded || !TriggerArea.TYPE_COMMANDS.equals(area.triggerType) || (area.runOnce && area.runCount > 0) || !matchesTrigger(area, entity)) {
            return;
        }

        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }
        Entity sourceEntity = entity.isPassenger() ? entity.getRootVehicle() : entity;
        MinecraftServer server = level.getServer();
        CommandSourceStack source = new CommandSourceStack(
                server,
                sourceEntity.position(),
                sourceEntity.getRotationVector(),
                level,
                PermissionSet.ALL_PERMISSIONS,
                sourceEntity.getScoreboardName(),
                sourceEntity.getDisplayName(),
                server,
                sourceEntity
        );
        for (int i = 0; i < area.commands.size(); i++) {
            String command = area.commands.get(i);
            int delay = 0;
            if (i < area.commandDelays.size()) {
                delay = area.commandDelays.get(i);
            }
            if (delay <= 0) {
                String trimmed = command.trim();
                if (!trimmed.isEmpty()) {
                    if (trimmed.startsWith("/")) {
                        trimmed = trimmed.substring(1);
                    }
                    server.getCommands().performPrefixedCommand(source, trimmed);
                }
            } else {
                PENDING_COMMANDS.add(new PendingCommand(source, command, delay));
            }
        }
        area.runCount++;
        TriggerAreaState.get(level).setDirty();
        syncAll(server);
    }
    private static boolean matchesTrigger(TriggerArea area, Entity entity) {
        return "everyone".equals(area.triggerMode) || matchesSelectorList(area.triggerSelectors, entity);
    }

    private static boolean privateAppliesTo(TriggerArea area, Entity entity) {
        return matchesSelectorList(area.privateSelectors, entity);
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
        boolean removed = false;
        for (ServerLevel level : server.getAllLevels()) {
            TriggerArea area = TriggerAreaState.get(level).get(id);
            if (area != null && area.guarded) {
                return false;
            }
            removed |= TriggerAreaState.get(level).remove(id);
        }
        if (removed) {
            for (Set<UUID> inside : INSIDE_AREAS.values()) {
                inside.remove(id);
            }
            syncAll(server);
        }
        return removed;
    }

    public static boolean resetArea(MinecraftServer server, UUID id) {
        boolean reset = false;
        for (ServerLevel level : server.getAllLevels()) {
            TriggerArea area = TriggerAreaState.get(level).get(id);
            if (area != null) {
                if (area.guarded) {
                    return false;
                }
                area.runCount = 0;
                TriggerAreaState.get(level).setDirty();
                reset = true;
            }
        }
        if (reset) {
            syncAll(server);
        }
        return reset;
    }

    public static boolean toggleGuard(MinecraftServer server, UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            TriggerArea area = TriggerAreaState.get(level).get(id);
            if (area != null) {
                area.guarded = !area.guarded;
                TriggerAreaState.get(level).setDirty();
                if (area.guarded) {
                    for (Set<UUID> inside : INSIDE_AREAS.values()) {
                        inside.remove(id);
                    }
                }
                syncAll(server);
                return true;
            }
        }
        return false;
    }

    private static boolean isGuarded(MinecraftServer server, UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            TriggerArea area = TriggerAreaState.get(level).get(id);
            if (area != null) {
                return area.guarded;
            }
        }
        return false;
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
