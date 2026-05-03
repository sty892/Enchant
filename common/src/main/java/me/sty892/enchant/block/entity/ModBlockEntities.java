package me.sty892.enchant.block.entity;

import me.sty892.enchant.GuardianModCommon;
import me.sty892.enchant.block.ModBlocks;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModBlockEntities {
    public static final BlockEntityType<AltarBlockEntity> ALTAR_BE = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(GuardianModCommon.MOD_ID, "altar_be"),
            EntityType.Builder.create(AltarBlockEntity::new,
                    ModBlocks.ALTAR_SPEED, ModBlocks.ALTAR_PROTECTION, ModBlocks.ALTAR_DAMAGE, ModBlocks.ALTAR_RECOVERY).build(null)
    );

    public static final BlockEntityType<AltarCoreBlockEntity> ALTAR_CORE_BE = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(GuardianModCommon.MOD_ID, "altar_core_be"),
            EntityType.Builder.create(AltarCoreBlockEntity::new, ModBlocks.ALTAR_CORE).build(null)
    );

    public static void registerBlockEntities() {
    }
}
