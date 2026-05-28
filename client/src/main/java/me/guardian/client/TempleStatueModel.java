package me.guardian.client;

import me.guardian.GuardianMod;
import me.guardian.entity.TempleStatueEntity;
import net.minecraft.resources.Identifier;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;

public final class TempleStatueModel extends GeoModel<TempleStatueEntity> {
    private static final Identifier FALLBACK_MODEL = Identifier.fromNamespaceAndPath(GuardianMod.MOD_ID, "entity/temple_statue_fallback");
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(GuardianMod.MOD_ID, "textures/block/temple_statue.png");
    private static final Identifier FALLBACK_ANIMATION = Identifier.fromNamespaceAndPath(GuardianMod.MOD_ID, "entity/boss_fallback");

    @Override
    public Identifier getModelResource(GeoRenderState renderState) {
        return FALLBACK_MODEL;
    }

    @Override
    public Identifier getTextureResource(GeoRenderState renderState) {
        return TEXTURE;
    }

    @Override
    public Identifier getAnimationResource(TempleStatueEntity animatable) {
        return FALLBACK_ANIMATION;
    }
}
