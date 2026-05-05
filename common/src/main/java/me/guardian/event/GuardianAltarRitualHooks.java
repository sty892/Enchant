package me.guardian.event;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;

public final class GuardianAltarRitualHooks {
    private static ActivationHook activationHook = (level, corePos, player, stack) -> InteractionResult.PASS;
    private static SelectionHook selectionHook = (level, altarPos, player, stack) -> InteractionResult.PASS;

    private GuardianAltarRitualHooks() {
    }

    public static void setActivationHook(ActivationHook hook) {
        activationHook = hook == null ? (level, corePos, player, stack) -> InteractionResult.PASS : hook;
    }

    public static void setSelectionHook(SelectionHook hook) {
        selectionHook = hook == null ? (level, altarPos, player, stack) -> InteractionResult.PASS : hook;
    }

    public static InteractionResult activate(ServerLevel level, BlockPos corePos, ServerPlayer player, ItemStack stack) {
        return activationHook.activate(level, corePos, player, stack);
    }

    public static InteractionResult select(ServerLevel level, BlockPos altarPos, ServerPlayer player, ItemStack stack) {
        return selectionHook.select(level, altarPos, player, stack);
    }

    @FunctionalInterface
    public interface ActivationHook {
        InteractionResult activate(ServerLevel level, BlockPos corePos, ServerPlayer player, ItemStack stack);
    }

    @FunctionalInterface
    public interface SelectionHook {
        InteractionResult select(ServerLevel level, BlockPos altarPos, ServerPlayer player, ItemStack stack);
    }
}
