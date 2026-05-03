package me.guardian.server;

import me.guardian.GuardianMod;
import me.guardian.config.ConfigManager;
import me.guardian.event.GuardianBossEventHooks;
import me.guardian.event.GuardianAltarRitualHooks;
import me.guardian.event.GuardianEventExecutor;
import me.guardian.network.HandshakeC2SPayload;
import me.guardian.network.HandshakeOkS2CPayload;
import me.guardian.server.altar.AltarRitualManager;
import me.guardian.server.altar.GuardianPlayerUpgrades;
import me.guardian.server.boss.BossEventManager;
import me.guardian.server.command.GuardianCommand;
import me.guardian.server.event.BossEventSystem;
import me.guardian.server.restriction.DiamondRestrictionHandler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;

public final class GuardianModServer implements ModInitializer {
    @Override
    public void onInitialize() {
        ConfigManager.initialize();
        BossEventManager.initialize();
        DiamondRestrictionHandler.initialize();
        AltarRitualManager.initialize();
        GuardianCommand.initialize();
        GuardianAltarRitualHooks.setActivationHook(AltarRitualManager::tryActivateRitual);
        GuardianBossEventHooks.setSpawnHook(BossEventManager::triggerOnSpawn);
        GuardianBossEventHooks.setDeathHook(BossEventManager::triggerOnDeath);
        GuardianEventExecutor.setExecutor((level, eventData, center, source) -> {
            if (level instanceof ServerLevel serverLevel) {
                BossEventSystem.executeEvent(serverLevel, eventData, center, source, java.util.Collections.emptyMap());
            }
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> GuardianPlayerUpgrades.reapplyAll(handler.player));
        
        ServerPlayNetworking.registerGlobalReceiver(HandshakeC2SPayload.TYPE, (payload, context) -> {
            context.responseSender().sendPacket(new HandshakeOkS2CPayload());
        });

        GuardianMod.LOGGER.info("Guardian Mod server foundation initialized");
    }
}
