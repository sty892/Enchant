package me.sty892.enchant.util;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import java.util.Optional;

public class StructureSpawner {
    public static void place(ServerWorld world, BlockPos pos, String structureId) {
        StructureTemplateManager manager = world.getStructureTemplateManager();
        Optional<StructureTemplate> template = manager.getTemplate(Identifier.of(structureId));
        template.ifPresent(t -> {
            t.place(world, pos, pos, new StructurePlacementData(), world.random, BlockPos.ORIGIN.getX());
        });
    }
}
