package me.guardian.client;

import me.guardian.GuardianMod;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

public final class GuardianAltarAssets {
    private static boolean hasModel;
    private static boolean hasTexture;
    private static boolean hasAnimation;
    private static Identifier texture;

    private GuardianAltarAssets() {
    }

    public static void reload(ResourceManager manager) {
        hasModel = GuardianResourcePackAssets.hasExternalResource(manager, asset("geckolib/models/block/altar.geo.json"));
        texture = findTexture(manager);
        hasTexture = texture != null;
        hasAnimation = GuardianResourcePackAssets.hasExternalResource(manager, asset("geckolib/animations/block/altar.animation.json"));
        GuardianMod.LOGGER.info("Guardian Mod altar assets detected: model={}, texture={}, animation={}", hasModel, hasTexture, hasAnimation);
    }

    public static boolean hasModel() {
        return hasModel;
    }

    public static boolean hasTexture() {
        return hasTexture;
    }

    public static Identifier texture() {
        return texture;
    }

    public static boolean hasAnimation() {
        return hasAnimation;
    }

    private static Identifier asset(String path) {
        return Identifier.fromNamespaceAndPath(GuardianMod.MOD_ID, path);
    }

    private static Identifier findTexture(ResourceManager manager) {
        Identifier standardTexture = asset("textures/block/altar.png");
        if (GuardianResourcePackAssets.hasExternalResource(manager, standardTexture)) {
            return standardTexture;
        }
        Identifier legacyTexture = asset("textures/block1/altar.png");
        return GuardianResourcePackAssets.hasExternalResource(manager, legacyTexture) ? legacyTexture : null;
    }
}
