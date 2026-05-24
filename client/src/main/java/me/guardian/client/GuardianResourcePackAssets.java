package me.guardian.client;

import me.guardian.GuardianMod;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

public final class GuardianResourcePackAssets {
    private static final String MOD_PACK_ID = "mod/" + GuardianMod.MOD_ID;

    private GuardianResourcePackAssets() {
    }

    public static boolean hasExternalResource(ResourceManager manager, Identifier asset) {
        for (Resource resource : manager.getResourceStack(asset)) {
            if (!isModResource(resource.sourcePackId())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isModResource(String sourcePackId) {
        return GuardianMod.MOD_ID.equals(sourcePackId) || MOD_PACK_ID.equals(sourcePackId);
    }
}
