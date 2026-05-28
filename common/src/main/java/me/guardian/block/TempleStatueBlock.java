package me.guardian.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Dormant temple statue block — placed when StatueRevivalAttack triggers.
 * After a delay the block is removed and a zombie mob (TempleStatueEntity) spawns in its place.
 * Completely indestructible by players (hardness = -1).
 * Fallback texture: stone_bricks (custom model will override later).
 */
public class TempleStatueBlock extends Block {

    public TempleStatueBlock(Properties properties) {
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
