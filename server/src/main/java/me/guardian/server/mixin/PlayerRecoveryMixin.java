package me.guardian.server.mixin;

import me.guardian.server.altar.GuardianPlayerUpgrades;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(Player.class)
public abstract class PlayerRecoveryMixin {
    @ModifyArg(method = "causeFoodExhaustion", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/food/FoodData;addExhaustion(F)V"), index = 0)
    private float guardian_mod$applyRecoveryExhaustion(float exhaustion) {
        if ((Object) this instanceof ServerPlayer player) {
            int recoveryLevel = GuardianPlayerUpgrades.getRecoveryLevel(player);
            return Math.max(0.0F, exhaustion * (1.0F - 0.1F * recoveryLevel));
        }
        return exhaustion;
    }
}
