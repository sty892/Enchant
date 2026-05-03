package me.sty892.enchant.event;

import me.sty892.enchant.config.ConfigManager;
import me.sty892.enchant.state.GuardianWorldState;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class DiamondRestrictionHandler {
    public static void register() {
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (world.isClient) return true;
            if (!ConfigManager.getConfig().diamond_restriction_enabled) return true;
            if (ConfigManager.getConfig().op_diamond_whitelist.contains(player.getUuid())) return true;

            GuardianWorldState worldState = GuardianWorldState.getServerState(world.getServer());
            if (!worldState.overworldBossDefeated) {
                if (state.isOf(Blocks.DIAMOND_ORE) || state.isOf(Blocks.DEEPSLATE_DIAMOND_ORE)) {
                    player.sendMessage(Text.literal("Нельзя получить алмазы пока не убит Хранитель Верхнего Мира"), true);
                    return false;
                }
            }
            return true;
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 20 != 0) return;
            if (!ConfigManager.getConfig().diamond_restriction_enabled) return;

            GuardianWorldState worldState = GuardianWorldState.getServerState(server);
            if (worldState.overworldBossDefeated) return;

            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (ConfigManager.getConfig().op_diamond_whitelist.contains(player.getUuid())) continue;

                PlayerInventory inventory = player.getInventory();
                boolean removed = false;
                for (int i = 0; i < inventory.size(); i++) {
                    ItemStack stack = inventory.getStack(i);
                    if (isDiamondItem(stack)) {
                        inventory.setStack(i, ItemStack.EMPTY);
                        removed = true;
                    }
                }
                if (removed) {
                    player.sendMessage(Text.literal("Алмазы были изъяты (Хранитель Верхнего Мира еще жив)"), true);
                }
            }
        });
    }

    private static boolean isDiamondItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        String path = Registries.ITEM.getId(stack.getItem()).getPath();
        return path.startsWith("diamond") || path.equals("diamond");
    }
}
