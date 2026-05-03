package me.sty892.enchant.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class KeyholeBlock extends Block {
    public static final IntProperty STAGE = IntProperty.of("stage", 0, 8);

    public KeyholeBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(STAGE, 0));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(STAGE);
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        // Handled by server side logic in KeyholeManager or similar
        return ActionResult.PASS;
    }
}
