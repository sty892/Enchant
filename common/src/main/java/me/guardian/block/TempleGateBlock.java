package me.guardian.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Temple gate block — placed in walls around the boss arena when the boss enters phase 3.3.
 * Completely indestructible by players (hardness = -1, blast resistance = max).
 * Fallback texture: obsidian (custom model will override later).
 * The gate is represented by a wall of these blocks. To "open" a gate, the blocks are removed.
 */
public class TempleGateBlock extends Block {

    public TempleGateBlock(Properties properties) {
        super(properties);
    }

    /** Players cannot mine this block. */
    @Override
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        return 0.0F;
    }

    @Override
    public boolean canBeReplaced(BlockState state,
            net.minecraft.world.item.context.BlockPlaceContext useContext) {
        return false;
    }
}
