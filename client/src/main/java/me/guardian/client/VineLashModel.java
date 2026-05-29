package me.guardian.client;

import me.guardian.GuardianMod;
import me.guardian.entity.VineLashEntity;
import net.minecraft.resources.Identifier;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;

public final class VineLashModel extends GeoModel<VineLashEntity> {
    private static final Identifier MODEL = Identifier.fromNamespaceAndPath(GuardianMod.MOD_ID, "entity/vine_lash_fallback");
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(GuardianMod.MOD_ID, "textures/entity/vine_lash.png");
    private static final Identifier ANIMATION = Identifier.fromNamespaceAndPath(GuardianMod.MOD_ID, "entity/boss_fallback");

    @Override
    public Identifier getModelResource(GeoRenderState renderState) {
        return MODEL;
    }

    @Override
    public Identifier getTextureResource(GeoRenderState renderState) {
        return TEXTURE;
    }

    @Override
    public Identifier getAnimationResource(VineLashEntity animatable) {
        return ANIMATION;
    }
}
