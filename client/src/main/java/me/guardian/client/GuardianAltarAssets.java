package me.guardian.client;

import me.guardian.GuardianMod;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

public final class GuardianAltarAssets {
    private static boolean hasModel;
    private static boolean hasTexture;
    private static boolean hasAnimation;

    private GuardianAltarAssets() {
    }

    public static void reload(ResourceManager manager) {
        hasModel = manager.getResource(asset("geckolib/models/block/altar.geo.json")).isPresent();
        hasTexture = manager.getResource(asset("textures/block/altar.png")).isPresent();
        hasAnimation = manager.getResource(asset("geckolib/animations/block/altar.animation.json")).isPresent();
        GuardianMod.LOGGER.info("Guardian Mod altar assets detected: model={}, texture={}, animation={}", hasModel, hasTexture, hasAnimation);
    }

    public static boolean hasModel() {
        return hasModel;
    }

    public static boolean hasTexture() {
        return hasTexture;
    }

    public static boolean hasAnimation() {
        return hasAnimation;
    }

    private static Identifier asset(String path) {
        return Identifier.fromNamespaceAndPath(GuardianMod.MOD_ID, path);
    }
}
