package me.guardian.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Temple gate block — placed by players around the boss arena.
 * Open by default (CLOSED=false): fully passable, no collision.
 * The boss "closes" it (CLOSED=true) when it enters phase 3.3, making it a solid wall.
 * Completely indestructible by players (hardness = -1, blast resistance = max).
 * On boss death / arena wipe the blocks are re-opened (CLOSED=false).
 */
public class TempleGateBlock extends Block {
    public static final BooleanProperty CENTER = BooleanProperty.create("center");
    public static final BooleanProperty CLOSED = BooleanProperty.create("closed");

    public TempleGateBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(CENTER, false)
                .setValue(CLOSED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CENTER, CLOSED);
    }

    /** No collision when open, full solid cube when closed. */
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(CLOSED) ? Shapes.block() : Shapes.empty();
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
