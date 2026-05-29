package me.guardian.block;

import me.guardian.GuardianMod;
import me.guardian.block.entity.AltarBlockEntity;
import me.guardian.block.entity.KeyholeBlockEntity;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
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
    public static Block DIMENSION_TRIGGER;
    public static Block DIMENSION_RETURN_TRIGGER;

    // Boss Arena Blocks
    public static Block TEMPLE_STATUE;
    public static Block TEMPLE_GATE;
    public static Block TEMPLE_BOMB;

    // Block Entity Types
    public static BlockEntityType<AltarBlockEntity> ALTAR_BE_TYPE;
    public static BlockEntityType<KeyholeBlockEntity> KEYHOLE_BE_TYPE;

    public static void initialize() {
        ALTAR_CORE = register("altar_core", properties -> new AltarCoreBlock(properties));

        // Registration with lazy BlockEntityType reference
        ALTAR_SPEED = register("altar_speed", properties -> new AltarBlock(properties, () -> ALTAR_BE_TYPE));
        ALTAR_PROTECTION = register("altar_protection", properties -> new AltarBlock(properties, () -> ALTAR_BE_TYPE));
        ALTAR_DAMAGE = register("altar_damage", properties -> new AltarBlock(properties, () -> ALTAR_BE_TYPE));
        ALTAR_RECOVERY = register("altar_recovery", properties -> new AltarBlock(properties, () -> ALTAR_BE_TYPE));

        KEYHOLE_1 = register("keyhole_1", properties -> new KeyholeBlock(properties, 1, () -> KEYHOLE_BE_TYPE));
        KEYHOLE_2 = register("keyhole_2", properties -> new KeyholeBlock(properties, 2, () -> KEYHOLE_BE_TYPE));
        KEYHOLE_3 = register("keyhole_3", properties -> new KeyholeBlock(properties, 3, () -> KEYHOLE_BE_TYPE));
        KEYHOLE_4 = register("keyhole_4", properties -> new KeyholeBlock(properties, 4, () -> KEYHOLE_BE_TYPE));
        KEYHOLE_5 = register("keyhole_5", properties -> new KeyholeBlock(properties, 5, () -> KEYHOLE_BE_TYPE));
        KEYHOLE_6 = register("keyhole_6", properties -> new KeyholeBlock(properties, 6, () -> KEYHOLE_BE_TYPE));
        KEYHOLE_7 = register("keyhole_7", properties -> new KeyholeBlock(properties, 7, () -> KEYHOLE_BE_TYPE));
        KEYHOLE_8 = register("keyhole_8", properties -> new KeyholeBlock(properties, 8, () -> KEYHOLE_BE_TYPE));
        DIMENSION_TRIGGER = register("dimension_trigger", properties -> new DimensionTriggerBlock(properties.noOcclusion().replaceable().instabreak(), false));
        DIMENSION_RETURN_TRIGGER = register("dimension_return_trigger", properties -> new DimensionTriggerBlock(properties.noOcclusion().replaceable().instabreak(), true));

        // Temple statue block — placed by players when building the arena; indestructible (strength -1).
        // The boss only revives existing statue blocks, it never places them.
        TEMPLE_STATUE = register("temple_statue",
                properties -> new TempleStatueBlock(properties.strength(-1.0F, 3600000.0F)));

        // Temple gate block — placed by players around the arena; indestructible (strength -1).
        // Open (passable) by default, the boss closes it (solid) at phase 3.3.
        TEMPLE_GATE = register("temple_gate",
                properties -> new TempleGateBlock(properties.strength(-1.0F, 3600000.0F).noOcclusion()));

        // Temple bomb block — indestructible (strength -1), no occlusion
        TEMPLE_BOMB = registerNoItem("temple_bomb",
                properties -> new Block(properties.strength(-1.0F, 3600000.0F).noOcclusion()));

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
                        KEYHOLE_1, KEYHOLE_2, KEYHOLE_3, KEYHOLE_4,
                        KEYHOLE_5, KEYHOLE_6, KEYHOLE_7, KEYHOLE_8
                ).build());
    }

    private static Block register(String name, java.util.function.Function<BlockBehaviour.Properties, Block> factory) {
        Identifier id = Identifier.fromNamespaceAndPath(GuardianMod.MOD_ID, name);
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id);
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.ofFullCopy(Blocks.OBSIDIAN).setId(key);
        Block block = Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties));
        registerBlockItem(name, block);
        return block;
    }

    /** Register a block without an associated BlockItem (arena-only blocks). */
    private static Block registerNoItem(String name, java.util.function.Function<BlockBehaviour.Properties, Block> factory) {
        Identifier id = Identifier.fromNamespaceAndPath(GuardianMod.MOD_ID, name);
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id);
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.ofFullCopy(Blocks.OBSIDIAN).setId(key);
        return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties));
    }

    private static void registerBlockItem(String name, Block block) {
        Identifier id = Identifier.fromNamespaceAndPath(GuardianMod.MOD_ID, name);
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
        Registry.register(BuiltInRegistries.ITEM, key, new BlockItem(block, new Item.Properties().setId(key)));
    }

    public static Block[] keyholeBlocks() {
        return new Block[] {
                KEYHOLE_1, KEYHOLE_2, KEYHOLE_3, KEYHOLE_4,
                KEYHOLE_5, KEYHOLE_6, KEYHOLE_7, KEYHOLE_8
        };
    }

    public static Block getKeyholeBlock(int slot) {
        return switch (slot) {
            case 1 -> KEYHOLE_1;
            case 2 -> KEYHOLE_2;
            case 3 -> KEYHOLE_3;
            case 4 -> KEYHOLE_4;
            case 5 -> KEYHOLE_5;
            case 6 -> KEYHOLE_6;
            case 7 -> KEYHOLE_7;
            case 8 -> KEYHOLE_8;
            default -> throw new IllegalArgumentException("Keyhole slot must be 1..8, got " + slot);
        };
    }
}
