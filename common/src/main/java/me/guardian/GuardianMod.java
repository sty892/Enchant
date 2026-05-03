package me.guardian;

import me.guardian.config.ConfigManager;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GuardianMod implements ModInitializer {
    public static final String MOD_ID = "guardian_mod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ConfigManager.initialize();
        LOGGER.info("Guardian Mod common foundation initialized");
    }
}
