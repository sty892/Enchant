package me.guardian.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class KeyholeBlockEntity extends BlockEntity {
    private boolean isActivated = false;

    public KeyholeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public boolean isActivated() {
        return isActivated;
    }

    public void setActivated(boolean activated) {
        this.isActivated = activated;
        setChanged();
    }
}
