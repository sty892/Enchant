package me.guardian.client;

import com.google.common.reflect.TypeToken;
import me.guardian.GuardianMod;
import me.guardian.entity.OverworldGuardianEntity;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Mob;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;

public final class GuardianBossModel<T extends Mob & GeoEntity> extends GeoModel<T> {
    private static final DataTicket<Integer> OVERWORLD_PHASE = DataTicket.create(
            "guardian_mod_overworld_phase",
            Integer.class,
            new TypeToken<>() {
            }
    );

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
        String modelAssetName = phaseAssetName(renderState);
        if (GuardianBossAssets.hasModelAsset(modelAssetName)) {
            return asset("entity/" + modelAssetName);
        }
        return GuardianBossAssets.hasModelAsset(bossAssetName) ? bossModel : fallbackModel;
    }

    @Override
    public Identifier getTextureResource(GeoRenderState renderState) {
        String textureAssetName = phaseAssetName(renderState);
        if (GuardianBossAssets.hasTextureAsset(textureAssetName)) {
            return asset("textures/entity/" + textureAssetName + ".png");
        }
        return GuardianBossAssets.hasTextureAsset(bossAssetName) ? bossTexture : fallbackTexture;
    }

    @Override
    public Identifier getAnimationResource(T animatable) {
        return GuardianBossAssets.hasAnimationAsset(bossAssetName) ? bossAnimation : fallbackAnimation;
    }

    @Override
    public void addAdditionalStateData(T animatable, Object relatedObject, GeoRenderState renderState) {
        if (animatable instanceof OverworldGuardianEntity guardian) {
            renderState.addGeckolibData(OVERWORLD_PHASE, guardian.getBossPhase().id());
        }
    }

    private static Identifier asset(String path) {
        return Identifier.fromNamespaceAndPath(GuardianMod.MOD_ID, path);
    }

    private String phaseAssetName(GeoRenderState renderState) {
        if (!"boss_overworld".equals(bossAssetName)) {
            return bossAssetName;
        }
        int phase = renderState.getOrDefaultGeckolibData(OVERWORLD_PHASE, 1);
        return switch (phase) {
            case 2 -> "boss_overworld_phase2";
            case 3 -> "boss_overworld_phase3";
            default -> bossAssetName;
        };
    }

    private static String fallbackName(String bossAssetName) {
        return "boss_nether".equals(bossAssetName) ? "boss_nether_fallback" : "boss_fallback";
    }
}
