package me.sty892.enchant.block.entity;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import java.util.UUID;

public class AltarBlockEntity extends BlockEntity {
    private UUID owner;
    private ItemStack fragment = ItemStack.EMPTY;
    private boolean isActive = false;

    public AltarBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ALTAR_BE, pos, state);
    }

    public static void tick(World world, BlockPos pos, BlockState state, AltarBlockEntity blockEntity) {
        if (world.isClient) return;
        if (blockEntity.owner != null && !blockEntity.fragment.isEmpty()) {
            PlayerEntity player = world.getPlayerByUuid(blockEntity.owner);
            if (player == null || player.squaredDistanceTo(pos.toCenterPos()) > 25.0) { // 5 blocks squared
                player = world.getClosestPlayer(pos.getX(), pos.getY(), pos.getZ(), 5.0, false);
                if (player == null || !player.getUuid().equals(blockEntity.owner)) {
                    world.spawnEntity(new net.minecraft.entity.ItemEntity(world, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, blockEntity.fragment));
                    blockEntity.clear();
                }
            }
        }
    }

    public ActionResult onUse(PlayerEntity player, ItemStack stack) {
        if (fragment.isEmpty()) {
            if (stack.getItem().toString().contains("fragment")) {
                this.fragment = stack.split(1);
                this.owner = player.getUuid();
                markDirty();
                if (world != null) world.updateListeners(pos, getCachedState(), getCachedState(), 3);
                return ActionResult.SUCCESS;
            }
        } else {
            if (player.getUuid().equals(owner)) {
                player.getInventory().offerOrDrop(fragment);
                fragment = ItemStack.EMPTY;
                owner = null;
                isActive = false;
                markDirty();
                if (world != null) world.updateListeners(pos, getCachedState(), getCachedState(), 3);
                return ActionResult.SUCCESS;
            } else {
                player.sendMessage(Text.literal("Алтарь занят другим игроком"), true);
                return ActionResult.CONSUME;
            }
        }
        return ActionResult.PASS;
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        if (owner != null) nbt.putUuid("Owner", owner);
        if (!fragment.isEmpty()) {
            nbt.put("Fragment", fragment.encode(registries));
        }
        nbt.putBoolean("Active", isActive);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        if (nbt.containsUuid("Owner")) owner = nbt.getUuid("Owner");
        if (nbt.contains("Fragment")) {
            fragment = ItemStack.fromNbt(registries, nbt.getCompound("Fragment")).orElse(ItemStack.EMPTY);
        } else {
            fragment = ItemStack.EMPTY;
        }
        isActive = nbt.getBoolean("Active");
    }

    public void setActive(boolean active) {
        this.isActive = active;
        markDirty();
        if (world != null) world.updateListeners(pos, getCachedState(), getCachedState(), 3);
    }

    public boolean isActive() {
        return isActive;
    }

    public UUID getOwner() {
        return owner;
    }

    public ItemStack getFragment() {
        return fragment;
    }

    public void clear() {
        this.fragment = ItemStack.EMPTY;
        this.owner = null;
        this.isActive = false;
        markDirty();
        if (world != null) world.updateListeners(pos, getCachedState(), getCachedState(), 3);
    }

    @Override
    public BlockEntityUpdateS2CPacket toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
        return createNbt(registries);
    }
}
