package me.sty892.enchant;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import me.sty892.enchant.networking.HandshakePayload;
import me.sty892.enchant.networking.HandshakeOkPayload;
import me.sty892.enchant.entity.OverworldGuardianEntity;
import me.sty892.enchant.entity.NetherGuardianEntity;
import me.sty892.enchant.entity.GenericBossEntity;
import me.sty892.enchant.event.boss.BossEventSystem;
import me.sty892.enchant.event.DiamondRestrictionHandler;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import me.sty892.enchant.command.GuardianCommand;
import me.sty892.enchant.config.ConfigManager;
import me.sty892.enchant.event.boss.BossConfigManager;

public class GuardianModServer implements DedicatedServerModInitializer {
    @Override
    public void onInitializeServer() {
        GuardianModCommon.LOGGER.info("Guardian Mod Server Initialized");

        ConfigManager.load();
        BossConfigManager.load();

        ServerPlayNetworking.registerGlobalReceiver(HandshakePayload.ID, (payload, context) -> {
            context.responseSender().sendPacket(new HandshakeOkPayload());
        });

        OverworldGuardianEntity.deathCallback = (boss, world) -> {
            BossEventSystem.triggerOnDeath(boss, world, boss.getDamageContributors().keySet());
        };

        NetherGuardianEntity.deathCallback = (boss, world) -> {
            BossEventSystem.triggerOnDeath(boss, world, boss.getDamageContributors().keySet());
        };

        GenericBossEntity.deathCallback = (boss, world) -> {
            if (!boss.isSummoned()) {
                BossEventSystem.triggerOnDeath(boss, world, boss.getDamageContributors().keySet());
            }
        };

        DiamondRestrictionHandler.register();

        CommandRegistrationCallback.EVENT.register(GuardianCommand::register);
    }
}
