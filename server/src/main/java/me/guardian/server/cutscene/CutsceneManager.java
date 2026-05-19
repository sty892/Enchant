package me.guardian.server.cutscene;

import me.guardian.entity.CameraMarkerEntity;
import me.guardian.server.state.GuardianWorldState;
import me.guardian.server.state.GuardianWorldState.CutsceneSession;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;

import java.util.*;

public final class CutsceneManager {
    private CutsceneManager() {
    }

    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(CutsceneManager::tick);
        ServerTickEvents.END_SERVER_TICK.register(CutsceneManager::checkTriggers);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayer player = handler.player;
            if (player != null) {
                stopCutscene(player);
            }
        });
    }

    public static void startCutscene(ServerPlayer player, String cutsceneId) {
        MinecraftServer server = player.level().getServer();
        if (server == null) return;

        GuardianWorldState worldState = GuardianWorldState.get(server.overworld());
        // Check if already in a session
        for (CutsceneSession session : worldState.getActiveSessions()) {
            if (session.playerUuid.equals(player.getUUID().toString())) {
                return;
            }
        }

        // Gather and sort camera markers
        List<CameraMarkerEntity> markers = findMarkers(server, cutsceneId);
        if (markers.isEmpty()) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cNo camera markers found for cutscene: " + cutsceneId));
            return;
        }

        // Save player state
        String origGameMode = player.gameMode.getGameModeForPlayer().getName();
        double origX = player.getX();
        double origY = player.getY();
        double origZ = player.getZ();
        float origYaw = player.getYRot();
        float origPitch = player.getXRot();
        String origDim = player.level().dimension().identifier().toString();

        CameraMarkerEntity firstCamera = markers.get(0);
        int initialTicks = (int) (firstCamera.getDuration() * 20);
        if (initialTicks <= 0) initialTicks = 100; // fallback to 5 seconds

        CutsceneSession session = new CutsceneSession(
                player.getUUID().toString(),
                cutsceneId,
                0,
                initialTicks,
                origGameMode,
                origX, origY, origZ,
                origYaw, origPitch,
                origDim
        );

        worldState.getActiveSessions().add(session);
        worldState.markChanged();

        // Put player in spectator
        player.setGameMode(GameType.SPECTATOR);
        player.teleportTo(
                (ServerLevel) firstCamera.level(),
                firstCamera.getX(),
                firstCamera.getY(),
                firstCamera.getZ(),
                Collections.emptySet(),
                firstCamera.getYRot(),
                firstCamera.getXRot(),
                true
        );
        player.setCamera(firstCamera);
    }

    public static void stopCutscene(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null) return;

        GuardianWorldState worldState = GuardianWorldState.get(server.overworld());
        String playerUuid = player.getUUID().toString();
        CutsceneSession foundSession = null;

        for (CutsceneSession session : worldState.getActiveSessions()) {
            if (session.playerUuid.equals(playerUuid)) {
                foundSession = session;
                break;
            }
        }

        if (foundSession != null) {
            worldState.getActiveSessions().remove(foundSession);
            worldState.markChanged();
            restorePlayer(player, foundSession);
        }
    }

    private static void restorePlayer(ServerPlayer player, CutsceneSession session) {
        player.setCamera(player);

        MinecraftServer server = player.level().getServer();
        if (server != null) {
            ServerLevel origLevel = server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(session.originalDimension)));
            if (origLevel != null) {
                player.teleportTo(origLevel, session.originalX, session.originalY, session.originalZ, Collections.emptySet(), session.originalYaw, session.originalPitch, true);
            }
        }

        GameType mode = GameType.byName(session.originalGameMode, GameType.SURVIVAL);
        player.setGameMode(mode);
    }

    private static void tick(MinecraftServer server) {
        GuardianWorldState worldState = GuardianWorldState.get(server.overworld());
        List<CutsceneSession> sessions = new ArrayList<>(worldState.getActiveSessions());
        if (sessions.isEmpty()) return;

        boolean changed = false;
        for (CutsceneSession session : sessions) {
            ServerPlayer player = server.getPlayerList().getPlayer(UUID.fromString(session.playerUuid));
            if (player == null) {
                worldState.getActiveSessions().remove(session);
                changed = true;
                continue;
            }

            session.ticksLeft--;
            List<CameraMarkerEntity> markers = findMarkers(server, session.cutsceneId);

            if (markers.isEmpty() || session.cameraIndex >= markers.size()) {
                worldState.getActiveSessions().remove(session);
                restorePlayer(player, session);
                changed = true;
                continue;
            }

            CameraMarkerEntity currentCamera = markers.get(session.cameraIndex);

            if (player.getCamera() != currentCamera) {
                player.setCamera(currentCamera);
            }
            if (player.distanceToSqr(currentCamera) > 0.01) {
                player.teleportTo(
                        (ServerLevel) currentCamera.level(),
                        currentCamera.getX(),
                        currentCamera.getY(),
                        currentCamera.getZ(),
                        Collections.emptySet(),
                        currentCamera.getYRot(),
                        currentCamera.getXRot(),
                        true
                );
            }

            if (session.ticksLeft <= 0) {
                session.cameraIndex++;
                if (session.cameraIndex >= markers.size()) {
                    worldState.getActiveSessions().remove(session);
                    restorePlayer(player, session);
                    changed = true;
                } else {
                    CameraMarkerEntity nextCamera = markers.get(session.cameraIndex);
                    session.ticksLeft = (int) (nextCamera.getDuration() * 20);
                    if (session.ticksLeft <= 0) session.ticksLeft = 100;
                    player.teleportTo(
                            (ServerLevel) nextCamera.level(),
                            nextCamera.getX(),
                            nextCamera.getY(),
                            nextCamera.getZ(),
                            Collections.emptySet(),
                            nextCamera.getYRot(),
                            nextCamera.getXRot(),
                            true
                    );
                    player.setCamera(nextCamera);
                    changed = true;
                }
            }
        }

        if (changed) {
            worldState.markChanged();
        }
    }

    private static void checkTriggers(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator() || !player.isAlive()) {
                continue;
            }

            GuardianWorldState worldState = GuardianWorldState.get((ServerLevel) player.level());
            boolean inCutscene = false;
            for (CutsceneSession session : worldState.getActiveSessions()) {
                if (session.playerUuid.equals(player.getUUID().toString())) {
                    inCutscene = true;
                    break;
                }
            }
            if (inCutscene) {
                continue;
            }

            // 1. on_diamond_pickup
            if (!worldState.hasTriggeredEvent(player.getUUID().toString(), "on_diamond_pickup")) {
                if (hasDiamond(player)) {
                    worldState.addTriggeredEvent(player.getUUID().toString(), "on_diamond_pickup");
                    startCutscene(player, "on_diamond_pickup");
                    continue;
                }
            }

            // 2. on_border_approach
            if (!worldState.hasTriggeredEvent(player.getUUID().toString(), "on_border_approach")) {
                double dist = player.level().getWorldBorder().getDistanceToBorder(player);
                if (dist < 2.0) {
                    worldState.addTriggeredEvent(player.getUUID().toString(), "on_border_approach");
                    startCutscene(player, "on_border_approach");
                    continue;
                }
            }

            // 3. on_nether_portal_enter
            if (!worldState.hasTriggeredEvent(player.getUUID().toString(), "on_nether_portal_enter")) {
                if (player.level().getBlockState(player.blockPosition()).is(net.minecraft.world.level.block.Blocks.NETHER_PORTAL)
                        || player.level().getBlockState(player.blockPosition().above()).is(net.minecraft.world.level.block.Blocks.NETHER_PORTAL)) {
                    worldState.addTriggeredEvent(player.getUUID().toString(), "on_nether_portal_enter");
                    startCutscene(player, "on_nether_portal_enter");
                    continue;
                }
            }
        }
    }

    private static boolean hasDiamond(ServerPlayer player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            net.minecraft.world.item.ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.getItem() == net.minecraft.world.item.Items.DIAMOND) {
                return true;
            }
        }
        return false;
    }

    private static List<CameraMarkerEntity> findMarkers(MinecraftServer server, String cutsceneId) {
        List<CameraMarkerEntity> markers = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof CameraMarkerEntity marker) {
                    if (cutsceneId.equals(marker.getCutsceneId())) {
                        markers.add(marker);
                    }
                }
            }
        }
        markers.sort(Comparator.comparingInt(CameraMarkerEntity::getIndex));
        return markers;
    }
}
