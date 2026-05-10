package me.guardian.entity;

import me.guardian.GuardianMod;
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
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class NetherGuardianEntity extends Monster implements GeoEntity {
    private static final String BOSS_CONFIG_KEY = "nether";
    private static final int FIREBALL_BARRAGE_COOLDOWN_TICKS = 160;
    private static final RawAnimation IDLE_ANIMATION = RawAnimation.begin().thenLoop("animation.guardian_mod.boss_nether.idle");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Map<UUID, Float> damageContributors = new HashMap<>();
    private boolean spawnEventTriggered = false;
    private boolean phase50Triggered = false;
    private boolean phase25Triggered = false;
    private boolean deathEventTriggered = false;
    private int barrageCooldown = FIREBALL_BARRAGE_COOLDOWN_TICKS;

    public NetherGuardianEntity(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 750.0)
                .add(Attributes.ATTACK_DAMAGE, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.30)
                .add(Attributes.FOLLOW_RANGE, 80.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    public Map<UUID, Float> getDamageContributors() {
        return damageContributors;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 20.0F));
        this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.1, true));
        this.goalSelector.addGoal(4, new RandomStrollGoal(this, 0.8));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, net.minecraft.world.entity.Entity target) {
        boolean hurt = super.doHurtTarget(level, target);
        if (hurt) {
            target.igniteForSeconds(5.0F);
        }
        return hurt;
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
        tickPhases(level);
        tickFireballBarrage(level);
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

    private void tickPhases(ServerLevel level) {
        float healthRatio = this.getHealth() / this.getMaxHealth();
        if (!phase50Triggered && healthRatio <= 0.50F) {
            phase50Triggered = true;
            this.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 60, 0));
            GuardianMod.LOGGER.info("Nether Guardian 50% phase hook triggered; fire pillars are deferred until Module 9 polish/structure effects");
        }
        if (!phase25Triggered && healthRatio <= 0.25F) {
            phase25Triggered = true;
            for (Player player : level.players()) {
                if (player.distanceToSqr(this) <= 400.0D) {
                    player.addEffect(new MobEffectInstance(MobEffects.WITHER, 200, 1));
                }
            }
            GuardianMod.LOGGER.info("Nether Guardian 25% wither phase hook triggered");
        }
    }

    private void tickFireballBarrage(ServerLevel level) {
        if (barrageCooldown > 0) {
            barrageCooldown--;
            return;
        }

        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive()) {
            barrageCooldown = 20;
            return;
        }

        Vec3 origin = this.position().add(0.0D, this.getEyeHeight() * 0.75D, 0.0D);
        Vec3 baseDirection = target.position().add(0.0D, target.getEyeHeight() * 0.5D, 0.0D).subtract(origin).normalize();
        for (int i = 0; i < 5; i++) {
            Vec3 spread = baseDirection.add((this.random.nextDouble() - 0.5D) * 0.15D, (this.random.nextDouble() - 0.5D) * 0.10D, (this.random.nextDouble() - 0.5D) * 0.15D).normalize();
            SmallFireball fireball = new SmallFireball(level, this, spread);
            fireball.setPos(origin.x, origin.y, origin.z);
            level.addFreshEntity(fireball);
        }
        barrageCooldown = FIREBALL_BARRAGE_COOLDOWN_TICKS;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>("Idle", state -> state.setAndContinue(IDLE_ANIMATION)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
