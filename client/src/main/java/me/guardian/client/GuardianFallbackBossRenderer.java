package me.guardian.client;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Mob;

public final class GuardianFallbackBossRenderer<T extends Mob> extends EntityRenderer<T, EntityRenderState> {
    public GuardianFallbackBossRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 1.0f;
    }

    @Override
    public EntityRenderState createRenderState() {
        return new EntityRenderState();
    }
}
