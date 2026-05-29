package me.guardian.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class TempleWallSegmentEntity extends Monster implements GeoEntity {
    private static final double INSIDE_RADIUS = 4.0D;
    private static final int EMPTY_DESPAWN_TICKS = 100;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private Vec3 ringCenter = Vec3.ZERO;
    private int age = 0;
    private int emptyInsideTicks = 0;

    public TempleWallSegmentEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 150.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    public void setRingCenter(Vec3 center) {
        this.ringCenter = center;
    }

    @Override
    protected void registerGoals() {
        // No AI goals
    }

    @Override
    public boolean isImmobile() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void push(net.minecraft.world.entity.Entity entity) {
        // Do not move when pushed
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<TempleWallSegmentEntity>("controller", 0, state -> {
            return state.setAndContinue(RawAnimation.begin().thenLoop("idle"));
        }));
    }

    @Override
    public void tick() {
        super.tick();
        this.age++;

        if (this.level().isClientSide()) {
            return;
        }

        ServerLevel serverLevel = (ServerLevel) this.level();

        if (this.age % 20 == 0) {
            int playersInside = 0;
            for (Player player : serverLevel.players()) {
                if (player.isAlive() && player.level() == this.level()) {
                    Vec3 diff = player.position().subtract(ringCenter);
                    double horizontalDist = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
                    if (horizontalDist <= INSIDE_RADIUS) {
                        playersInside++;
                    }
                }
            }

            if (playersInside == 0) {
                emptyInsideTicks += 20;
                if (emptyInsideTicks >= EMPTY_DESPAWN_TICKS) {
                    crumble(serverLevel);
                    return;
                }
            } else {
                emptyInsideTicks = 0;
                int maxLifetimeTicks = switch (playersInside) {
                    case 1 -> 400;
                    case 2 -> 600;
                    default -> 800;
                };
                if (this.age >= maxLifetimeTicks) {
                    crumble(serverLevel);
                }
            }
        }
    }

    @Override
    public void die(DamageSource cause) {
        super.die(cause);
        if (this.level() instanceof ServerLevel serverLevel) {
            crumble(serverLevel);
        }
    }

    private void crumble(ServerLevel level) {
        level.playSound(null, this.blockPosition(), net.minecraft.sounds.SoundEvents.STONE_BREAK,
                net.minecraft.sounds.SoundSource.HOSTILE, 1.5F, 0.8F);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 1.5D, this.getZ(),
                15, 0.3D, 0.5D, 0.3D, 0.05D);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE_BRICKS.defaultBlockState()),
                this.getX(), this.getY() + 1.5D, this.getZ(),
                30, 0.3D, 0.5D, 0.3D, 0.1D);
        this.discard();
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.age = input.getIntOr("Age", 0);
        double cx = input.getDoubleOr("CenterX", this.getX());
        double cy = input.getDoubleOr("CenterY", this.getY());
        double cz = input.getDoubleOr("CenterZ", this.getZ());
        this.ringCenter = new Vec3(cx, cy, cz);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("Age", this.age);
        output.putDouble("CenterX", ringCenter.x);
        output.putDouble("CenterY", ringCenter.y);
        output.putDouble("CenterZ", ringCenter.z);
    }
}
