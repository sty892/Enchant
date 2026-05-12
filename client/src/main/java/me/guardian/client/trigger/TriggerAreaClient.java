package me.guardian.client.trigger;

import me.guardian.client.screen.TriggerAreaEditorScreen;
import me.guardian.item.ModItems;
import me.guardian.network.TriggerAreaPayloads;
import me.guardian.trigger.TriggerArea;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class TriggerAreaClient {
    private static final List<TriggerArea> AREAS = new ArrayList<>();
    private static boolean revealEnabled;
    private static boolean useWasDown;
    private static boolean attackWasDown;

    private TriggerAreaClient() {
    }

    public static void initialize() {
        WorldRenderEvents.END_MAIN.register(TriggerAreaClient::render);
        ClientPlayNetworking.registerGlobalReceiver(TriggerAreaPayloads.Sync.TYPE, (payload, context) -> context.client().execute(() -> {
            AREAS.clear();
            for (String serializedArea : payload.areas()) {
                try {
                    AREAS.add(TriggerArea.deserialize(serializedArea));
                } catch (RuntimeException ignored) {
                }
            }
        }));
        ClientPlayNetworking.registerGlobalReceiver(TriggerAreaPayloads.EditorData.TYPE, (payload, context) -> context.client().execute(() -> {
            try {
                context.client().setScreen(new TriggerAreaEditorScreen(TriggerArea.deserialize(payload.area())));
            } catch (RuntimeException ignored) {
            }
        }));
    }

    public static void clear() {
        AREAS.clear();
        revealEnabled = false;
        useWasDown = false;
        attackWasDown = false;
    }

    public static boolean isRevealEnabled() {
        return revealEnabled;
    }

    public static void tick(Minecraft client) {
        if (client.player == null || client.level == null || client.screen != null) {
            useWasDown = false;
            attackWasDown = false;
            return;
        }
        boolean useDown = client.options.keyUse.isDown();
        boolean attackDown = client.options.keyAttack.isDown();
        if (!useDown && !attackDown) {
            useWasDown = false;
            attackWasDown = false;
            return;
        }

        boolean usePressed = useDown && !useWasDown;
        boolean attackPressed = attackDown && !attackWasDown;
        useWasDown = useDown;
        attackWasDown = attackDown;
        if (!usePressed && !attackPressed) {
            return;
        }

        Optional<TriggerArea> lookedAtArea = revealEnabled ? findLookedAtArea(client) : Optional.empty();
        if (lookedAtArea.isPresent()) {
            ClientPlayNetworking.send(new TriggerAreaPayloads.OpenEditor(lookedAtArea.get().id));
            return;
        }

        if (usePressed && client.player.isHolding(ModItems.TRIGGER_REVEALER)) {
            revealEnabled = !revealEnabled;
            client.player.displayClientMessage(net.minecraft.network.chat.Component.literal("Trigger visibility " + (revealEnabled ? "enabled" : "disabled")), true);
        }
    }

    private static Optional<TriggerArea> findLookedAtArea(Minecraft client) {
        Vec3 eye = client.player.getEyePosition();
        Vec3 look = client.player.getLookAngle();
        String dimension = client.level.dimension().identifier().toString();
        TriggerArea best = null;
        double bestDistance = Double.MAX_VALUE;
        for (double distance = 0.0D; distance <= 48.0D; distance += 0.25D) {
            Vec3 point = eye.add(look.scale(distance));
            for (TriggerArea area : AREAS) {
                if (!area.dimension.equals(dimension) || !contains(area, point)) {
                    continue;
                }
                if (distance < bestDistance) {
                    best = area;
                    bestDistance = distance;
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private static boolean contains(TriggerArea area, Vec3 point) {
        return point.x >= area.min.getX() && point.x <= area.max.getX() + 1.0D
                && point.y >= area.min.getY() && point.y <= area.max.getY() + 1.0D
                && point.z >= area.min.getZ() && point.z <= area.max.getZ() + 1.0D;
    }

    private static void render(WorldRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (!revealEnabled || client.player == null || client.level == null) {
            return;
        }

        String dimension = client.level.dimension().identifier().toString();
        Vec3 viewer = context.worldState().cameraRenderState.pos;
        for (TriggerArea area : AREAS) {
            if (!area.dimension.equals(dimension) || viewer.distanceToSqr(center(area)) > 4096.0D) {
                continue;
            }
            AABB box = new AABB(area.min.getX(), area.min.getY(), area.min.getZ(),
                    area.max.getX() + 1.0D, area.max.getY() + 1.0D, area.max.getZ() + 1.0D).inflate(0.03D);
            renderFill(context, box, viewer);
            ShapeRenderer.renderShape(context.matrices(), context.consumers().getBuffer(RenderTypes.secondaryBlockOutline()),
                    Shapes.create(box), -viewer.x, -viewer.y, -viewer.z, 0xFFFFFFFF, 4.0F);
        }
    }

    private static void renderFill(WorldRenderContext context, AABB box, Vec3 viewer) {
        double minX = box.minX - viewer.x;
        double minY = box.minY - viewer.y;
        double minZ = box.minZ - viewer.z;
        double maxX = box.maxX - viewer.x;
        double maxY = box.maxY - viewer.y;
        double maxZ = box.maxZ - viewer.z;
        VertexConsumer buffer = context.consumers().getBuffer(RenderTypes.debugQuads());
        PoseStack.Pose pose = context.matrices().last();
        int color = 0x2855DFFF;
        quad(buffer, pose, minX, minY, minZ, maxX, minY, minZ, maxX, maxY, minZ, minX, maxY, minZ, color);
        quad(buffer, pose, maxX, minY, maxZ, minX, minY, maxZ, minX, maxY, maxZ, maxX, maxY, maxZ, color);
        quad(buffer, pose, minX, minY, maxZ, minX, minY, minZ, minX, maxY, minZ, minX, maxY, maxZ, color);
        quad(buffer, pose, maxX, minY, minZ, maxX, minY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, color);
        quad(buffer, pose, minX, minY, maxZ, maxX, minY, maxZ, maxX, minY, minZ, minX, minY, minZ, color);
        quad(buffer, pose, minX, maxY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, minX, maxY, maxZ, color);
    }

    private static void quad(VertexConsumer buffer, PoseStack.Pose pose,
                             double x1, double y1, double z1, double x2, double y2, double z2,
                             double x3, double y3, double z3, double x4, double y4, double z4,
                             int color) {
        vertex(buffer, pose, x1, y1, z1, color);
        vertex(buffer, pose, x2, y2, z2, color);
        vertex(buffer, pose, x3, y3, z3, color);
        vertex(buffer, pose, x4, y4, z4, color);
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, double x, double y, double z, int color) {
        buffer.addVertex(pose, (float) x, (float) y, (float) z).setColor(color);
    }

    private static Vec3 center(TriggerArea area) {
        return new Vec3((area.min.getX() + area.max.getX() + 1.0D) * 0.5D,
                (area.min.getY() + area.max.getY() + 1.0D) * 0.5D,
                (area.min.getZ() + area.max.getZ() + 1.0D) * 0.5D);
    }

}
