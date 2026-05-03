package me.guardian.block;

import me.guardian.block.entity.KeyholeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

import java.util.function.Supplier;

public class KeyholeBlock extends Block implements EntityBlock {
    public static final IntegerProperty STAGE = IntegerProperty.create("stage", 0, 8);
    private final Supplier<BlockEntityType<? extends KeyholeBlockEntity>> blockEntityType;

    public KeyholeBlock(Properties properties, Supplier<BlockEntityType<? extends KeyholeBlockEntity>> blockEntityType) {
        super(properties);
        this.blockEntityType = blockEntityType;
        this.registerDefaultState(this.stateDefinition.any().setValue(STAGE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(STAGE);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return blockEntityType.get().create(pos, state);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof KeyholeBlockEntity keyholeBe) {
            // This is a bridge to the server-side logic
            // We will use a callback or just call a server-side manager directly if we are in server module
            // But since this is 'common', we might need a registry or a static entrypoint
            return InteractionResult.PASS; // Let the caller handle it
        }
        return InteractionResult.SUCCESS;
    }
}
