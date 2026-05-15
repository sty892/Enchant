package me.guardian.block.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public class AltarBlockEntity extends BlockEntity {
    private ItemStack fragment = ItemStack.EMPTY;
    private UUID ownerUuid = null;
    private UUID displayEntityUuid = null;
    private boolean isActive = false;
    private int ritualTicks = 0;

    public AltarBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public ItemStack getFragment() {
        return fragment;
    }

    public void setFragment(ItemStack fragment) {
        ItemStack nextFragment = fragment == null ? ItemStack.EMPTY : fragment;
        boolean changedFragment = !ItemStack.matches(this.fragment, nextFragment);
        this.fragment = nextFragment;
        if (changedFragment && level instanceof ServerLevel serverLevel) {
            removeDisplayEntity(serverLevel);
            if (!nextFragment.isEmpty()) {
                spawnDisplayEntity(serverLevel);
            }
            sync();
        }
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
        if (displayEntityUuid != null) {
            output.putString("DisplayEntity", displayEntityUuid.toString());
        }
        output.putBoolean("IsActive", isActive);
        output.putInt("RitualTicks", ritualTicks);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.fragment = input.read("Fragment", ItemStack.CODEC).orElse(ItemStack.EMPTY);
        this.ownerUuid = input.getString("Owner").map(UUID::fromString).orElse(null);
        this.displayEntityUuid = input.getString("DisplayEntity").map(UUID::fromString).orElse(null);
        this.isActive = input.getBooleanOr("IsActive", false);
        this.ritualTicks = input.getIntOr("RitualTicks", 0);
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel serverLevel) {
            removeDisplayEntity(serverLevel);
        }
        super.setRemoved();
    }

    public static void tick(Level level, BlockPos pos, BlockState state, AltarBlockEntity altar) {
        if (!(level instanceof ServerLevel serverLevel) || serverLevel.getServer().getTickCount() % 20 != 0) {
            return;
        }
        if (altar.fragment.isEmpty()) {
            altar.removeDisplayEntity(serverLevel);
            return;
        }

        Entity display = altar.displayEntityUuid == null ? null : serverLevel.getEntity(altar.displayEntityUuid);
        if (!(display instanceof ItemEntity itemEntity) || display.isRemoved()) {
            altar.spawnDisplayEntity(serverLevel);
            return;
        }
        altar.positionDisplayEntity(itemEntity);
    }

    private void spawnDisplayEntity(ServerLevel level) {
        ItemEntity itemEntity = new ItemEntity(level, worldPosition.getX() + 0.5D, worldPosition.getY() + 1.25D, worldPosition.getZ() + 0.5D, fragment.copyWithCount(1));
        itemEntity.setNeverPickUp();
        itemEntity.setUnlimitedLifetime();
        itemEntity.setNoGravity(true);
        itemEntity.setInvulnerable(true);
        positionDisplayEntity(itemEntity);
        level.addFreshEntity(itemEntity);
        displayEntityUuid = itemEntity.getUUID();
        setChanged();
    }

    private void positionDisplayEntity(ItemEntity itemEntity) {
        itemEntity.setPos(worldPosition.getX() + 0.5D, worldPosition.getY() + 1.25D, worldPosition.getZ() + 0.5D);
        itemEntity.setDeltaMovement(Vec3.ZERO);
    }

    private void removeDisplayEntity(ServerLevel level) {
        if (displayEntityUuid == null) {
            return;
        }
        Entity display = level.getEntity(displayEntityUuid);
        if (display != null) {
            display.discard();
        }
        displayEntityUuid = null;
        setChanged();
    }

    private void sync() {
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }
}
