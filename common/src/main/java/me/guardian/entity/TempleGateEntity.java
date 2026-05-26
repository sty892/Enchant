package me.guardian.entity;

import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * An indestructible gate entity that closes the temple arena at the start of the boss fight.
 * Players cannot destroy it, but it can be opened/closed by the boss logic.
 * When open, it is invisible and has no collision (noPhysics=true).
 * When closed, it blocks passage and pushes players back towards the center of the arena.
 */
public class TempleGateEntity extends Entity {

    private boolean closed = false;
    private BlockPos arenaCenter = null;

    public TempleGateEntity(EntityType<TempleGateEntity> type, Level level) {
        super(type, level);
        noPhysics = true; // starts open/passable
    }

    public TempleGateEntity(Level level, double x, double y, double z) {
        this(ModEntities.TEMPLE_GATE, level);
        this.setPos(x, y, z);
    }

    public void setArenaCenter(BlockPos pos) {
        this.arenaCenter = pos;
    }

    public void close() {
        this.closed = true;
        this.noPhysics = false;
    }

    public void open() {
        this.closed = false;
        this.noPhysics = true;
    }

    public boolean isClosed() {
        return closed;
    }

    @Override
    public boolean canBeCollidedWith(Entity entity) {
        return this.closed;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        // Completely indestructible by players
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        // Gates don't move on their own
        this.setDeltaMovement(0.0D, 0.0D, 0.0D);

        if (this.closed) {
            // Expand bounding box to 6x5x6 to fully block typical arena gates
            double w = 3.0D;
            double h = 5.0D;
            this.setBoundingBox(new AABB(
                    this.getX() - w, this.getY(), this.getZ() - w,
                    this.getX() + w, this.getY() + h, this.getZ() + w
            ));

            // Force push players who manage to get near the gates back towards the center
            if (!level().isClientSide() && arenaCenter != null) {
                Vec3 centerVec = Vec3.atCenterOf(arenaCenter);
                var checkRange = this.getBoundingBox().inflate(0.5D);
                for (net.minecraft.world.entity.player.Player player : level().getEntitiesOfClass(net.minecraft.world.entity.player.Player.class, checkRange)) {
                    if (!player.isCreative() && !player.isSpectator()) {
                        Vec3 pushDir = centerVec.subtract(player.position()).horizontal().normalize().scale(0.35D);
                        player.setDeltaMovement(pushDir.x, 0.15D, pushDir.z);
                        player.hurtMarked = true; // force sync velocity to client
                    }
                }
            }
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        boolean wasClosed = input.getBooleanOr("Closed", false);
        if (wasClosed) {
            close();
        } else {
            open();
        }
        if (input.getInt("ArenaCenterX").isPresent()
                && input.getInt("ArenaCenterY").isPresent()
                && input.getInt("ArenaCenterZ").isPresent()) {
            this.arenaCenter = new BlockPos(
                    input.getIntOr("ArenaCenterX", 0),
                    input.getIntOr("ArenaCenterY", 0),
                    input.getIntOr("ArenaCenterZ", 0)
            );
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putBoolean("Closed", closed);
        if (arenaCenter != null) {
            output.putInt("ArenaCenterX", arenaCenter.getX());
            output.putInt("ArenaCenterY", arenaCenter.getY());
            output.putInt("ArenaCenterZ", arenaCenter.getZ());
        }
    }
}

