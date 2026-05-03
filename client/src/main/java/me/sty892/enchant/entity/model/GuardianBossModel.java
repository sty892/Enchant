package me.sty892.enchant.entity.model;

import me.sty892.enchant.ModState;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.model.GeoModel;

public class GuardianBossModel<T extends HostileEntity & GeoEntity> extends GeoModel<T> {
    private final String bossName;

    public GuardianBossModel(String bossName) {
        this.bossName = bossName;
    }

    @Override
    public Identifier getModelResource(T animatable) {
        if (!ModState.resourcePackLoaded) {
            return Identifier.of("guardian_mod", "geo/entity/boss_fallback.geo.json");
        }
        return Identifier.of("guardian_mod", "geo/entity/" + bossName + ".geo.json");
    }

    @Override
    public Identifier getTextureResource(T animatable) {
        if (!ModState.resourcePackLoaded) {
            return Identifier.of("guardian_mod", "textures/entity/boss_fallback.png");
        }
        return Identifier.of("guardian_mod", "textures/entity/" + bossName + ".png");
    }

    @Override
    public Identifier getAnimationResource(T animatable) {
        if (!ModState.resourcePackLoaded) {
            return Identifier.of("guardian_mod", "animations/entity/boss_fallback.animation.json");
        }
        return Identifier.of("guardian_mod", "animations/entity/" + bossName + ".animation.json");
    }
}
