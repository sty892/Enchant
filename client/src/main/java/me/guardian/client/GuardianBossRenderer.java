package me.guardian.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Mob;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import software.bernie.geckolib.renderer.base.RenderPassInfo;

public final class GuardianBossRenderer<T extends Mob & GeoEntity, R extends EntityRenderState & GeoRenderState>
        extends GeoEntityRenderer<T, R> {
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

    // Head-bone rotation is intentionally NOT applied here.
    // GeckoLib 5 does not expose normals through setNormal(), so the ShadingDisablingVertexConsumer
    // cannot neutralise directional shading that results from bone rotation.  The entity body yaw
    // (set by LookControl) already rotates the whole model toward the target, so independent head
    // tilting is unnecessary and was the sole cause of one-sided shading on the head group bones.
}
