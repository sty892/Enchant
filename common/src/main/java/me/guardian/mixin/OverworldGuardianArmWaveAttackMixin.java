package me.guardian.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(targets = "me.guardian.entity.OverworldGuardianAttackController$ArmWaveAttack$1")
public abstract class OverworldGuardianArmWaveAttackMixin {
    @ModifyConstant(method = "onTick", constant = @Constant(floatValue = 10.0F), require = 0)
    private float guardian_mod$rightHandCloseDamage(float original) {
        return 12.0F;
    }

    @ModifyConstant(method = "onTick", constant = @Constant(floatValue = 9.0F), require = 0)
    private float guardian_mod$leftHandCloseDamage(float original) {
        return 12.0F;
    }

    @ModifyConstant(method = "onTick", constant = @Constant(floatValue = 5.0F), require = 0)
    private float guardian_mod$waveDamage(float original) {
        return 12.0F;
    }

    @ModifyConstant(method = "onTick", constant = @Constant(doubleValue = 0.55D), require = 0)
    private double guardian_mod$waveKnockback(double original) {
        return 0.95D;
    }

    @ModifyConstant(method = "onTick", constant = @Constant(doubleValue = 0.25D), require = 0)
    private double guardian_mod$waveUpwardKnockback(double original) {
        return 0.35D;
    }
}
