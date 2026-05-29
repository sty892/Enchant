package me.guardian.entity;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.UUID;

/**
 * Visual vine between the Overworld Guardian and a pulled target (attack vine_pull).
 * Model: {@code geckolib/models/entity/vine_lash_fallback.geo.json} (from leans.bbmodel).
 */
public class VineLashEntity extends Entity implements GeoEntity {
    private static final EntityDataAccessor<Float> DATA_LENGTH =
            SynchedEntityData.defineId(VineLashEntity.class, EntityDataSerializers.FLOAT);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private UUID bossUuid;
    private UUID targetUuid;
    private int maxAge = 60;
    // Which slice of the boss→target line this vine occupies (so several stacked vines reach the target).
    private int segmentIndex = 0;
    private int segmentCount = 1;

    public VineLashEntity(EntityType<VineLashEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public VineLashEntity(Level level, OverworldGuardianEntity boss, LivingEntity target, int lifetimeTicks) {
        this(level, boss, target, lifetimeTicks, 0, 1);
    }

    public VineLashEntity(Level level, OverworldGuardianEntity boss, LivingEntity target, int lifetimeTicks,
                          int segmentIndex, int segmentCount) {
        this(ModEntities.VINE_LASH, level);
        this.bossUuid = boss.getUUID();
        this.targetUuid = target.getUUID();
        this.maxAge = lifetimeTicks;
        this.segmentIndex = segmentIndex;
        this.segmentCount = Math.max(1, segmentCount);
        updatePose(level, boss, target);
    }

    public float getVisualLength() {
        return this.entityData.get(DATA_LENGTH);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_LENGTH, 1.0F);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<VineLashEntity>("controller", 0, state ->
                state.setAndContinue(RawAnimation.begin().thenLoop("idle"))));
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            return;
        }
        ServerLevel serverLevel = (ServerLevel) level();
        Entity bossEntity = bossUuid != null ? serverLevel.getEntity(bossUuid) : null;
        Entity targetEntity = targetUuid != null ? serverLevel.getEntity(targetUuid) : null;
        if (!(bossEntity instanceof LivingEntity boss) || !(targetEntity instanceof LivingEntity target) || !target.isAlive()) {
            discard();
            return;
        }
        updatePose(serverLevel, boss, target);
        if (tickCount >= maxAge) {
            discard();
        }
    }

    private void updatePose(Level level, LivingEntity boss, LivingEntity target) {
        Vec3 from = boss.position().add(0.0D, boss.getBbHeight() * 0.55D, 0.0D);
        Vec3 to = target.position().add(0.0D, target.getBbHeight() * 0.45D, 0.0D);
        Vec3 diff = to.subtract(from);
        // Place this vine at its slice of the line so several stacked vines together reach the target.
        double frac = (segmentIndex + 0.5D) / segmentCount;
        Vec3 mid = from.add(diff.scale(frac));
        float length = (float) Math.max(0.5D, diff.length() / segmentCount);
        this.setPos(mid.x, mid.y, mid.z);
        this.entityData.set(DATA_LENGTH, length);
        double yaw = Math.atan2(diff.z, diff.x);
        this.setYRot((float) Math.toDegrees(yaw) - 90.0F);
        this.setXRot((float) Math.toDegrees(Math.atan2(diff.y, Math.sqrt(diff.x * diff.x + diff.z * diff.z))));
        this.setOldPosAndRot();
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        this.maxAge = input.getIntOr("MaxAge", 60);
        this.segmentIndex = input.getIntOr("SegIndex", 0);
        this.segmentCount = Math.max(1, input.getIntOr("SegCount", 1));
        input.getString("BossUUID").ifPresent(s -> {
            try {
                this.bossUuid = UUID.fromString(s);
            } catch (IllegalArgumentException ignored) {
            }
        });
        input.getString("TargetUUID").ifPresent(s -> {
            try {
                this.targetUuid = UUID.fromString(s);
            } catch (IllegalArgumentException ignored) {
            }
        });
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putInt("MaxAge", maxAge);
        output.putInt("SegIndex", segmentIndex);
        output.putInt("SegCount", segmentCount);
        if (bossUuid != null) {
            output.putString("BossUUID", bossUuid.toString());
        }
        if (targetUuid != null) {
            output.putString("TargetUUID", targetUuid.toString());
        }
    }
}
