package me.guardian.entity;

import me.guardian.GuardianMod;
import me.guardian.event.GuardianBossEventHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class OverworldGuardianEntity extends Monster implements GeoEntity {
    private static final String BOSS_CONFIG_KEY = "overworld";
    private static final String SPAWN_CONTROLLER_NAME = "Spawn";
    private static final String SPAWN_TRIGGER_NAME = "spawn";
    private static final String PHASE_SHIFT_TRIGGER = "phase_shift";
    private static final double PREFERRED_HOME_RADIUS = 16.0D;
    private static final double SOFT_RETURN_RADIUS = 19.0D;
    private static final double SOFT_RETURN_RADIUS_SQR = SOFT_RETURN_RADIUS * SOFT_RETURN_RADIUS;
    private static final double BOSS_BAR_RADIUS_SQR = 30.0D * 30.0D;
    private static final int SPAWN_ANIMATION_TICKS = 80;
    private static final RawAnimation SPAWN_ANIMATION = RawAnimation.begin().thenPlay("spawn");
    private static final RawAnimation IDLE_ANIMATION = RawAnimation.begin().thenLoop("idle");
    private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(
            OverworldGuardianEntity.class,
            EntityDataSerializers.INT
    );

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Map<UUID, Float> damageContributors = new HashMap<>();
    private final OverworldGuardianThreatTable threatTable = new OverworldGuardianThreatTable();
    private final OverworldGuardianAttackController attackController = new OverworldGuardianAttackController(this);
    private final ServerBossEvent bossEvent = new ServerBossEvent(
            bossName(),
            BossEvent.BossBarColor.GREEN,
            BossEvent.BossBarOverlay.NOTCHED_6
    );
    private OverworldGuardianPhase appliedPhase = OverworldGuardianPhase.ONE;
    private boolean spawnEventTriggered = false;
    private boolean deathEventTriggered = false;
    private boolean spawnAnimationTriggered = false;
    private BlockPos spawnCenter = null;
    private int unansweredHits = 0;
    private UUID counterTargetId = null;

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

    public OverworldGuardianThreatTable getThreatTable() {
        return threatTable;
    }

    public OverworldGuardianPhase getBossPhase() {
        return OverworldGuardianPhase.byId(this.entityData.get(DATA_PHASE));
    }

    public void triggerAttackAnimation(String triggerName) {
        GuardianBossAi.triggerAttackAnimation(this, triggerName);
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_PHASE, OverworldGuardianPhase.ONE.id());
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 16.0F));
        this.goalSelector.addGoal(3, new RandomStrollGoal(this, 0.8));
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new GuardianBossNavigation(this, level);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean damaged = super.hurtServer(level, source, amount);
        if (damaged && source.getEntity() instanceof LivingEntity attacker && attacker != this) {
            counterTargetId = attacker.getUUID();
            unansweredHits++;
            if (attacker instanceof ServerPlayer player) {
                damageContributors.merge(player.getUUID(), amount, Float::sum);
                threatTable.recordDamage(player, amount, this.tickCount);
            }
        }
        return damaged;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        GuardianBossAi.ensureSpawnHome(this);
        triggerSpawnEvent(level);
        triggerSpawnAnimation();
        threatTable.tick(this, level);
        tickTarget(level);
        tickPhase();
        tickSoftHomeReturn();
        attackController.tick(level);
        tickBossBar(level);
    }

    @Override
    public float getWalkTargetValue(BlockPos pos, LevelReader level) {
        return GuardianBossAi.applySpawnDistancePenalty(this, pos, super.getWalkTargetValue(pos, level));
    }

    @Override
    public void die(DamageSource damageSource) {
        if (!deathEventTriggered && this.level() instanceof ServerLevel serverLevel) {
            deathEventTriggered = true;
            GuardianBossEventHooks.triggerOnDeath(BOSS_CONFIG_KEY, serverLevel, this.blockPosition(), this, damageContributors);
        }
        removeBossBarPlayers();
        super.die(damageSource);
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        removeBossBarPlayers();
        super.remove(reason);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("BossPhase", getBossPhase().id());
        if (spawnCenter != null) {
            output.putInt("SpawnCenterX", spawnCenter.getX());
            output.putInt("SpawnCenterY", spawnCenter.getY());
            output.putInt("SpawnCenterZ", spawnCenter.getZ());
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        setBossPhase(OverworldGuardianPhase.byId(input.getIntOr("BossPhase", 1)));
        if (input.getInt("SpawnCenterX").isPresent()
                && input.getInt("SpawnCenterY").isPresent()
                && input.getInt("SpawnCenterZ").isPresent()) {
            this.spawnCenter = new BlockPos(
                    input.getIntOr("SpawnCenterX", 0),
                    input.getIntOr("SpawnCenterY", 0),
                    input.getIntOr("SpawnCenterZ", 0)
            );
        }
    }

    private void triggerSpawnEvent(ServerLevel level) {
        if (spawnEventTriggered) {
            return;
        }
        spawnEventTriggered = true;
        if (spawnCenter == null) {
            spawnCenter = this.blockPosition();
        }
        GuardianBossEventHooks.triggerOnSpawn(BOSS_CONFIG_KEY, level, this.blockPosition(), this);
    }

    private void triggerSpawnAnimation() {
        if (spawnAnimationTriggered || this.tickCount < 2) {
            return;
        }
        spawnAnimationTriggered = true;
        this.triggerAnim(SPAWN_CONTROLLER_NAME, SPAWN_TRIGGER_NAME);
    }

    public boolean shouldReturnTowardHome() {
        if (spawnCenter == null) {
            return false;
        }
        return distanceToHomeSqr() > SOFT_RETURN_RADIUS_SQR;
    }

    public Vec3 homeCenter() {
        return Vec3.atCenterOf(spawnCenter == null ? this.blockPosition() : spawnCenter);
    }

    public boolean isNearHome(LivingEntity entity, double extraRadius) {
        if (spawnCenter == null) {
            return true;
        }
        double radius = PREFERRED_HOME_RADIUS + extraRadius;
        return entity.position().distanceToSqr(homeCenter()) <= radius * radius;
    }

    public LivingEntity peekCounterTarget(ServerLevel level) {
        return counterTarget(level, false);
    }

    public LivingEntity consumeCounterTarget(ServerLevel level) {
        return counterTarget(level, true);
    }

    public void recordSuccessfulHit() {
        unansweredHits = 0;
        counterTargetId = null;
    }

    private LivingEntity counterTarget(ServerLevel level, boolean consume) {
        if (getBossPhase().id() < 2 || unansweredHits < 3 || counterTargetId == null) {
            return null;
        }
        for (Entity entity : level.getAllEntities()) {
            if (counterTargetId.equals(entity.getUUID()) && entity instanceof LivingEntity living && living.isAlive()) {
                if (consume) {
                    unansweredHits = 0;
                }
                return living;
            }
        }
        if (consume) {
            unansweredHits = 0;
            counterTargetId = null;
        }
        return null;
    }

    private void tickSoftHomeReturn() {
        if (spawnCenter == null) {
            spawnCenter = this.blockPosition();
            return;
        }
        Vec3 center = Vec3.atCenterOf(spawnCenter);
        Vec3 offset = this.position().subtract(center);
        double horizontalDistanceSqr = offset.x * offset.x + offset.z * offset.z;
        double preferredRadiusSqr = PREFERRED_HOME_RADIUS * PREFERRED_HOME_RADIUS;
        if (horizontalDistanceSqr <= preferredRadiusSqr) {
            return;
        }

        double horizontalDistance = Math.sqrt(horizontalDistanceSqr);
        Vec3 inward = new Vec3(center.x - this.getX(), 0.0D, center.z - this.getZ()).normalize();
        double excess = horizontalDistance - PREFERRED_HOME_RADIUS;
        double strength = Math.min(0.09D, 0.015D + excess * 0.008D);
        this.addDeltaMovement(inward.scale(strength));
    }

    private double distanceToHomeSqr() {
        if (spawnCenter == null) {
            return 0.0D;
        }
        Vec3 center = Vec3.atCenterOf(spawnCenter);
        double x = this.getX() - center.x;
        double z = this.getZ() - center.z;
        return x * x + z * z;
    }

    private void tickTarget(ServerLevel level) {
        LivingEntity nextTarget = threatTable.chooseTarget(this, level);
        if (nextTarget != this.getTarget()) {
            this.setTarget(nextTarget);
        }
    }

    private void tickPhase() {
        OverworldGuardianPhase nextPhase = OverworldGuardianPhase.fromHealth(this.getHealth() / this.getMaxHealth());
        OverworldGuardianPhase currentPhase = getBossPhase();
        if (nextPhase != currentPhase) {
            setBossPhase(nextPhase);
            if (nextPhase.id() > currentPhase.id()) {
                triggerAttackAnimation(PHASE_SHIFT_TRIGGER);
                GuardianMod.LOGGER.info("Overworld Guardian advanced to phase {}", nextPhase.id());
            }
        }
        if (nextPhase != appliedPhase) {
            appliedPhase = nextPhase;
            setBaseAttribute(Attributes.MOVEMENT_SPEED, nextPhase.movementSpeed());
            setBaseAttribute(Attributes.ATTACK_DAMAGE, nextPhase.attackDamage());
            bossEvent.setColor(nextPhase.bossBarColor());
            bossEvent.setOverlay(BossEvent.BossBarOverlay.NOTCHED_6);
            bossEvent.setName(bossName());
        }
    }

    private void setBossPhase(OverworldGuardianPhase phase) {
        this.entityData.set(DATA_PHASE, phase.id());
    }

    private void setBaseAttribute(net.minecraft.core.Holder<Attribute> attribute, double value) {
        var instance = this.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    private void tickBossBar(ServerLevel level) {
        bossEvent.setProgress(Math.max(0.0F, this.getHealth() / this.getMaxHealth()));
        bossEvent.setOverlay(BossEvent.BossBarOverlay.NOTCHED_6);
        bossEvent.setName(bossName());
        Set<ServerPlayer> eligiblePlayers = new HashSet<>();
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(this) <= BOSS_BAR_RADIUS_SQR) {
                eligiblePlayers.add(player);
            }
        }
        for (ServerPlayer player : Set.copyOf(bossEvent.getPlayers())) {
            if (!eligiblePlayers.contains(player)) {
                bossEvent.removePlayer(player);
            }
        }
        for (ServerPlayer player : eligiblePlayers) {
            if (!bossEvent.getPlayers().contains(player)) {
                bossEvent.addPlayer(player);
            }
        }
    }

    private void removeBossBarPlayers() {
        bossEvent.removeAllPlayers();
    }

    private static Component bossName() {
        return Component.translatable("entity.guardian_mod.boss_overworld");
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>("Idle", state -> state.setAndContinue(IDLE_ANIMATION)));
        controllers.add(new AnimationController<>(SPAWN_CONTROLLER_NAME, state -> state.renderState().getAnimatableAge() <= SPAWN_ANIMATION_TICKS
                ? state.setAndContinue(SPAWN_ANIMATION)
                : PlayState.STOP)
                .triggerableAnim(SPAWN_TRIGGER_NAME, SPAWN_ANIMATION));
        controllers.add(new AnimationController<OverworldGuardianEntity>(GuardianBossAi.ATTACK_CONTROLLER, 0, state -> PlayState.STOP)
                .triggerableAnim("attack", RawAnimation.begin().thenPlay("attack"))
                .triggerableAnim("melee", RawAnimation.begin().thenPlay("melee"))
                .triggerableAnim("rockfall", RawAnimation.begin().thenPlay("rockfall"))
                .triggerableAnim("shockwave", RawAnimation.begin().thenPlay("shockwave"))
                .triggerableAnim("fissure", RawAnimation.begin().thenPlay("fissure"))
                .triggerableAnim(PHASE_SHIFT_TRIGGER, RawAnimation.begin().thenPlay(PHASE_SHIFT_TRIGGER)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
