package me.guardian.block;

import me.guardian.block.entity.KeyholeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Supplier;

public class KeyholeBlock extends Block implements EntityBlock {
    private final Supplier<BlockEntityType<? extends KeyholeBlockEntity>> blockEntityType;

    public KeyholeBlock(Properties properties, Supplier<BlockEntityType<? extends KeyholeBlockEntity>> blockEntityType) {
        super(properties);
        this.blockEntityType = blockEntityType;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return blockEntityType.get().create(pos, state);
    }
}
