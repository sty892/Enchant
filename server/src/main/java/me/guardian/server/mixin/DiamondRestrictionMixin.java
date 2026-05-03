package me.guardian.server.mixin;

import me.guardian.item.ModItems;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class DiamondRestrictionMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void guardian_onTick(CallbackInfo ci) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (!(entity instanceof Player player)) return;
        if (player.level().isClientSide()) return;

        // Check all slots
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = player.getItemBySlot(slot);
            if (isDiamondItem(stack)) {
                if (!hasRequiredFragment(player)) {
                    if (!player.getInventory().add(stack)) {
                        player.drop(stack, false);
                    }
                    player.setItemSlot(slot, ItemStack.EMPTY);
                }
            }
        }
    }

    private boolean isDiamondItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        String name = stack.getItem().toString();
        return name.contains("diamond_");
    }

    private boolean hasRequiredFragment(Player player) {
        return player.getInventory().contains(new ItemStack(ModItems.FRAGMENT_GENERIC));
    }
}
