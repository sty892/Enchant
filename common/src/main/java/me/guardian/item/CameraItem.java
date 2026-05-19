package me.guardian.item;

import me.guardian.entity.CameraMarkerEntity;
import me.guardian.entity.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public class CameraItem extends Item {
    public CameraItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (!level.isClientSide() && player != null) {
            boolean isAllowed = player.isCreative() || 
                (player instanceof ServerPlayer serverPlayer && serverPlayer.level().getServer().getPlayerList().isOp(serverPlayer.nameAndId()));
            if (isAllowed) {
                BlockPos clickedPos = context.getClickedPos();
                Direction clickedFace = context.getClickedFace();
                BlockPos spawnPos = clickedPos.relative(clickedFace);

                CameraMarkerEntity entity = new CameraMarkerEntity(ModEntities.CAMERA_MARKER, level);
                entity.setPos(
                        spawnPos.getX() + 0.5,
                        spawnPos.getY(),
                        spawnPos.getZ() + 0.5
                );
                entity.setYRot(player.getYRot());
                entity.setXRot(player.getXRot());
                level.addFreshEntity(entity);
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.SUCCESS;
    }
}
