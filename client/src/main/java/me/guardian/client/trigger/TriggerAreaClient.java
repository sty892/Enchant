package me.guardian.client.trigger;

import me.guardian.client.screen.TriggerAreaEditorScreen;
import me.guardian.item.ModItems;
import me.guardian.network.TriggerAreaPayloads;
import me.guardian.trigger.TriggerArea;
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
                    area.max.getX() + 1.0D, area.max.getY() + 1.0D, area.max.getZ() + 1.0D);
            ShapeRenderer.renderShape(context.matrices(), context.consumers().getBuffer(RenderTypes.secondaryBlockOutline()),
                    Shapes.create(box), -viewer.x, -viewer.y, -viewer.z, 0xFFFFFFFF, 1.0F);
        }
    }

    private static Vec3 center(TriggerArea area) {
        return new Vec3((area.min.getX() + area.max.getX() + 1.0D) * 0.5D,
                (area.min.getY() + area.max.getY() + 1.0D) * 0.5D,
                (area.min.getZ() + area.max.getZ() + 1.0D) * 0.5D);
    }

}
