package me.guardian.client;

import me.guardian.GuardianMod;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Mob;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;

public final class GuardianBossModel<T extends Mob & GeoEntity> extends GeoModel<T> {
    private final String bossAssetName;
    private final Identifier bossModel;
    private final Identifier bossTexture;
    private final Identifier bossAnimation;
    private final Identifier fallbackModel;
    private final Identifier fallbackTexture;
    private final Identifier fallbackAnimation;

    public GuardianBossModel(String bossAssetName) {
        this.bossAssetName = bossAssetName;
        this.bossModel = asset("geckolib/models/entity/" + bossAssetName + ".geo.json");
        this.bossTexture = asset("textures/entity/" + bossAssetName + ".png");
        this.bossAnimation = asset("geckolib/animations/entity/" + bossAssetName + ".animation.json");
        this.fallbackModel = asset("geckolib/models/entity/boss_fallback.geo.json");
        this.fallbackTexture = asset("textures/entity/boss_fallback.png");
        this.fallbackAnimation = asset("geckolib/animations/entity/boss_fallback.animation.json");
    }

    @Override
    public Identifier getModelResource(GeoRenderState renderState) {
        return shouldUseRealAsset() ? bossModel : fallbackModel;
    }

    @Override
    public Identifier getTextureResource(GeoRenderState renderState) {
        return shouldUseRealAsset() ? bossTexture : fallbackTexture;
    }

    @Override
    public Identifier getAnimationResource(T animatable) {
        return shouldUseRealAsset() ? bossAnimation : fallbackAnimation;
    }

    private boolean shouldUseRealAsset() {
        return GuardianBossAssets.hasCompleteAsset(bossAssetName);
    }

    private static Identifier asset(String path) {
        return Identifier.fromNamespaceAndPath(GuardianMod.MOD_ID, path);
    }
}
