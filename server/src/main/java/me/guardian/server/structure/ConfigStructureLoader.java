package me.guardian.server.structure;

import me.guardian.GuardianMod;
import me.guardian.config.ConfigLoader;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public final class ConfigStructureLoader {
    private static final long MAX_STRUCTURE_NBT_BYTES = 32L * 1024L * 1024L;

    private ConfigStructureLoader() {
    }

    public static Path structuresRoot() {
        return ConfigLoader.configRoot().resolve("structures");
    }

    public static Optional<StructureTemplate> load(ServerLevel level, Identifier structureId) {
        Path path = structurePath(structureId);
        if (Files.notExists(path)) {
            return Optional.empty();
        }

        try {
            CompoundTag tag = readStructureTag(path);
            StructureTemplate template = new StructureTemplate();
            template.load(level.registryAccess().lookupOrThrow(Registries.BLOCK), tag);
            GuardianMod.LOGGER.info("Loaded guardian config structure {} from {}", structureId, path);
            return Optional.of(template);
        } catch (IOException | RuntimeException e) {
            GuardianMod.LOGGER.error("Failed to load guardian config structure {} from {}", structureId, path, e);
            return Optional.empty();
        }
    }

    private static Path structurePath(Identifier structureId) {
        String namespace = structureId.getNamespace();
        String path = structureId.getPath();
        return structuresRoot().resolve(namespace).resolve(path + ".nbt").normalize();
    }

    private static CompoundTag readStructureTag(Path path) throws IOException {
        try {
            return NbtIo.readCompressed(path, NbtAccounter.create(MAX_STRUCTURE_NBT_BYTES));
        } catch (IOException compressedFailure) {
            CompoundTag tag = NbtIo.read(path);
            if (tag == null) {
                throw compressedFailure;
            }
            return tag;
        }
    }
}
