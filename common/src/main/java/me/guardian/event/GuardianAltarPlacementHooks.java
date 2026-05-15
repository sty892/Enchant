package me.guardian.event;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public final class GuardianAltarPlacementHooks {
    private static PlacementHook placementHook = (level, pos) -> false;

    private GuardianAltarPlacementHooks() {
    }

    public static void setPlacementHook(PlacementHook hook) {
        placementHook = hook == null ? (level, pos) -> false : hook;
    }

    public static boolean placeAltar(ServerLevel level, BlockPos pos) {
        return placementHook.place(level, pos);
    }

    @FunctionalInterface
    public interface PlacementHook {
        boolean place(ServerLevel level, BlockPos pos);
    }
}
