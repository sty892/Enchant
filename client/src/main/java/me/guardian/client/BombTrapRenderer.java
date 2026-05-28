package me.guardian.client;

import me.guardian.block.ModBlocks;
import me.guardian.entity.BombTrapEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.FallingBlockRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import com.mojang.blaze3d.vertex.PoseStack;

public class BombTrapRenderer extends EntityRenderer<BombTrapEntity, FallingBlockRenderState> {

    public BombTrapRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public FallingBlockRenderState createRenderState() {
        return new FallingBlockRenderState();
    }

    @Override
    public void extractRenderState(BombTrapEntity entity, FallingBlockRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        if (state.movingBlockRenderState != null && ModBlocks.TEMPLE_BOMB != null) {
            state.movingBlockRenderState.blockState = ModBlocks.TEMPLE_BOMB.defaultBlockState();
            state.movingBlockRenderState.blockPos = entity.blockPosition();
            state.movingBlockRenderState.level = entity.level();
            state.movingBlockRenderState.biome = entity.level().getBiome(entity.blockPosition());
            state.movingBlockRenderState.randomSeedPos = entity.blockPosition();
        }
    }

    @Override
    public void submit(FallingBlockRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState cameraState) {
        if (state.movingBlockRenderState == null || state.movingBlockRenderState.blockState == null) return;
        super.submit(state, poseStack, collector, cameraState);
        poseStack.pushPose();
        // Center the 1x1 block model on the entity position
        poseStack.translate(-0.5D, 0.0D, -0.5D);
        collector.order(0).submitMovingBlock(poseStack, state.movingBlockRenderState);
        poseStack.popPose();
    }
}
