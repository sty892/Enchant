package me.guardian.event;

import me.guardian.GuardianMod;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;

public final class GuardianTriggerHooks {
    private static PointHook pointHook = (player, pos, second) ->
            GuardianMod.LOGGER.warn("Trigger area point hook is not registered; skipping point {}", pos);

    private GuardianTriggerHooks() {
    }

    public static void setPointHook(PointHook pointHook) {
        GuardianTriggerHooks.pointHook = pointHook;
    }

    public static void setPoint(Player player, BlockPos pos, boolean second) {
        pointHook.setPoint(player, pos, second);
    }

    @FunctionalInterface
    public interface PointHook {
        void setPoint(Player player, BlockPos pos, boolean second);
    }
}
