package me.guardian.client;

import me.guardian.GuardianMod;
import me.guardian.ModState;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class GuardianBossAssets {
    private static final Set<String> MODEL_ASSETS = new HashSet<>();
    private static final Set<String> TEXTURE_ASSETS = new HashSet<>();
    private static final Set<String> ANIMATION_ASSETS = new HashSet<>();
    private static final String[] BOSS_ASSET_NAMES = {
            "boss_overworld",
            "boss_overworld_phase2",
            "boss_overworld_phase3",
            "boss_nether",
            "boss_generic"
    };

    private GuardianBossAssets() {
    }

    public static void reload(ResourceManager manager) {
        MODEL_ASSETS.clear();
        TEXTURE_ASSETS.clear();
        ANIMATION_ASSETS.clear();
        for (String bossAssetName : BOSS_ASSET_NAMES) {
            boolean hasModel = hasModelAsset(manager, bossAssetName);
            boolean hasTexture = hasTextureAsset(manager, bossAssetName);
            boolean hasAnimation = hasAnimationAsset(manager, bossAssetName);
            if (hasModel) {
                MODEL_ASSETS.add(bossAssetName);
            }
            if (hasTexture) {
                TEXTURE_ASSETS.add(bossAssetName);
            }
            if (hasAnimation) {
                ANIMATION_ASSETS.add(bossAssetName);
            }
            if (!hasModel || !hasTexture) {
                GuardianMod.LOGGER.info(
                        "Guardian Mod boss asset check for {}: model={}, texture={}, animation={}",
                        bossAssetName,
                        hasModel,
                        hasTexture,
                        hasAnimation
                );
            }
        }
        ModState.resourcePackLoaded = !MODEL_ASSETS.isEmpty();
        GuardianMod.LOGGER.info("Guardian Mod boss model assets detected: {}, texture assets detected: {}, animation assets detected: {}",
                Collections.unmodifiableSet(MODEL_ASSETS),
                Collections.unmodifiableSet(TEXTURE_ASSETS),
                Collections.unmodifiableSet(ANIMATION_ASSETS));
        if (!ModState.resourcePackLoaded) {
            GuardianMod.LOGGER.warn("Guardian Mod boss model assets not detected; fallback boss models will be used.");
        }
    }

    public static boolean hasModelAsset(String bossAssetName) {
        return MODEL_ASSETS.contains(bossAssetName);
    }

    public static boolean hasTextureAsset(String bossAssetName) {
        return TEXTURE_ASSETS.contains(bossAssetName);
    }

    public static boolean hasAnimationAsset(String bossAssetName) {
        return ANIMATION_ASSETS.contains(bossAssetName);
    }

    private static boolean hasModelAsset(ResourceManager manager, String bossAssetName) {
        return manager.getResource(asset("geckolib/models/entity/" + bossAssetName + ".geo.json")).isPresent();
    }

    private static boolean hasTextureAsset(ResourceManager manager, String bossAssetName) {
        return manager.getResource(asset("textures/entity/" + bossAssetName + ".png")).isPresent();
    }

    private static boolean hasAnimationAsset(ResourceManager manager, String bossAssetName) {
        return manager.getResource(asset("geckolib/animations/entity/" + bossAssetName + ".animation.json")).isPresent();
    }

    private static Identifier asset(String path) {
        return Identifier.fromNamespaceAndPath(GuardianMod.MOD_ID, path);
    }
}
