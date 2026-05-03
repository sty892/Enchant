package me.guardian.client;

import me.guardian.GuardianMod;
import net.fabricmc.api.ClientModInitializer;

public final class GuardianModClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        GuardianMod.LOGGER.info("Guardian Mod client foundation initialized");
    }
}
