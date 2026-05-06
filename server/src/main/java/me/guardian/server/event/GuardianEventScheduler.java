package me.guardian.server.event;

import me.guardian.GuardianMod;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class GuardianEventScheduler {
    private static final List<ScheduledAction> ACTIONS = new ArrayList<>();

    private GuardianEventScheduler() {
    }

    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(GuardianEventScheduler::tick);
    }

    public static void schedule(MinecraftServer server, long delayTicks, Runnable action) {
        if (server == null) {
            GuardianMod.LOGGER.warn("Guardian event scheduler received a missing server; skipping action");
            return;
        }
        if (action == null) {
            GuardianMod.LOGGER.warn("Guardian event scheduler received a missing action; skipping action");
            return;
        }

        long runAtTick = server.getTickCount() + Math.max(0L, delayTicks);
        ACTIONS.add(new ScheduledAction(server, runAtTick, action));
    }

    private static void tick(MinecraftServer server) {
        Iterator<ScheduledAction> iterator = ACTIONS.iterator();
        while (iterator.hasNext()) {
            ScheduledAction scheduled = iterator.next();
            if (scheduled.server != server || server.getTickCount() < scheduled.runAtTick) {
                continue;
            }

            iterator.remove();
            try {
                scheduled.action.run();
            } catch (RuntimeException e) {
                GuardianMod.LOGGER.warn("Guardian scheduled event action failed", e);
            }
        }
    }

    private record ScheduledAction(MinecraftServer server, long runAtTick, Runnable action) {
    }
}
