package me.guardian.client;

import me.guardian.GuardianMod;
import me.guardian.ModState;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class GuardianBossAssets {
    private static final Set<String> COMPLETE_ASSETS = new HashSet<>();
    private static final String[] BOSS_ASSET_NAMES = {
            "boss_overworld",
            "boss_nether",
            "boss_generic"
    };

    private GuardianBossAssets() {
    }

    public static void reload(ResourceManager manager) {
        COMPLETE_ASSETS.clear();
        for (String bossAssetName : BOSS_ASSET_NAMES) {
            if (hasCompleteAsset(manager, bossAssetName)) {
                COMPLETE_ASSETS.add(bossAssetName);
            }
        }
        ModState.resourcePackLoaded = !COMPLETE_ASSETS.isEmpty();
        GuardianMod.LOGGER.info("Guardian Mod complete boss asset sets detected: {}", Collections.unmodifiableSet(COMPLETE_ASSETS));
        if (!ModState.resourcePackLoaded) {
            GuardianMod.LOGGER.warn("Guardian Mod boss resource pack assets not detected; fallback boss assets will be used.");
        }
    }

    public static boolean hasCompleteAsset(String bossAssetName) {
        return COMPLETE_ASSETS.contains(bossAssetName);
    }

    private static boolean hasCompleteAsset(ResourceManager manager, String bossAssetName) {
        return manager.getResource(asset("geckolib/models/entity/" + bossAssetName + ".geo.json")).isPresent()
                && manager.getResource(asset("textures/entity/" + bossAssetName + ".png")).isPresent()
                && manager.getResource(asset("geckolib/animations/entity/" + bossAssetName + ".animation.json")).isPresent();
    }

    private static Identifier asset(String path) {
        return Identifier.fromNamespaceAndPath(GuardianMod.MOD_ID, path);
    }
}
