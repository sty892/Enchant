package me.guardian.client;

import me.guardian.GuardianMod;
import me.guardian.entity.CameraMarkerEntity;
import net.minecraft.resources.Identifier;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;

public final class CameraMarkerModel extends GeoModel<CameraMarkerEntity> {
    private static final Identifier MODEL = asset("entity/camera_marker");
    private static final Identifier TEXTURE = asset("textures/entity/camera_marker.png");

    @Override
    public Identifier getModelResource(GeoRenderState renderState) {
        return MODEL;
    }

    @Override
    public Identifier getTextureResource(GeoRenderState renderState) {
        return TEXTURE;
    }

    @Override
    public Identifier getAnimationResource(CameraMarkerEntity animatable) {
        return asset("entity/boss_fallback");
    }

    private static Identifier asset(String path) {
        return Identifier.fromNamespaceAndPath(GuardianMod.MOD_ID, path);
    }
}
