package me.guardian.block;

import me.guardian.dimension.GuardianDimensions;
import me.guardian.item.ModItems;
import me.guardian.system.GuardianSystemsState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class DimensionTriggerBlock extends Block {
    private static final VoxelShape VISIBLE_SHAPE = Shapes.block();
    private final boolean returnToOverworld;

    public DimensionTriggerBlock(Properties properties, boolean returnToOverworld) {
        super(properties);
        this.returnToOverworld = returnToOverworld;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return context.isHoldingItem(ModItems.TRIGGER_REVEALER) ? VISIBLE_SHAPE : Shapes.empty();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean wasInside) {
        if (!(level instanceof ServerLevel serverLevel)
                || !(entity instanceof ServerPlayer player)
                || !GuardianSystemsState.get(serverLevel).triggerSystemsEnabled()
                || player.isOnPortalCooldown()
                || player.isHolding(ModItems.TRIGGER_REVEALER)) {
            return;
        }

        ServerLevel targetLevel = returnToOverworld
                ? serverLevel.getServer().overworld()
                : serverLevel.getServer().getLevel(GuardianDimensions.GUARDIAN_DIMENSION);
        if (targetLevel == null || player.level().dimension().equals(targetLevel.dimension())) {
            return;
        }

        player.setPortalCooldown(80);
        player.teleport(new TeleportTransition(
                targetLevel,
                new Vec3(pos.getX() + 0.5D, 80.0D, pos.getZ() + 0.5D),
                player.getDeltaMovement(),
                player.getYRot(),
                player.getXRot(),
                TeleportTransition.DO_NOTHING
        ));
    }
}
