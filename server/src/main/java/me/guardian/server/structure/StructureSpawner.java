package me.guardian.server.structure;

import me.guardian.GuardianMod;
import me.guardian.entity.AltarPlacementEntity;
import me.guardian.entity.ModEntities;
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

    public static boolean place(ServerLevel level, BlockPos center, String structureId) {
        Identifier requestedId = parseStructureId(structureId);
        if (requestedId != null && GuardianMod.MOD_ID.equals(requestedId.getNamespace()) && "altar".equals(requestedId.getPath())) {
            return startAltarPlacement(level, center);
        }
        return placeImmediate(level, center, structureId);
    }

    public static boolean placeImmediate(ServerLevel level, BlockPos center, String structureId) {
        Identifier id = parseStructureId(structureId);
        if (id == null) {
            GuardianMod.LOGGER.warn("Invalid structure id: {}", structureId);
            return false;
        }

        Optional<StructureTemplate> template = ConfigStructureLoader.load(level, id)
                .or(() -> level.getStructureManager().get(id));
        if (template.isEmpty()) {
            GuardianMod.LOGGER.warn("Structure template {} was not found in config/guardian_mod/structures or data/{}/structure", id, id.getNamespace());
            return false;
        }

        return placeTemplate(level, center, id, template.get());
    }

    private static boolean startAltarPlacement(ServerLevel level, BlockPos center) {
        AltarPlacementEntity entity = ModEntities.ALTAR_PLACEMENT.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        if (entity == null) {
            return placeImmediate(level, center, "guardian_mod:altar");
        }
        entity.setPos(center.getX() + 0.5D, center.getY(), center.getZ() + 0.5D);
        level.addFreshEntity(entity);
        return true;
    }

    private static boolean placeTemplate(ServerLevel level, BlockPos center, Identifier id, StructureTemplate structure) {
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
        return placed;
    }

    public static Identifier parseStructureId(String structureId) {
        if (structureId == null || structureId.isBlank()) {
            return null;
        }

        String id = structureId.trim();
        if (id.indexOf(':') < 0) {
            id = GuardianMod.MOD_ID + ":" + id;
        }
        return Identifier.tryParse(id);
    }

    public static String resourcePath(Identifier id) {
        return "data/" + id.getNamespace() + "/structure/" + id.getPath() + ".nbt";
    }
}
