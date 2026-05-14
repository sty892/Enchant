package me.guardian.server;

import me.guardian.GuardianMod;
import me.guardian.config.ConfigManager;
import me.guardian.entity.OverworldGuardianEntity;
import me.guardian.entity.SummonedBlockCleaner;
import me.guardian.event.GuardianBossEventHooks;
import me.guardian.event.GuardianAltarRitualHooks;
import me.guardian.event.GuardianEventExecutor;
import me.guardian.network.HandshakeC2SPayload;
import me.guardian.network.HandshakeOkS2CPayload;
import me.guardian.network.GuardianNetworking;
import me.guardian.server.altar.AltarRitualManager;
import me.guardian.server.altar.GuardianPlayerUpgrades;
import me.guardian.server.boss.BossEventManager;
import me.guardian.server.command.GuardianCommand;
import me.guardian.server.event.BossEventSystem;
import me.guardian.server.event.GuardianEventScheduler;
import me.guardian.server.event.KeyFoundEventHandler;
import me.guardian.server.event.ScriptRunner;
import me.guardian.server.restriction.DiamondRestrictionHandler;
import me.guardian.server.trigger.TriggerAreaManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerLevel;

public final class GuardianModServer implements ModInitializer {
    @Override
    public void onInitialize() {
        GuardianNetworking.registerPayloadTypes();
        ConfigManager.initialize();
        BossEventManager.initialize();
        GuardianEventScheduler.initialize();
        KeyFoundEventHandler.initialize();
        DiamondRestrictionHandler.initialize();
        TriggerAreaManager.initialize();
        SummonedBlockCleaner.initialize();
        AltarRitualManager.initialize();
        GuardianCommand.initialize();
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

        GuardianMod.LOGGER.info("Guardian Mod server foundation initialized");
    }
}
