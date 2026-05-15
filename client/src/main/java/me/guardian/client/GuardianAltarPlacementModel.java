package me.guardian.client;

import me.guardian.GuardianMod;
import me.guardian.entity.AltarPlacementEntity;
import net.minecraft.resources.Identifier;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;

public final class GuardianAltarPlacementModel extends GeoModel<AltarPlacementEntity> {
    private static final Identifier ALTAR_MODEL = asset("block/altar");
    private static final Identifier ALTAR_TEXTURE = asset("textures/block/altar.png");
    private static final Identifier ALTAR_ANIMATION = asset("block/altar");
    private static final Identifier FALLBACK_MODEL = asset("block/altar_fallback");
    private static final Identifier FALLBACK_TEXTURE = asset("textures/block/altar_core.png");
    private static final Identifier FALLBACK_ANIMATION = asset("block/altar_fallback");

    @Override
    public Identifier getModelResource(GeoRenderState renderState) {
        return GuardianAltarAssets.hasModel() ? ALTAR_MODEL : FALLBACK_MODEL;
    }

    @Override
    public Identifier getTextureResource(GeoRenderState renderState) {
        return GuardianAltarAssets.hasTexture() ? ALTAR_TEXTURE : FALLBACK_TEXTURE;
    }

    @Override
    public Identifier getAnimationResource(AltarPlacementEntity animatable) {
        return GuardianAltarAssets.hasAnimation() ? ALTAR_ANIMATION : FALLBACK_ANIMATION;
    }

    private static Identifier asset(String path) {
        return Identifier.fromNamespaceAndPath(GuardianMod.MOD_ID, path);
    }
}
