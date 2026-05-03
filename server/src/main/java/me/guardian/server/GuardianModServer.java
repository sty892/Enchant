package me.guardian.server;

import me.guardian.GuardianMod;
import me.guardian.config.ConfigManager;
import net.fabricmc.api.ModInitializer;

public final class GuardianModServer implements ModInitializer {
    @Override
    public void onInitialize() {
        ConfigManager.initialize();
        GuardianMod.LOGGER.info("Guardian Mod server foundation initialized");
    }
}
