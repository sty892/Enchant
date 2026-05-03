package me.guardian.block;

import me.guardian.block.entity.AltarBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Supplier;

public class AltarBlock extends Block implements EntityBlock {
    private final Supplier<BlockEntityType<? extends AltarBlockEntity>> blockEntityType;

    public AltarBlock(Properties properties, Supplier<BlockEntityType<? extends AltarBlockEntity>> blockEntityType) {
        super(properties);
        this.blockEntityType = blockEntityType;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return blockEntityType.get().create(pos, state);
    }
}
