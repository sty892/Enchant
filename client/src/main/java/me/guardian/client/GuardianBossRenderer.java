package me.guardian.client;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animation.state.BoneSnapshot;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.base.BoneSnapshots;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import software.bernie.geckolib.renderer.base.RenderPassInfo;

public final class GuardianBossRenderer<T extends Mob & GeoEntity, R extends EntityRenderState & GeoRenderState>
        extends GeoEntityRenderer<T, R> {
    private static final float DEG_TO_RAD = (float) (Math.PI / 180.0D);

    public GuardianBossRenderer(EntityRendererProvider.Context context, GuardianBossModel<T> model) {
        super(context, model);
    }

    @Override
    public void adjustModelBonesForRender(RenderPassInfo<R> renderPassInfo, BoneSnapshots snapshots) {
        R renderState = renderPassInfo.renderState();
        float headYaw = Mth.clamp(renderState.getOrDefaultGeckolibData(DataTickets.ENTITY_YAW, 0.0F), -75.0F, 75.0F);
        float headPitch = Mth.clamp(renderState.getOrDefaultGeckolibData(DataTickets.ENTITY_PITCH, 0.0F), -45.0F, 45.0F);
        rotateHeadBone(snapshots, "Head", headPitch, headYaw);
        rotateHeadBone(snapshots, "head", headPitch, headYaw);
    }

    private static void rotateHeadBone(BoneSnapshots snapshots, String boneName, float pitch, float yaw) {
        snapshots.ifPresent(boneName, snapshot -> rotateHead(snapshot, pitch, yaw));
    }

    private static void rotateHead(BoneSnapshot snapshot, float pitch, float yaw) {
        snapshot.setRotX(snapshot.getRotX() - pitch * DEG_TO_RAD);
        snapshot.setRotY(snapshot.getRotY() + yaw * DEG_TO_RAD);
    }
}
