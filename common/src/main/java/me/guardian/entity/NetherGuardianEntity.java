package me.guardian.entity;

import me.guardian.event.GuardianBossEventHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.BossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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

public class NetherGuardianEntity extends Monster implements GeoEntity {
    private static final String BOSS_CONFIG_KEY = "nether";
    private static final String SPAWN_CONTROLLER_NAME = "Spawn";
    private static final String SPAWN_TRIGGER_NAME = "spawn";
    private static final String PHASE_SHIFT_TRIGGER = "phase_shift";
    private static final String BOSS_BAR_ID_KEY = "BossBarId";
    private static final double PREFERRED_HOME_RADIUS = 15.0D;
    private static final double SOFT_RETURN_RADIUS = 20.0D;
    private static final double SOFT_RETURN_RADIUS_SQR = SOFT_RETURN_RADIUS * SOFT_RETURN_RADIUS;
    private static final double BOSS_BAR_RADIUS_SQR = 36.0D * 36.0D;
    private static final RawAnimation SPAWN_ANIMATION = RawAnimation.begin().thenPlay(SPAWN_TRIGGER_NAME);
    private static final RawAnimation IDLE_ANIMATION = RawAnimation.begin().thenLoop("idle");
    private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(
            NetherGuardianEntity.class,
            EntityDataSerializers.INT
    );

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Map<UUID, Float> damageContributors = new HashMap<>();
    private final NetherGuardianThreatTable threatTable = new NetherGuardianThreatTable();
    private final NetherGuardianAttackController attackController = new NetherGuardianAttackController(this);
    private GuardianServerBossEvent bossEvent = createBossEvent(UUID.randomUUID());
    private NetherGuardianPhase appliedPhase = NetherGuardianPhase.ONE;
    private boolean spawnEventTriggered = false;
    private boolean deathEventTriggered = false;
    private boolean spawnAnimationTriggered = false;
    private BlockPos spawnCenter = null;
    private int vulnerabilityTicks;
    private boolean aiDisabled = false;

    public boolean isAiDisabled() {
        return this.aiDisabled;
    }

    public void setAiDisabled(boolean aiDisabled) {
        this.aiDisabled = aiDisabled;
    }

