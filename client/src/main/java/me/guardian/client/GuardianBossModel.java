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
        this.bossModel = asset("entity/" + bossAssetName);
        this.bossTexture = asset("textures/entity/" + bossAssetName + ".png");
        this.bossAnimation = asset("entity/" + bossAssetName);
        this.fallbackModel = asset("entity/" + fallbackName(bossAssetName));
        this.fallbackTexture = asset("textures/entity/" + fallbackName(bossAssetName) + ".png");
        this.fallbackAnimation = asset("entity/boss_fallback");
    }

    @Override
    public Identifier getModelResource(GeoRenderState renderState) {
        return GuardianBossAssets.hasModelAsset(bossAssetName) ? bossModel : fallbackModel;
    }

    @Override
    public Identifier getTextureResource(GeoRenderState renderState) {
        return GuardianBossAssets.hasTextureAsset(bossAssetName) ? bossTexture : fallbackTexture;
    }

    @Override
    public Identifier getAnimationResource(T animatable) {
        return GuardianBossAssets.hasAnimationAsset(bossAssetName) ? bossAnimation : fallbackAnimation;
    }

    private static Identifier asset(String path) {
        return Identifier.fromNamespaceAndPath(GuardianMod.MOD_ID, path);
    }

    private static String fallbackName(String bossAssetName) {
        return "boss_nether".equals(bossAssetName) ? "boss_nether_fallback" : "boss_fallback";
    }
}
