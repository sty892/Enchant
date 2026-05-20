package me.guardian.item;

import me.guardian.entity.CameraMarkerEntity;
import me.guardian.entity.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
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
            if (isAllowed(player)) {
                BlockPos clickedPos = context.getClickedPos();
                Direction clickedFace = context.getClickedFace();
                BlockPos spawnPos = clickedPos.relative(clickedFace);

                spawnCamera(level, player,
                        spawnPos.getX() + 0.5,
                        spawnPos.getY(),
                        spawnPos.getZ() + 0.5);
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide() && isAllowed(player)) {
            spawnCamera(level, player, player.getX(), player.getEyeY(), player.getZ());
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.SUCCESS;
    }

    private static boolean isAllowed(Player player) {
        return player.isCreative()
                || (player instanceof ServerPlayer serverPlayer
                && serverPlayer.level().getServer().getPlayerList().isOp(serverPlayer.nameAndId()));
    }

    private static void spawnCamera(Level level, Player player, double x, double y, double z) {
        CameraMarkerEntity entity = new CameraMarkerEntity(ModEntities.CAMERA_MARKER, level);
        entity.setPos(x, y, z);
        entity.setYRot(player.getYRot());
        entity.setXRot(player.getXRot());
        level.addFreshEntity(entity);
    }
}
