package me.guardian.server;

import me.guardian.GuardianMod;
import me.guardian.config.ConfigManager;
import me.guardian.entity.OverworldGuardianEntity;
import me.guardian.entity.SummonedBlockCleaner;
import me.guardian.event.GuardianBossEventHooks;
import me.guardian.event.GuardianAltarPlacementHooks;
import me.guardian.event.GuardianAltarRitualHooks;
import me.guardian.event.GuardianEventExecutor;
import me.guardian.network.HandshakeC2SPayload;
import me.guardian.network.HandshakeOkS2CPayload;
import me.guardian.server.altar.AltarRitualManager;
import me.guardian.server.altar.GuardianPlayerUpgrades;
import me.guardian.server.boss.BossEventManager;
import me.guardian.server.command.GuardianCommand;
import me.guardian.server.command.GuardianCommandAttackSuggestions;
import me.guardian.server.event.BossEventSystem;
import me.guardian.server.event.GuardianEventScheduler;
import me.guardian.entity.CameraMarkerEntity;
import me.guardian.network.CameraPayloads;
import me.guardian.server.cutscene.CutsceneManager;
import me.guardian.server.event.KeyFoundEventHandler;
import me.guardian.server.event.ScriptRunner;
import me.guardian.server.restriction.DiamondRestrictionHandler;
import me.guardian.server.structure.StructureSpawner;
import me.guardian.server.trigger.TriggerAreaManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;

public final class GuardianModServer implements ModInitializer {
    @Override
    public void onInitialize() {
        ConfigManager.initialize();
        BossEventManager.initialize();
        GuardianEventScheduler.initialize();
        KeyFoundEventHandler.initialize();
        DiamondRestrictionHandler.initialize();
        TriggerAreaManager.initialize();
        SummonedBlockCleaner.initialize();
        CutsceneManager.initialize();
        AltarRitualManager.initialize();
        GuardianCommandAttackSuggestions.apply();
        GuardianCommand.initialize();
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!world.isClientSide() && entity instanceof CameraMarkerEntity cameraMarker && player instanceof ServerPlayer serverPlayer) {
                if (canEditCameraMarkers(serverPlayer)) {
                    ServerPlayNetworking.send(serverPlayer, new CameraPayloads.OpenEditor(
                            cameraMarker.getId(),
                            cameraMarker.getCutsceneId(),
                            cameraMarker.getIndex(),
                            cameraMarker.getDuration()
                    ));
                    return InteractionResult.SUCCESS;
                }
            }
            return InteractionResult.PASS;
        });
        GuardianAltarPlacementHooks.setPlacementHook((level, pos) -> StructureSpawner.placeImmediate(level, pos, "guardian_mod:altar"));
        GuardianAltarRitualHooks.setActivationHook(AltarRitualManager::tryActivateRitual);
        GuardianAltarRitualHooks.setSelectionHook(AltarRitualManager::selectAltarAspect);
        GuardianBossEventHooks.setSpawnHook(BossEventManager::triggerOnSpawn);
        GuardianBossEventHooks.setDeathHook(BossEventManager::triggerOnDeath);
        ServerTickEvents.END_SERVER_TICK.register(ScriptRunner::tick);
        GuardianEventExecutor.setExecutor((level, eventData, center, source) -> {
            if (level instanceof ServerLevel serverLevel) {
                BossEventSystem.executeEvent(serverLevel, eventData, center, source, java.util.Collections.emptyMap());
            }
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> GuardianPlayerUpgrades.reapplyAll(handler.player));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            CutsceneManager.stopCutsceneAfterRespawn(newPlayer);
            for (ServerLevel level : newPlayer.level().getServer().getAllLevels()) {
                for (Entity entity : level.getAllEntities()) {
                    if (entity instanceof OverworldGuardianEntity guardian) {
                        guardian.syncBossBarAfterRespawn(oldPlayer, newPlayer);
                    }
                }
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(HandshakeC2SPayload.TYPE, (payload, context) -> {
            context.responseSender().sendPacket(new HandshakeOkS2CPayload());
        });

        ServerPlayNetworking.registerGlobalReceiver(CameraPayloads.SaveEditor.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                if (!canEditCameraMarkers(context.player())) {
                    context.player().sendSystemMessage(net.minecraft.network.chat.Component.literal("§cYou are not allowed to edit camera markers."));
                    return;
                }
                net.minecraft.world.entity.Entity entity = ((ServerLevel) context.player().level()).getEntity(payload.entityId());
                if (entity instanceof CameraMarkerEntity marker) {
                    marker.setCutsceneId(payload.cutsceneId());
                    marker.setIndex(payload.index());
                    marker.setDuration(payload.duration());
                    context.player().sendSystemMessage(net.minecraft.network.chat.Component.literal("§aSaved camera point settings."));
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(CameraPayloads.Delete.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                if (!canEditCameraMarkers(context.player())) {
                    context.player().sendSystemMessage(net.minecraft.network.chat.Component.literal("§cYou are not allowed to edit camera markers."));
                    return;
                }
                net.minecraft.world.entity.Entity entity = ((ServerLevel) context.player().level()).getEntity(payload.entityId());
                if (entity instanceof CameraMarkerEntity marker) {
                    marker.discard();
                    context.player().sendSystemMessage(net.minecraft.network.chat.Component.literal("§aDeleted camera point."));
                }
            });
        });

        GuardianMod.LOGGER.info("Guardian Mod server foundation initialized");
    }

    private static boolean canEditCameraMarkers(ServerPlayer player) {
        return player.isCreative() || player.level().getServer().getPlayerList().isOp(player.nameAndId());
    }
}
