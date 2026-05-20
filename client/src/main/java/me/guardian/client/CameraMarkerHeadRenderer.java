package me.guardian.client;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.google.common.collect.HashMultimap;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import me.guardian.entity.CameraMarkerEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

import java.util.UUID;

public final class CameraMarkerHeadRenderer extends EntityRenderer<CameraMarkerEntity, CameraMarkerHeadRenderState> {
    private static final String HEAD_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZDZkMTVlNzJhY2YyMTliYzFkYThhZTc2ODZmNGY4M2M3NzNhZGNiNGY4ZmFjYzg3Y2JiZDQzZWU3OTA1N2YzIn19fQ==";
    private static final ItemStack HEAD = createHead();

    private final ItemModelResolver itemModelResolver;

    public CameraMarkerHeadRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemModelResolver = context.getItemModelResolver();
        this.shadowRadius = 0.0F;
    }

    @Override
    public CameraMarkerHeadRenderState createRenderState() {
        return new CameraMarkerHeadRenderState();
    }

    @Override
    public void extractRenderState(CameraMarkerEntity entity, CameraMarkerHeadRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.visible = CameraMarkerEntity.clientRevealEnabled;
        state.yRot = entity.getYRot();
        state.xRot = entity.getXRot();
        itemModelResolver.updateForNonLiving(state.head, HEAD, ItemDisplayContext.HEAD, entity);
    }

    @Override
    public void submit(CameraMarkerHeadRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState cameraState) {
        if (!state.visible) {
            return;
        }
        super.submit(state, poseStack, collector, cameraState);
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - state.yRot));
        poseStack.mulPose(Axis.XP.rotationDegrees(state.xRot));
        poseStack.scale(1.0F, 1.0F, 1.0F);
        state.head.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        poseStack.popPose();
    }

    private static ItemStack createHead() {
        ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
        PropertyMap properties = new PropertyMap(HashMultimap.create());
        properties.put("textures", new Property("textures", HEAD_TEXTURE));
        GameProfile profile = new GameProfile(UUID.nameUUIDFromBytes(HEAD_TEXTURE.getBytes(java.nio.charset.StandardCharsets.UTF_8)), "Camera", properties);
        stack.set(DataComponents.PROFILE, ResolvableProfile.createResolved(profile));
        return stack;
    }
}