    public NetherGuardianEntity(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 750.0)
                .add(Attributes.ATTACK_DAMAGE, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.24)
                .add(Attributes.FOLLOW_RANGE, 80.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    public Map<UUID, Float> getDamageContributors() {
        return damageContributors;
    }

    public NetherGuardianThreatTable getThreatTable() {
        return threatTable;
    }

    public NetherGuardianPhase getBossPhase() {
        return NetherGuardianPhase.byId(this.entityData.get(DATA_PHASE));
    }

    public void triggerAttackAnimation(String triggerName) {
        GuardianBossAi.triggerAttackAnimation(this, triggerName);
    }

    public boolean forceAttack(ServerLevel level, String attackId) {
        return attackController.forceAttack(level, attackId);
    }

    public boolean isVulnerable() {
        return vulnerabilityTicks > 0;
    }

    public void startVulnerabilityWindow(int ticks) {
        vulnerabilityTicks = Math.max(vulnerabilityTicks, ticks);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_PHASE, NetherGuardianPhase.ONE.id());
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 20.0F));
        this.goalSelector.addGoal(3, new RandomStrollGoal(this, 0.8));
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new GuardianBossNavigation(this, level);
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        float modifiedAmount = attackController.modifyIncomingDamage(level, amount);
        boolean damaged = super.hurtServer(level, source, modifiedAmount);
        if (damaged && source.getEntity() instanceof ServerPlayer player) {
            damageContributors.merge(player.getUUID(), modifiedAmount, Float::sum);
            threatTable.recordDamage(player, modifiedAmount, this.tickCount);
        }
        return damaged;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (isAiDisabled()) {
            tickPhase();
            tickVulnerabilityWindow(level);
            attackController.tick(level);
            tickBossBar(level);
            return;
        }
        GuardianBossAi.ensureSpawnHome(this);
        triggerSpawnEvent(level);
        triggerSpawnAnimation();
        threatTable.tick(this, level);
        tickTarget(level);
        tickPhase();
        tickSoftHomeReturn();
        tickVulnerabilityWindow(level);
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
        output.putString(BOSS_BAR_ID_KEY, bossEvent.getId().toString());
        output.putBoolean("SpawnEventTriggered", spawnEventTriggered);
        output.putBoolean("SpawnAnimationTriggered", spawnAnimationTriggered);
        if (spawnCenter != null) {
            output.putInt("SpawnCenterX", spawnCenter.getX());
            output.putInt("SpawnCenterY", spawnCenter.getY());
            output.putInt("SpawnCenterZ", spawnCenter.getZ());
        }
        output.putBoolean("AiDisabled", aiDisabled);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        setBossPhase(NetherGuardianPhase.byId(input.getIntOr("BossPhase", 1)));
        this.bossEvent = createBossEvent(readBossBarId(input));
        this.spawnEventTriggered = input.getBooleanOr("SpawnEventTriggered", false);
        this.spawnAnimationTriggered = input.getBooleanOr("SpawnAnimationTriggered", spawnEventTriggered);
        if (input.getInt("SpawnCenterX").isPresent()
                && input.getInt("SpawnCenterY").isPresent()
                && input.getInt("SpawnCenterZ").isPresent()) {
            this.spawnCenter = new BlockPos(
                    input.getIntOr("SpawnCenterX", 0),
                    input.getIntOr("SpawnCenterY", 0),
                    input.getIntOr("SpawnCenterZ", 0)
            );
        }
        this.aiDisabled = input.getBooleanOr("AiDisabled", false);
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

    private void tickTarget(ServerLevel level) {
        LivingEntity nextTarget = threatTable.chooseTarget(this, level);
        if (nextTarget != this.getTarget()) {
            this.setTarget(nextTarget);
        }
    }

    private void tickPhase() {
        NetherGuardianPhase nextPhase = NetherGuardianPhase.fromHealth(this.getHealth() / this.getMaxHealth());
        NetherGuardianPhase currentPhase = getBossPhase();
        if (nextPhase != currentPhase) {
            setBossPhase(nextPhase);
            if (nextPhase.id() > currentPhase.id()) {
                triggerAttackAnimation(PHASE_SHIFT_TRIGGER);
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
        double strength = Math.min(0.1D, 0.016D + excess * 0.008D);
        this.addDeltaMovement(inward.scale(strength));
    }

    private void tickVulnerabilityWindow(ServerLevel level) {
        if (vulnerabilityTicks <= 0) {
            return;
        }
        vulnerabilityTicks--;
        this.getNavigation().stop();
        if (this.tickCount % 4 == 0) {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL,
                    this.getX(), this.getY() + 2.0D, this.getZ(),
                    8, 1.0D, 1.2D, 1.0D, 0.02D);
        }
    }

    private void tickBossBar(ServerLevel level) {
        bossEvent.setProgress(Math.max(0.0F, this.getHealth() / this.getMaxHealth()));
        Set<ServerPlayer> eligiblePlayers = new HashSet<>();
        for (ServerPlayer player : level.players()) {
            if (isBossBarEligible(player)) {
                eligiblePlayers.add(player);
            }
        }
        for (ServerPlayer player : Set.copyOf(bossEvent.getPlayers())) {
            ServerPlayer currentPlayer = level.getServer().getPlayerList().getPlayer(player.getUUID());
            if (currentPlayer != player || !eligiblePlayers.contains(player)) {
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

    private boolean isBossBarEligible(ServerPlayer player) {
        return player.isAlive()
                && !player.isRemoved()
                && player.level() == this.level()
                && player.distanceToSqr(this) <= BOSS_BAR_RADIUS_SQR;
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

    private void setBossPhase(NetherGuardianPhase phase) {
        this.entityData.set(DATA_PHASE, phase.id());
    }

    private void setBaseAttribute(net.minecraft.core.Holder<Attribute> attribute, double value) {
        var instance = this.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    private static GuardianServerBossEvent createBossEvent(UUID id) {
        return new GuardianServerBossEvent(
                id,
                bossName(),
                BossEvent.BossBarColor.RED,
                BossEvent.BossBarOverlay.NOTCHED_6
        );
    }

    private static UUID readBossBarId(ValueInput input) {
        return input.getString(BOSS_BAR_ID_KEY)
                .flatMap(value -> {
                    try {
                        return java.util.Optional.of(UUID.fromString(value));
                    } catch (IllegalArgumentException ignored) {
                        return java.util.Optional.empty();
                    }
                })
                .orElseGet(UUID::randomUUID);
    }

    private static Component bossName() {
        return Component.translatable("entity.guardian_mod.boss_nether");
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>("Idle", state -> state.setAndContinue(IDLE_ANIMATION)));
        controllers.add(new AnimationController<NetherGuardianEntity>(SPAWN_CONTROLLER_NAME, state -> PlayState.STOP)
                .triggerableAnim(SPAWN_TRIGGER_NAME, SPAWN_ANIMATION));
        controllers.add(new AnimationController<NetherGuardianEntity>(GuardianBossAi.ATTACK_CONTROLLER, 0, state -> PlayState.STOP)
                .triggerableAnim(GuardianBossAi.ATTACK_TRIGGER, RawAnimation.begin().thenPlay(GuardianBossAi.ATTACK_TRIGGER))
                .triggerableAnim("melee", RawAnimation.begin().thenPlay("melee"))
                .triggerableAnim("molten_fissure", RawAnimation.begin().thenPlay("molten_fissure"))
                .triggerableAnim("meteor_rain", RawAnimation.begin().thenPlay("meteor_rain"))
                .triggerableAnim("whip_grab", RawAnimation.begin().thenPlay("whip_grab"))
                .triggerableAnim("minion_aegis", RawAnimation.begin().thenPlay("minion_aegis"))
                .triggerableAnim("soul_vortex", RawAnimation.begin().thenPlay("soul_vortex"))
                .triggerableAnim("death_beams", RawAnimation.begin().thenPlay("death_beams"))
                .triggerableAnim(PHASE_SHIFT_TRIGGER, RawAnimation.begin().thenPlay(PHASE_SHIFT_TRIGGER)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
