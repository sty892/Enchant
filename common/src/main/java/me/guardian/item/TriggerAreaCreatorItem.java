package me.guardian.item;

import me.guardian.event.GuardianTriggerHooks;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

public class TriggerAreaCreatorItem extends Item {
    public TriggerAreaCreatorItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!context.getLevel().isClientSide() && context.getPlayer() != null) {
            GuardianTriggerHooks.setPoint(context.getPlayer(), context.getClickedPos(), true);
        }
        return InteractionResult.SUCCESS;
    }
}
