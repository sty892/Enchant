package me.guardian.item;

import me.guardian.system.GuardianSystemsState;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

public class SystemToggleItem extends Item {
    public SystemToggleItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level instanceof ServerLevel serverLevel) {
            boolean enabled = GuardianSystemsState.get(serverLevel).toggleTriggerSystems();
            player.displayClientMessage(Component.translatable(enabled
                    ? "message.guardian_mod.trigger_systems.enabled"
                    : "message.guardian_mod.trigger_systems.disabled"), true);
        }
        return InteractionResult.SUCCESS;
    }
}
