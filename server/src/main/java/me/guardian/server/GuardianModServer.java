package me.guardian.server;

import me.guardian.GuardianMod;
import me.guardian.config.ConfigManager;
import me.guardian.network.HandshakeC2SPayload;
import me.guardian.network.HandshakeOkS2CPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public final class GuardianModServer implements ModInitializer {
    @Override
    public void onInitialize() {
        ConfigManager.initialize();
        
        ServerPlayNetworking.registerGlobalReceiver(HandshakeC2SPayload.TYPE, (payload, context) -> {
            context.responseSender().sendPacket(new HandshakeOkS2CPayload());
        });

        GuardianMod.LOGGER.info("Guardian Mod server foundation initialized");
    }
}
