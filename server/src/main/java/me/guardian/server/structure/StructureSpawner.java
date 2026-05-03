package me.guardian.server.structure;

import me.guardian.GuardianMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public final class StructureSpawner {
    private StructureSpawner() {
    }

    public static void place(ServerLevel level, BlockPos center, String structureId) {
        GuardianMod.LOGGER.warn("StructureSpawner.place({}, {}, {}) is a placeholder until Module 12", level.dimension().identifier(), center, structureId);
    }
}
