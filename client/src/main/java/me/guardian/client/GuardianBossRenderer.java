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
import software.bernie.geckolib.cache.model.GeoBone;

import java.util.Set;
import java.util.HashSet;
import java.util.Map;

public final class GuardianBossRenderer<T extends Mob & GeoEntity, R extends EntityRenderState & GeoRenderState>
        extends GeoEntityRenderer<T, R> {
    private static final float DEG_TO_RAD = (float) (Math.PI / 180.0D);
    private static final ThreadLocal<String> CURRENT_BONE = ThreadLocal.withInitial(() -> "");

    public GuardianBossRenderer(EntityRendererProvider.Context context, GuardianBossModel<T> model) {
        super(context, model);
    }

    private static boolean isLegOrChildOfLeg(GeoBone bone) {
        GeoBone current = bone;
        while (current != null) {
            String name = current.name();
            if (isLegBoneName(name)) {
                return true;
            }
            current = current.parent();
        }
        return false;
    }

    private static boolean isLegBoneName(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("leg") || 
               lower.contains("foot") || 
               lower.contains("feet") || 
               lower.contains("thigh") || 
               lower.contains("calf") || 
               lower.contains("shin") || 
               lower.contains("paw") || 
               lower.contains("ankle") || 
               lower.contains("hoof") || 
               lower.contains("limb");
    }

    @Override
    public void submitRenderTasks(RenderPassInfo<R> renderPassInfo, OrderedSubmitNodeCollector renderTasks, RenderType renderType) {
        if (renderType == null)
            return;

        final int packedLight = renderPassInfo.packedLight();
        final int packedOverlay = renderPassInfo.packedOverlay();
        final int renderColor = renderPassInfo.renderColor();

        final Set<String> legBones = new HashSet<>();
        for (Map.Entry<String, GeoBone> entry : renderPassInfo.model().boneLookup().get().entrySet()) {
            GeoBone bone = entry.getValue();
            String name = entry.getKey();
            if (isLegOrChildOfLeg(bone)) {
                legBones.add(name);
            }
            renderPassInfo.addBonePositionListener(bone, (worldPos, modelPos, localPos) -> {
                CURRENT_BONE.set(name);
            });
        }

        renderTasks.submitCustomGeometry(renderPassInfo.poseStack(), renderType, (pose, vertexConsumer) -> {
            final PoseStack poseStack = renderPassInfo.poseStack();

            poseStack.pushPose();
            poseStack.last().pose().mul(pose.pose());
            poseStack.last().normal().mul(pose.normal());

            VertexConsumer wrappedConsumer = new ShadingDisablingVertexConsumer(vertexConsumer, legBones);

            renderPassInfo.renderPosed(() -> renderPassInfo.model().render(renderPassInfo, wrappedConsumer, packedLight, packedOverlay, renderColor));
            poseStack.popPose();
        });
    }

    private static class ShadingDisablingVertexConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final Set<String> legBones;

        public ShadingDisablingVertexConsumer(VertexConsumer delegate, Set<String> legBones) {
            this.delegate = delegate;
            this.legBones = legBones;
        }

        private boolean shouldKeepShading() {
            return legBones.contains(CURRENT_BONE.get());
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
            if (shouldKeepShading()) {
                delegate.setNormal(x, y, z);
            } else {
                delegate.setNormal(0.0F, 1.0F, 0.0F);
            }
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            delegate.setLineWidth(width);
            return this;
        }

        @Override
        public void addVertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float normalX, float normalY, float normalZ) {
            if (shouldKeepShading()) {
                delegate.addVertex(x, y, z, color, u, v, overlay, light, normalX, normalY, normalZ);
            } else {
                delegate.addVertex(x, y, z, color, u, v, overlay, light, 0.0F, 1.0F, 0.0F);
            }
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
