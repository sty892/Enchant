package me.guardian.item;

import me.guardian.event.GuardianTriggerHooks;
import me.guardian.system.GuardianSystemsState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

public class TriggerAreaCreatorItem extends Item {
    public TriggerAreaCreatorItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!GuardianSystemsState.triggerSystemsEnabled(context.getLevel())) {
            if (!context.getLevel().isClientSide() && context.getPlayer() != null) {
                context.getPlayer().displayClientMessage(Component.translatable("message.guardian_mod.trigger_systems.disabled"), true);
            }
            return InteractionResult.FAIL;
        }
        if (!context.getLevel().isClientSide() && context.getPlayer() != null) {
            GuardianTriggerHooks.setPoint(context.getPlayer(), context.getClickedPos(), true);
        }
        return InteractionResult.SUCCESS;
    }
}
