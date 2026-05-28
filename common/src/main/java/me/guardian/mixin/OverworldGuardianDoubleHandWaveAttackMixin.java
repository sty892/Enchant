package me.guardian.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(targets = "me.guardian.entity.OverworldGuardianAttackController$DoubleHandWaveAttack$1")
public abstract class OverworldGuardianDoubleHandWaveAttackMixin {
    @ModifyConstant(method = "onTick", constant = @Constant(floatValue = 12.0F), require = 0)
    private float guardian_mod$doubleHandDamage(float original) {
        return 18.0F;
    }

    @ModifyConstant(method = "onTick", constant = @Constant(doubleValue = 1.05D), require = 0)
    private double guardian_mod$doubleHandKnockback(double original) {
        return 1.55D;
    }

    @ModifyConstant(method = "onTick", constant = @Constant(doubleValue = 0.45D), require = 0)
    private double guardian_mod$doubleHandUpwardKnockback(double original) {
        return 0.65D;
    }
}
