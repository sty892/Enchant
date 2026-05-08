package me.guardian.client;

import me.guardian.GuardianMod;
import me.guardian.ModState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Mob;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;

public final class GuardianBossModel<T extends Mob & GeoEntity> extends DefaultedEntityGeoModel<T> {
    private static final Identifier FALLBACK_ASSET = Identifier.fromNamespaceAndPath(GuardianMod.MOD_ID, "boss_fallback");

    private final Identifier bossAsset;

    public GuardianBossModel(String bossAssetName) {
        super(Identifier.fromNamespaceAndPath(GuardianMod.MOD_ID, bossAssetName));
        this.bossAsset = Identifier.fromNamespaceAndPath(GuardianMod.MOD_ID, bossAssetName);
    }

    @Override
    public Identifier getModelResource(GeoRenderState renderState) {
        return buildFormattedModelPath(selectedAsset());
    }

    @Override
    public Identifier getTextureResource(GeoRenderState renderState) {
        return buildFormattedTexturePath(selectedAsset());
    }

    @Override
    public Identifier getAnimationResource(T animatable) {
        return buildFormattedAnimationPath(selectedAsset());
    }

    private Identifier selectedAsset() {
        return ModState.resourcePackLoaded ? bossAsset : FALLBACK_ASSET;
    }
}
