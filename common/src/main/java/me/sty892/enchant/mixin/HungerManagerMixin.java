package me.sty892.enchant.mixin;

import net.minecraft.entity.player.HungerManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HungerManager.class)
public abstract class HungerManagerMixin {
    private PlayerEntity cachedPlayer;

    // We need to find a way to get the player. HungerManager doesn't have a direct reference.
    // However, in 1.21.1, we might need to mixin into PlayerEntity.eat or similar.
}
