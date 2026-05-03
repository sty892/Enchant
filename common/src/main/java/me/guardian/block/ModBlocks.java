package me.guardian.block;

import me.guardian.GuardianMod;
import me.guardian.block.entity.AltarBlockEntity;
import me.guardian.block.entity.KeyholeBlockEntity;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class ModBlocks {

    // Altar Blocks
    public static Block ALTAR_CORE;
    public static Block ALTAR_SPEED;
    public static Block ALTAR_PROTECTION;
    public static Block ALTAR_DAMAGE;
    public static Block ALTAR_RECOVERY;

    // Keyhole Blocks
    public static Block KEYHOLE_1;
    public static Block KEYHOLE_2;
    public static Block KEYHOLE_3;
    public static Block KEYHOLE_4;
    public static Block KEYHOLE_5;
    public static Block KEYHOLE_6;
    public static Block KEYHOLE_7;
    public static Block KEYHOLE_8;

    // Block Entity Types
    public static BlockEntityType<AltarBlockEntity> ALTAR_BE_TYPE;
    public static BlockEntityType<KeyholeBlockEntity> KEYHOLE_BE_TYPE;

    public static void initialize() {
        ALTAR_CORE = register("altar_core", new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.OBSIDIAN)));
        ALTAR_SPEED = register("altar_speed", new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.OBSIDIAN)));
        ALTAR_PROTECTION = register("altar_protection", new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.OBSIDIAN)));
        ALTAR_DAMAGE = register("altar_damage", new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.OBSIDIAN)));
        ALTAR_RECOVERY = register("altar_recovery", new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.OBSIDIAN)));

        KEYHOLE_1 = register("keyhole_1", new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.OBSIDIAN)));
        KEYHOLE_2 = register("keyhole_2", new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.OBSIDIAN)));
        KEYHOLE_3 = register("keyhole_3", new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.OBSIDIAN)));
        KEYHOLE_4 = register("keyhole_4", new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.OBSIDIAN)));
        KEYHOLE_5 = register("keyhole_5", new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.OBSIDIAN)));
        KEYHOLE_6 = register("keyhole_6", new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.OBSIDIAN)));
        KEYHOLE_7 = register("keyhole_7", new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.OBSIDIAN)));
        KEYHOLE_8 = register("keyhole_8", new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.OBSIDIAN)));
    }

    private static Block register(String name, Block block) {
        return Registry.register(BuiltInRegistries.BLOCK, Identifier.fromNamespaceAndPath(GuardianMod.MOD_ID, name), block);
    }
}
