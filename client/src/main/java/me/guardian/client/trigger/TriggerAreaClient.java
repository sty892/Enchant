package me.guardian.client.trigger;

import me.guardian.client.screen.TriggerAreaEditorScreen;
import me.guardian.item.ModItems;
import me.guardian.network.TriggerAreaPayloads;
import me.guardian.trigger.TriggerArea;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class TriggerAreaClient {
    private static final List<TriggerArea> AREAS = new ArrayList<>();
    private static int renderTicks;

    private TriggerAreaClient() {
    }

    public static void initialize() {
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
    }

    public static void tick(Minecraft client) {
        if (client.player == null || client.level == null || client.screen != null) {
            return;
        }
        if (!client.player.isHolding(ModItems.TRIGGER_REVEALER)) {
            return;
        }

        while (client.options.keyUse.consumeClick()) {
            findLookedAtArea(client).ifPresent(area -> ClientPlayNetworking.send(new TriggerAreaPayloads.OpenEditor(area.id)));
        }

        renderTicks++;
        if (renderTicks % 3 == 0) {
            renderOutlines(client);
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

    private static void renderOutlines(Minecraft client) {
        String dimension = client.level.dimension().identifier().toString();
        Vec3 viewer = client.player.position();
        int emitted = 0;
        for (TriggerArea area : AREAS) {
            if (!area.dimension.equals(dimension) || viewer.distanceToSqr(center(area)) > 4096.0D) {
                continue;
            }
            emitted += renderOutline(client, area, 700 - emitted);
            if (emitted >= 700) {
                return;
            }
        }
    }

    private static Vec3 center(TriggerArea area) {
        return new Vec3((area.min.getX() + area.max.getX() + 1.0D) * 0.5D,
                (area.min.getY() + area.max.getY() + 1.0D) * 0.5D,
                (area.min.getZ() + area.max.getZ() + 1.0D) * 0.5D);
    }

    private static int renderOutline(Minecraft client, TriggerArea area, int budget) {
        double minX = area.min.getX();
        double minY = area.min.getY();
        double minZ = area.min.getZ();
        double maxX = area.max.getX() + 1.0D;
        double maxY = area.max.getY() + 1.0D;
        double maxZ = area.max.getZ() + 1.0D;
        int emitted = 0;
        emitted += line(client, minX, minY, minZ, maxX, minY, minZ, budget - emitted);
        emitted += line(client, minX, minY, maxZ, maxX, minY, maxZ, budget - emitted);
        emitted += line(client, minX, maxY, minZ, maxX, maxY, minZ, budget - emitted);
        emitted += line(client, minX, maxY, maxZ, maxX, maxY, maxZ, budget - emitted);
        emitted += line(client, minX, minY, minZ, minX, minY, maxZ, budget - emitted);
        emitted += line(client, maxX, minY, minZ, maxX, minY, maxZ, budget - emitted);
        emitted += line(client, minX, maxY, minZ, minX, maxY, maxZ, budget - emitted);
        emitted += line(client, maxX, maxY, minZ, maxX, maxY, maxZ, budget - emitted);
        emitted += line(client, minX, minY, minZ, minX, maxY, minZ, budget - emitted);
        emitted += line(client, maxX, minY, minZ, maxX, maxY, minZ, budget - emitted);
        emitted += line(client, minX, minY, maxZ, minX, maxY, maxZ, budget - emitted);
        emitted += line(client, maxX, minY, maxZ, maxX, maxY, maxZ, budget - emitted);
        return emitted;
    }

    private static int line(Minecraft client, double x1, double y1, double z1, double x2, double y2, double z2, int budget) {
        if (budget <= 0) {
            return 0;
        }
        double dx = x2 - x1;
        double dy = y2 - y1;
        double dz = z2 - z1;
        int steps = Math.max(1, (int) Math.ceil(Math.sqrt(dx * dx + dy * dy + dz * dz) * 2.0D));
        int emitted = 0;
        for (int i = 0; i <= steps && emitted < budget; i++) {
            double t = i / (double) steps;
            client.level.addParticle(ParticleTypes.END_ROD, x1 + dx * t, y1 + dy * t, z1 + dz * t, 0.0D, 0.0D, 0.0D);
            emitted++;
        }
        return emitted;
    }
}
