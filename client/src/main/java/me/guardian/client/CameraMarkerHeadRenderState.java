package me.guardian.client;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;

public final class CameraMarkerHeadRenderState extends EntityRenderState {
    public final ItemStackRenderState head = new ItemStackRenderState();
    public float yRot;
    public float xRot;
    public boolean visible;
}
