package me.guardian.client;

import me.guardian.GuardianMod;
import me.guardian.entity.HealingShieldEntity;
import net.minecraft.resources.Identifier;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;

public final class HealingShieldModel extends GeoModel<HealingShieldEntity> {
    private static final Identifier FALLBACK_MODEL = Identifier.fromNamespaceAndPath(GuardianMod.MOD_ID, "entity/healing_shield_fallback");
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(GuardianMod.MOD_ID, "textures/block/altar_protection.png");
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
    public Identifier getAnimationResource(HealingShieldEntity animatable) {
        return FALLBACK_ANIMATION;
    }
}
