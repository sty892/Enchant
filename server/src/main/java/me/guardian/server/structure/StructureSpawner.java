package me.guardian.server.structure;

import me.guardian.GuardianMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.Optional;

public final class StructureSpawner {
    private StructureSpawner() {
    }

    public static void place(ServerLevel level, BlockPos center, String structureId) {
        Identifier id = Identifier.tryParse(structureId);
        if (id == null) {
            GuardianMod.LOGGER.warn("Invalid structure id: {}", structureId);
            return;
        }

        Optional<StructureTemplate> template = level.getStructureManager().get(id);
        if (template.isEmpty()) {
            GuardianMod.LOGGER.warn("Structure template {} was not found in data/{}/structures", id, id.getNamespace());
            return;
        }

        StructureTemplate structure = template.get();
        Vec3i size = structure.getSize();
        BlockPos origin = center.offset(-size.getX() / 2, 0, -size.getZ() / 2);
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setMirror(Mirror.NONE)
                .setRotation(Rotation.NONE)
                .setIgnoreEntities(false)
                .setKnownShape(true)
                .setFinalizeEntities(true);

        boolean placed = structure.placeInWorld(level, origin, origin, settings, RandomSource.create(level.getRandom().nextLong()), 2);
        if (!placed) {
            GuardianMod.LOGGER.warn("Structure template {} failed to place at {} in {}", id, origin, level.dimension().identifier());
        }
    }
}
