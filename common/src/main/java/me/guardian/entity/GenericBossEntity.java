package me.guardian.entity;

import me.guardian.event.GuardianBossEventHooks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.constant.DefaultAnimations;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class GenericBossEntity extends Monster implements GeoEntity {
    private static final String BOSS_CONFIG_KEY = "generic";
    private static final int CHARGE_COOLDOWN_TICKS = 200;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Map<UUID, Float> damageContributors = new HashMap<>();
    private String variant = "default";
    private boolean summoned = false;
    private boolean phase33Triggered = false;
    private boolean deathEventTriggered = false;
    private int chargeCooldown = CHARGE_COOLDOWN_TICKS;
    private int groupBuffCooldown = 20;

    public GenericBossEntity(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 300.0)
                .add(Attributes.ATTACK_DAMAGE, 12.0)
                .add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.FOLLOW_RANGE, 48.0);
    }

    public String getVariant() {
        return variant;
    }

    public void setVariant(String variant) {
        this.variant = variant == null || variant.isBlank() ? "default" : variant;
    }

    public boolean isSummoned() {
        return summoned;
    }

    public void setSummoned(boolean summoned) {
        this.summoned = summoned;
    }

    public Map<UUID, Float> getDamageContributors() {
        return damageContributors;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 16.0F));
        this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.0, true));
        this.goalSelector.addGoal(4, new RandomStrollGoal(this, 0.8));

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
        tickCharge();
        tickGroupBuff(level);
        tickSummonPhase(level);
    }

    @Override
    public void die(DamageSource damageSource) {
        if (!summoned && !deathEventTriggered && this.level() instanceof ServerLevel serverLevel) {
            deathEventTriggered = true;
            GuardianBossEventHooks.triggerOnDeath(BOSS_CONFIG_KEY, serverLevel, this.blockPosition(), this, damageContributors);
        }
        super.die(damageSource);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putString("variant", variant);
        output.putBoolean("isSummoned", summoned);
        output.putBoolean("phase33Triggered", phase33Triggered);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.variant = input.getStringOr("variant", "default");
        this.summoned = input.getBooleanOr("isSummoned", false);
        this.phase33Triggered = input.getBooleanOr("phase33Triggered", false);
    }

    private void tickCharge() {
        if (chargeCooldown > 0) {
            chargeCooldown--;
            return;
        }

        LivingEntity target = this.getTarget();
        if (target != null && target.isAlive()) {
            Vec3 direction = target.position().subtract(this.position()).horizontal();
            if (direction.lengthSqr() > 0.0001D) {
                this.setDeltaMovement(direction.normalize().scale(1.6D).add(0.0D, 0.2D, 0.0D));
            }
        }
        chargeCooldown = CHARGE_COOLDOWN_TICKS;
    }

    private void tickGroupBuff(ServerLevel level) {
        if (groupBuffCooldown > 0) {
            groupBuffCooldown--;
            return;
        }
        groupBuffCooldown = 20;

        AABB area = this.getBoundingBox().inflate(10.0D);
        List<GenericBossEntity> nearby = level.getEntities(ModEntities.GENERIC_BOSS, area, boss -> boss.isAlive());
        if (nearby.size() >= 3) {
            for (GenericBossEntity boss : nearby) {
                boss.addEffect(new MobEffectInstance(MobEffects.SPEED, 60, 0));
                boss.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 60, 0));
            }
        }
    }

    private void tickSummonPhase(ServerLevel level) {
        if (phase33Triggered || this.getHealth() / this.getMaxHealth() > 0.33F) {
            return;
        }

        phase33Triggered = true;
        for (int i = 0; i < 2; i++) {
            GenericBossEntity minion = ModEntities.GENERIC_BOSS.create(level, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
            if (minion != null) {
                minion.setVariant(variant);
                minion.setSummoned(true);
                minion.setPos(this.getX() + (i == 0 ? 2.0D : -2.0D), this.getY(), this.getZ());
                minion.setYRot(this.getYRot());
                minion.setXRot(this.getXRot());
                level.addFreshEntity(minion);
            }
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(DefaultAnimations.genericWalkIdleController());
        controllers.add(DefaultAnimations.genericAttackAnimation(DefaultAnimations.ATTACK_SWING));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
