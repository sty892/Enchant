package me.guardian.block;

import me.guardian.GuardianMod;
import me.guardian.block.entity.AltarBlockEntity;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class ModBlocks {

    // Blocks
    public static Block ALTAR_CORE;
    public static Block ALTAR_SPEED;
    public static Block ALTAR_PROTECTION;
    public static Block ALTAR_DAMAGE;
    public static Block ALTAR_RECOVERY;

    // Block Entity Types
    public static BlockEntityType<AltarBlockEntity> ALTAR_BE_TYPE;

    public static void initialize() {
        ALTAR_CORE = register("altar_core", new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.OBSIDIAN)));
        ALTAR_SPEED = register("altar_speed", new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.OBSIDIAN)));
        ALTAR_PROTECTION = register("altar_protection", new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.OBSIDIAN)));
        ALTAR_DAMAGE = register("altar_damage", new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.OBSIDIAN)));
        ALTAR_RECOVERY = register("altar_recovery", new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.OBSIDIAN)));
    }

    private static Block register(String name, Block block) {
        return Registry.register(BuiltInRegistries.BLOCK, Identifier.fromNamespaceAndPath(GuardianMod.MOD_ID, name), block);
    }
}
