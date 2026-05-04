package me.guardian;

import me.guardian.block.ModBlocks;
import me.guardian.config.ConfigManager;
import me.guardian.entity.ModEntities;
import me.guardian.item.ModItems;
import me.guardian.network.GuardianNetworking;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GuardianMod implements ModInitializer {
    public static final String MOD_ID = "guardian_mod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        GuardianNetworking.registerPayloadTypes();
        ConfigManager.initialize();
        ModItems.initialize();
        ModBlocks.initialize();
        ModEntities.initialize();

        LOGGER.info("Guardian Mod common foundation initialized");
    }
}
