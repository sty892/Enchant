package me.guardian.client;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

public final class GuardianResourcePackAssets {
    private GuardianResourcePackAssets() {
    }

    public static boolean hasExternalResource(ResourceManager manager, Identifier asset) {
        return manager.getResource(asset).isPresent();
    }
}
