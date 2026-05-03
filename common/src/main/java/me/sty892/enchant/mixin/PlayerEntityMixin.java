package me.sty892.enchant.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {
    @ModifyVariable(method = "addExhaustion", at = @At("HEAD"), argsOnly = true)
    private float modifyExhaustion(float exhaustion) {
        PlayerEntity player = (PlayerEntity) (Object) this;
        NbtCompound nbt = player.getPersistentData();
        int recoveryLevel = nbt.getInt("guardian_recovery_level");
        if (recoveryLevel > 0) {
            float multiplier = Math.max(0, 1.0f - 0.1f * recoveryLevel);
            return exhaustion * multiplier;
        }
        return exhaustion;
    }
}
