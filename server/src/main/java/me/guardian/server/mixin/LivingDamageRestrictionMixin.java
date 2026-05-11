package me.guardian.server.mixin;

import me.guardian.server.trigger.TriggerAreaManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingDamageRestrictionMixin {
    @Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
    private void guardian_mod$blockTriggerAreaDamage(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (TriggerAreaManager.blocksDamage((Entity) (Object) this, source)) {
            cir.setReturnValue(false);
        }
    }
}
