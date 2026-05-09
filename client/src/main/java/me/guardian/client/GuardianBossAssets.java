package me.guardian.client;

import me.guardian.GuardianMod;
import me.guardian.ModState;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class GuardianBossAssets {
    private static final Set<String> VISIBLE_ASSETS = new HashSet<>();
    private static final Set<String> ANIMATION_ASSETS = new HashSet<>();
    private static final String[] BOSS_ASSET_NAMES = {
            "boss_overworld",
            "boss_nether",
            "boss_generic"
    };

    private GuardianBossAssets() {
    }

    public static void reload(ResourceManager manager) {
        VISIBLE_ASSETS.clear();
        ANIMATION_ASSETS.clear();
        for (String bossAssetName : BOSS_ASSET_NAMES) {
            if (hasVisibleAsset(manager, bossAssetName)) {
                VISIBLE_ASSETS.add(bossAssetName);
            }
            if (hasAnimationAsset(manager, bossAssetName)) {
                ANIMATION_ASSETS.add(bossAssetName);
            }
        }
        ModState.resourcePackLoaded = !VISIBLE_ASSETS.isEmpty();
        GuardianMod.LOGGER.info("Guardian Mod visible boss asset sets detected: {}, animation sets detected: {}",
                Collections.unmodifiableSet(VISIBLE_ASSETS), Collections.unmodifiableSet(ANIMATION_ASSETS));
        if (!ModState.resourcePackLoaded) {
            GuardianMod.LOGGER.warn("Guardian Mod boss resource pack assets not detected; fallback boss assets will be used.");
        }
    }

    public static boolean hasVisibleAsset(String bossAssetName) {
        return VISIBLE_ASSETS.contains(bossAssetName);
    }

    public static boolean hasAnimationAsset(String bossAssetName) {
        return ANIMATION_ASSETS.contains(bossAssetName);
    }

    private static boolean hasVisibleAsset(ResourceManager manager, String bossAssetName) {
        return manager.getResource(asset("geckolib/models/entity/" + bossAssetName + ".geo.json")).isPresent()
                && manager.getResource(asset("textures/entity/" + bossAssetName + ".png")).isPresent();
    }

    private static boolean hasAnimationAsset(ResourceManager manager, String bossAssetName) {
        return manager.getResource(asset("geckolib/animations/entity/" + bossAssetName + ".animation.json")).isPresent();
    }

    private static Identifier asset(String path) {
        return Identifier.fromNamespaceAndPath(GuardianMod.MOD_ID, path);
    }
}
