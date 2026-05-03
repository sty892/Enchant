package me.guardian;

import me.guardian.block.ModBlocks;
import me.guardian.config.ConfigManager;
import me.guardian.entity.ModEntities;
import me.guardian.item.ModItems;
import me.guardian.network.HandshakeC2SPayload;
import me.guardian.network.HandshakeOkS2CPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GuardianMod implements ModInitializer {
    public static final String MOD_ID = "guardian_mod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ConfigManager.initialize();
        ModItems.initialize();
        ModBlocks.initialize();
        ModEntities.initialize();

        PayloadTypeRegistry.playC2S().register(HandshakeC2SPayload.TYPE, HandshakeC2SPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(HandshakeOkS2CPayload.TYPE, HandshakeOkS2CPayload.CODEC);

        LOGGER.info("Guardian Mod common foundation initialized");
    }
}
