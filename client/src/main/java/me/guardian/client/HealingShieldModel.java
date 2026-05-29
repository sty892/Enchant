package me.guardian.client;

import com.google.common.reflect.TypeToken;
import me.guardian.GuardianMod;
import me.guardian.entity.HealingShieldEntity;
import net.minecraft.resources.Identifier;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;

public final class HealingShieldModel extends GeoModel<HealingShieldEntity> {
    private static final DataTicket<Integer> STAGE = DataTicket.create(
            "guardian_mod_shield_stage", Integer.class, new TypeToken<>() {});

    private static final Identifier FALLBACK_MODEL = Identifier.fromNamespaceAndPath(GuardianMod.MOD_ID, "entity/healing_shield_fallback");
    private static final Identifier FALLBACK_ANIMATION = Identifier.fromNamespaceAndPath(GuardianMod.MOD_ID, "entity/boss_fallback");
    // Three breaking-stage textures (healthy / cracked / breaking).
    private static final Identifier[] TEXTURES = {
            Identifier.fromNamespaceAndPath(GuardianMod.MOD_ID, "textures/entity/healing_shield_stage0.png"),
            Identifier.fromNamespaceAndPath(GuardianMod.MOD_ID, "textures/entity/healing_shield_stage1.png"),
            Identifier.fromNamespaceAndPath(GuardianMod.MOD_ID, "textures/entity/healing_shield_stage2.png")
    };

    @Override
    public Identifier getModelResource(GeoRenderState renderState) {
        return FALLBACK_MODEL;
    }

    @Override
    public Identifier getTextureResource(GeoRenderState renderState) {
        int stage = renderState.getOrDefaultGeckolibData(STAGE, 0);
        return TEXTURES[Math.max(0, Math.min(2, stage))];
    }

    @Override
    public Identifier getAnimationResource(HealingShieldEntity animatable) {
        return FALLBACK_ANIMATION;
    }

    @Override
    public void addAdditionalStateData(HealingShieldEntity animatable, Object relatedObject, GeoRenderState renderState) {
        renderState.addGeckolibData(STAGE, animatable.getDamageStage());
    }
}
