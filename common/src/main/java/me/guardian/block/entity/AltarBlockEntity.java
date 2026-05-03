package me.guardian.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.UUID;

public class AltarBlockEntity extends BlockEntity {
    private ItemStack fragment = ItemStack.EMPTY;
    private UUID ownerUuid = null;
    private boolean isActive = false;
    private int ritualTicks = 0;

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

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public void setOwnerUuid(UUID ownerUuid) {
        this.ownerUuid = ownerUuid;
        setChanged();
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
        setChanged();
    }

    public int getRitualTicks() {
        return ritualTicks;
    }

    public void setRitualTicks(int ritualTicks) {
        this.ritualTicks = ritualTicks;
        setChanged();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!fragment.isEmpty()) {
            output.store("Fragment", ItemStack.CODEC, fragment);
        }
        if (ownerUuid != null) {
            output.putString("Owner", ownerUuid.toString());
        }
        output.putBoolean("IsActive", isActive);
        output.putInt("RitualTicks", ritualTicks);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.fragment = input.read("Fragment", ItemStack.CODEC).orElse(ItemStack.EMPTY);
        this.ownerUuid = input.getString("Owner").map(UUID::fromString).orElse(null);
        this.isActive = input.getBooleanOr("IsActive", false);
        this.ritualTicks = input.getIntOr("RitualTicks", 0);
    }
}
