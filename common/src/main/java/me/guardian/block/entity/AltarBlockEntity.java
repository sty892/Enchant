package me.guardian.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

public class AltarBlockEntity extends BlockEntity {
    private ItemStack fragment = ItemStack.EMPTY;
    private UUID owner = null;
    private boolean isActive = false;

    public AltarBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public ItemStack getFragment() {
        return fragment;
    }

    public void setFragment(ItemStack fragment) {
        this.fragment = fragment;
        setChanged();
    }

    public UUID getOwner() {
        return owner;
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
        setChanged();
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
        setChanged();
    }
}
