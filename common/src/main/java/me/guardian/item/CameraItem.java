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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CameraItem extends Item {
    /** Cooldown tracking: player UUID -> last use tick (server-side) */
    private static final Map<UUID, Long> LAST_USE_TICK = new HashMap<>();
    /** 1 second = 20 ticks cooldown */
    private static final long COOLDOWN_TICKS = 20;

    public CameraItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (!level.isClientSide() && player != null) {
            if (isAllowed(player) && !isOnCooldown(player, level)) {
                recordUse(player, level);
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
        if (!level.isClientSide() && isAllowed(player) && !isOnCooldown(player, level)) {
            recordUse(player, level);
            spawnCamera(level, player, player.getX(), player.getEyeY(), player.getZ());
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.SUCCESS;
    }

    private static boolean isOnCooldown(Player player, Level level) {
        Long last = LAST_USE_TICK.get(player.getUUID());
        if (last == null) {
            return false;
        }
        return (level.getGameTime() - last) < COOLDOWN_TICKS;
    }

    private static void recordUse(Player player, Level level) {
        LAST_USE_TICK.put(player.getUUID(), level.getGameTime());
    }

    private static boolean isAllowed(Player player) {
        return player.isCreative()
                || (player instanceof ServerPlayer serverPlayer
                && serverPlayer.level().getServer().getPlayerList().isOp(serverPlayer.nameAndId()));
    }

    private static void spawnCamera(Level level, Player player, double x, double y, double z) {
        // Compute next camera index by finding max existing index for same cutscene + 1
        int nextIndex = computeNextIndex(level);

        CameraMarkerEntity entity = new CameraMarkerEntity(ModEntities.CAMERA_MARKER, level);
        entity.setPos(x, y, z);
        entity.setYRot(player.getYRot());
        entity.setXRot(player.getXRot());
        entity.setIndex(nextIndex);
        level.addFreshEntity(entity);
    }

    private static int computeNextIndex(Level level) {
        int maxIndex = 0;
        // Iterate all entities in the level to find the highest camera index
        for (var entity : level.getEntitiesOfClass(CameraMarkerEntity.class,
                new net.minecraft.world.phys.AABB(-30000000, -2048, -30000000, 30000000, 4096, 30000000),
                e -> true)) {
            if (entity.getIndex() > maxIndex) {
                maxIndex = entity.getIndex();
            }
        }
        return maxIndex + 1;
    }
}
