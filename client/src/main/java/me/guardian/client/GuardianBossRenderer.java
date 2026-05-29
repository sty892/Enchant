package me.guardian.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
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
        // No ground/blob shadow under the boss.
        this.shadowRadius = 0.0F;
    }

    @Override
    public void submitRenderTasks(RenderPassInfo<R> renderPassInfo, OrderedSubmitNodeCollector renderTasks, RenderType renderType) {
        if (renderType == null)
            return;

        final int packedLight = renderPassInfo.packedLight();
        final int packedOverlay = renderPassInfo.packedOverlay();
        final int renderColor = renderPassInfo.renderColor();

        renderTasks.submitCustomGeometry(renderPassInfo.poseStack(), renderType, (pose, vertexConsumer) -> {
            final PoseStack poseStack = renderPassInfo.poseStack();

            poseStack.pushPose();
            poseStack.last().pose().mul(pose.pose());
            poseStack.last().normal().mul(pose.normal());

            VertexConsumer wrappedConsumer = new ShadingDisablingVertexConsumer(vertexConsumer);

            renderPassInfo.renderPosed(() -> renderPassInfo.model().render(renderPassInfo, wrappedConsumer, packedLight, packedOverlay, renderColor));
            poseStack.popPose();
        });
    }

    private static class ShadingDisablingVertexConsumer implements VertexConsumer {
        private final VertexConsumer delegate;

        public ShadingDisablingVertexConsumer(VertexConsumer delegate) {
            this.delegate = delegate;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            delegate.setColor(r, g, b, a);
            return this;
        }

        @Override
        public VertexConsumer setColor(int color) {
            delegate.setColor(color);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            delegate.setNormal(0.0F, 1.0F, 0.0F);
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            delegate.setLineWidth(width);
            return this;
        }

        @Override
        public void addVertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float normalX, float normalY, float normalZ) {
            delegate.addVertex(x, y, z, color, u, v, overlay, light, 0.0F, 1.0F, 0.0F);
        }
    }

    @Override
    public void adjustModelBonesForRender(RenderPassInfo<R> renderPassInfo, BoneSnapshots snapshots) {
        R renderState = renderPassInfo.renderState();
        float headYaw = Mth.clamp(renderState.getOrDefaultGeckolibData(DataTickets.ENTITY_YAW, 0.0F), -75.0F, 75.0F);
        float headPitch = Mth.clamp(renderState.getOrDefaultGeckolibData(DataTickets.ENTITY_PITCH, 0.0F), -45.0F, 45.0F);
        // Apply head rotation to only ONE bone to avoid double-rotation when
        // "Head" is a parent of "head" (or vice-versa).  Prefer lowercase "head".
        if (!tryRotateHeadBone(snapshots, "head", headPitch, headYaw)) {
            tryRotateHeadBone(snapshots, "Head", headPitch, headYaw);
        }
    }

    private static boolean tryRotateHeadBone(BoneSnapshots snapshots, String boneName, float pitch, float yaw) {
        boolean[] found = {false};
        snapshots.ifPresent(boneName, snapshot -> {
            rotateHead(snapshot, pitch, yaw);
            found[0] = true;
        });
        return found[0];
    }

    private static void rotateHead(BoneSnapshot snapshot, float pitch, float yaw) {
        snapshot.setRotX(snapshot.getRotX() - pitch * DEG_TO_RAD);
        snapshot.setRotY(snapshot.getRotY() - yaw * DEG_TO_RAD);
    }
}
