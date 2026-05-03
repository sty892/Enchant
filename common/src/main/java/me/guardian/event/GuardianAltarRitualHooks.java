package me.guardian.event;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;

public final class GuardianAltarRitualHooks {
    private static ActivationHook activationHook = (level, corePos, player, stack) -> InteractionResult.PASS;

    private GuardianAltarRitualHooks() {
    }

    public static void setActivationHook(ActivationHook hook) {
        activationHook = hook == null ? (level, corePos, player, stack) -> InteractionResult.PASS : hook;
    }

    public static InteractionResult activate(ServerLevel level, BlockPos corePos, ServerPlayer player, ItemStack stack) {
        return activationHook.activate(level, corePos, player, stack);
    }

    @FunctionalInterface
    public interface ActivationHook {
        InteractionResult activate(ServerLevel level, BlockPos corePos, ServerPlayer player, ItemStack stack);
    }
}
