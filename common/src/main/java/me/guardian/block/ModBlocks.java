package me.guardian.block;

import me.guardian.GuardianMod;
import me.guardian.block.entity.AltarBlockEntity;
import me.guardian.block.entity.KeyholeBlockEntity;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
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

    // Keyhole Block
    public static Block KEYHOLE;

    // Block Entity Types
    public static BlockEntityType<AltarBlockEntity> ALTAR_BE_TYPE;
    public static BlockEntityType<KeyholeBlockEntity> KEYHOLE_BE_TYPE;

    public static void initialize() {
        ALTAR_CORE = register("altar_core", new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.OBSIDIAN)));

        // Registration with lazy BlockEntityType reference
        ALTAR_SPEED = register("altar_speed", new AltarBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OBSIDIAN), () -> ALTAR_BE_TYPE));
        ALTAR_PROTECTION = register("altar_protection", new AltarBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OBSIDIAN), () -> ALTAR_BE_TYPE));
        ALTAR_DAMAGE = register("altar_damage", new AltarBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OBSIDIAN), () -> ALTAR_BE_TYPE));
        ALTAR_RECOVERY = register("altar_recovery", new AltarBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OBSIDIAN), () -> ALTAR_BE_TYPE));

        KEYHOLE = register("keyhole", new KeyholeBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OBSIDIAN), () -> KEYHOLE_BE_TYPE));

        ALTAR_BE_TYPE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                Identifier.fromNamespaceAndPath(GuardianMod.MOD_ID, "altar_be"),
                FabricBlockEntityTypeBuilder.create(
                        (pos, state) -> new AltarBlockEntity(ALTAR_BE_TYPE, pos, state),
                        ALTAR_SPEED, ALTAR_PROTECTION, ALTAR_DAMAGE, ALTAR_RECOVERY
                ).build());

        KEYHOLE_BE_TYPE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                Identifier.fromNamespaceAndPath(GuardianMod.MOD_ID, "keyhole_be"),
                FabricBlockEntityTypeBuilder.create(
                        (pos, state) -> new KeyholeBlockEntity(KEYHOLE_BE_TYPE, pos, state),
                        KEYHOLE
                ).build());
    }

    private static Block register(String name, Block block) {
        Identifier id = Identifier.fromNamespaceAndPath(GuardianMod.MOD_ID, name);
        return Registry.register(BuiltInRegistries.BLOCK, id, block);
    }
}
