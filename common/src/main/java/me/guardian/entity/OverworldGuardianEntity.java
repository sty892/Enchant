package me.guardian.entity;

import me.guardian.GuardianMod;
import me.guardian.event.GuardianBossEventHooks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.constant.DefaultAnimations;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class OverworldGuardianEntity extends Monster implements GeoEntity {
    private static final String BOSS_CONFIG_KEY = "overworld";
    private static final int FIREBALL_COOLDOWN_TICKS = 100;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Map<UUID, Float> damageContributors = new HashMap<>();
    private boolean spawnEventTriggered = false;
    private boolean phase75Triggered = false;
    private boolean phase50Triggered = false;
    private boolean phase25Triggered = false;
    private boolean deathEventTriggered = false;
    private int fireballCooldown = FIREBALL_COOLDOWN_TICKS;

    public OverworldGuardianEntity(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 500.0)
                .add(Attributes.ATTACK_DAMAGE, 15.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FOLLOW_RANGE, 64.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8);
    }

    public Map<UUID, Float> getDamageContributors() {
        return damageContributors;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 16.0F));
        this.goalSelector.addGoal(3, new RandomStrollGoal(this, 0.8));
        this.goalSelector.addGoal(4, new MeleeAttackGoal(this, 1.0, true));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean damaged = super.hurtServer(level, source, amount);
        if (damaged && source.getEntity() instanceof ServerPlayer player) {
            damageContributors.merge(player.getUUID(), amount, Float::sum);
        }
        return damaged;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        triggerSpawnEvent(level);
        tickPhases();
        tickFireball(level);
    }

    @Override
    public void die(DamageSource damageSource) {
        if (!deathEventTriggered && this.level() instanceof ServerLevel serverLevel) {
            deathEventTriggered = true;
            GuardianBossEventHooks.triggerOnDeath(BOSS_CONFIG_KEY, serverLevel, this.blockPosition(), this, damageContributors);
        }
        super.die(damageSource);
    }

    private void triggerSpawnEvent(ServerLevel level) {
        if (spawnEventTriggered) {
            return;
        }
        spawnEventTriggered = true;
        GuardianBossEventHooks.triggerOnSpawn(BOSS_CONFIG_KEY, level, this.blockPosition(), this);
    }

    private void tickPhases() {
        float healthRatio = this.getHealth() / this.getMaxHealth();
        if (!phase75Triggered && healthRatio <= 0.75F) {
            phase75Triggered = true;
            setBaseAttribute(Attributes.MOVEMENT_SPEED, 0.30);
            GuardianMod.LOGGER.info("Overworld Guardian 75% phase hook triggered; generic summon is deferred until Module 10");
        }
        if (!phase50Triggered && healthRatio <= 0.50F) {
            phase50Triggered = true;
            leapAtTarget();
            GuardianMod.LOGGER.info("Overworld Guardian 50% phase hook triggered");
        }
        if (!phase25Triggered && healthRatio <= 0.25F) {
            phase25Triggered = true;
            setBaseAttribute(Attributes.MOVEMENT_SPEED, 0.50);
            setBaseAttribute(Attributes.ATTACK_DAMAGE, 22.5);
            GuardianMod.LOGGER.info("Overworld Guardian 25% berserk phase hook triggered");
        }
    }

    private void setBaseAttribute(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, double value) {
        var instance = this.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    private void leapAtTarget() {
        LivingEntity target = this.getTarget();
        if (target == null) {
            return;
        }

        Vec3 direction = target.position().subtract(this.position()).horizontal();
        if (direction.lengthSqr() > 0.0001D) {
            this.setDeltaMovement(direction.normalize().scale(1.2D).add(0.0D, 0.45D, 0.0D));
        }
    }

    private void tickFireball(ServerLevel level) {
        if (fireballCooldown > 0) {
            fireballCooldown--;
            return;
        }

        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive()) {
            fireballCooldown = 20;
            return;
        }

        Vec3 origin = this.position().add(0.0D, this.getEyeHeight() * 0.75D, 0.0D);
        Vec3 targetPos = target.position().add(0.0D, target.getEyeHeight() * 0.5D, 0.0D);
        Vec3 direction = targetPos.subtract(origin).normalize();
        SmallFireball fireball = new SmallFireball(level, this, direction);
        fireball.setPos(origin.x, origin.y, origin.z);
        level.addFreshEntity(fireball);
        fireballCooldown = FIREBALL_COOLDOWN_TICKS;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(DefaultAnimations.genericWalkIdleController());
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
