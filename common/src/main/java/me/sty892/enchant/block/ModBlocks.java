package me.sty892.enchant.block;

import me.sty892.enchant.GuardianModCommon;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModBlocks {
    public static final Block ALTAR_SPEED = register("altar_speed", new AltarBlock(AbstractBlock.Settings.copy(Blocks.STONE)));
    public static final Block ALTAR_PROTECTION = register("altar_protection", new AltarBlock(AbstractBlock.Settings.copy(Blocks.STONE)));
    public static final Block ALTAR_DAMAGE = register("altar_damage", new AltarBlock(AbstractBlock.Settings.copy(Blocks.STONE)));
    public static final Block ALTAR_RECOVERY = register("altar_recovery", new AltarBlock(AbstractBlock.Settings.copy(Blocks.STONE)));
    public static final Block ALTAR_CORE = register("altar_core", new AltarCoreBlock(AbstractBlock.Settings.copy(Blocks.STONE)));
    public static final Block KEYHOLE = register("keyhole", new KeyholeBlock(AbstractBlock.Settings.copy(Blocks.STONE)));

    private static Block register(String name, Block block) {
        return Registry.register(Registries.BLOCK, Identifier.of(GuardianModCommon.MOD_ID, name), block);
    }

    public static void registerModBlocks() {
        GuardianModCommon.LOGGER.info("Registering Mod Blocks for " + GuardianModCommon.MOD_ID);
    }
}
