package me.sty892.enchant.entity.renderer;

import me.sty892.enchant.entity.model.GuardianBossModel;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.entity.mob.HostileEntity;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class GuardianBossRenderer<T extends HostileEntity & GeoEntity> extends GeoEntityRenderer<T> {
    public GuardianBossRenderer(EntityRendererFactory.Context renderManager, String bossName) {
        super(renderManager, new GuardianBossModel<>(bossName));
    }
}
