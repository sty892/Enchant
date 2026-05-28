package me.guardian.entity;

import me.guardian.GuardianMod;
import me.guardian.block.ModBlocks;
import me.guardian.config.BossArenaConfig;
import me.guardian.event.GuardianBossEventHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class OverworldGuardianEntity extends Monster implements GeoEntity {
    private static final String BOSS_CONFIG_KEY = "overworld";
    private static final String SPAWN_TRIGGER_NAME = "spawn";
    private static final String DEATH_TRIGGER_NAME = "death";
    private static final String PHASE_SHIFT_TRIGGER = "phase_shift";
    private static final String BOSS_BAR_ID_KEY = "BossBarId";
    private static final double PREFERRED_HOME_RADIUS = 16.0D;
    private static final double SOFT_RETURN_RADIUS = 19.0D;
    private static final double SOFT_RETURN_RADIUS_SQR = SOFT_RETURN_RADIUS * SOFT_RETURN_RADIUS;
    private static final double BOSS_BAR_RADIUS_SQR = 30.0D * 30.0D;
    private static final RawAnimation SPAWN_ANIMATION = RawAnimation.begin().thenPlay("spawn");
    private static final RawAnimation DEATH_ANIMATION = RawAnimation.begin().thenPlay("death");
    private static final RawAnimation IDLE_ANIMATION = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK_ANIMATION = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation RUN_ANIMATION = RawAnimation.begin().thenLoop("run");
    private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(
            OverworldGuardianEntity.class,
            EntityDataSerializers.INT
    );
    private static final EntityDataAccessor<String> DATA_ACTION_ANIMATION = SynchedEntityData.defineId(
            OverworldGuardianEntity.class,
            EntityDataSerializers.STRING
    );
    private static final EntityDataAccessor<Boolean> DATA_FULL_BODY_ACTION = SynchedEntityData.defineId(
            OverworldGuardianEntity.class,
            EntityDataSerializers.BOOLEAN
    );
    private static final EntityDataAccessor<Integer> DATA_ACTION_TICKS = SynchedEntityData.defineId(
            OverworldGuardianEntity.class,
            EntityDataSerializers.INT
    );
    private static final EntityDataAccessor<Boolean> DATA_AGGROED = SynchedEntityData.defineId(
            OverworldGuardianEntity.class,
            EntityDataSerializers.BOOLEAN
    );

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Map<UUID, Float> damageContributors = new HashMap<>();
    private final OverworldGuardianThreatTable threatTable = new OverworldGuardianThreatTable();
    private final OverworldGuardianAttackController attackController = new OverworldGuardianAttackController(this);
    private GuardianServerBossEvent bossEvent = createBossEvent(UUID.randomUUID());
    private OverworldGuardianPhase appliedPhase = OverworldGuardianPhase.ONE;
    private boolean spawnEventTriggered = false;
    private boolean deathEventTriggered = false;
    private boolean spawnAnimationTriggered = false;
    private BlockPos spawnCenter = null;
    private int unansweredHits = 0;
    private UUID counterTargetId = null;
    // Attack 9 — statues (dormant blocks)
    private final List<BlockPos> activeStatueBlocks = new ArrayList<>();
    // Attack 9 — statues (revived zombie entities)
    private final List<UUID> activeStatues = new ArrayList<>();
    // Attack 14 — shield
    private UUID activeShieldUUID = null;
    // Attack 15 — gates (block positions of the closed gate wall)
    private final List<BlockPos> gatePositions = new ArrayList<>();
    private boolean gatesClosed = false;
    private long bossLostTick = -1L;  // tick when all players left arena
    private long gateCooldownUntilTick = -1L;
    // Saved health for restore on wipe (as health fraction 0-1)
    private float savedHealthFraction = 1.0F;
    private boolean aiDisabled = false;
    // track whether gates have been triggered for phase 3.3
    private boolean gates33Triggered = false;

    public boolean isAiDisabled() {
        return this.aiDisabled;
    }

    public void setAiDisabled(boolean aiDisabled) {
        this.aiDisabled = aiDisabled;
    }

    public OverworldGuardianEntity(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 500.0)
                .add(Attributes.ATTACK_DAMAGE, 15.0)
                .add(Attributes.MOVEMENT_SPEED, 0.20)
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

    public int getHiddenStageStep() {
        float healthRatio = Math.max(0.0F, Math.min(1.0F, this.getHealth() / this.getMaxHealth()));
        double upperBound;
        double lowerBound;
        switch (getBossPhase()) {
            case ONE -> {
                upperBound = 1.0D;
                lowerBound = 0.66D;
            }
            case TWO -> {
                upperBound = 0.66D;
                lowerBound = 0.33D;
            }
            case THREE -> {
                upperBound = 0.33D;
                lowerBound = 0.0D;
            }
            default -> {
                upperBound = 1.0D;
                lowerBound = 0.66D;
            }
        }
        double progress = (upperBound - healthRatio) / (upperBound - lowerBound);
        if (progress >= 2.0D / 3.0D) {
            return 3;
        }
        if (progress >= 1.0D / 3.0D) {
            return 2;
        }
        return 1;
    }

    public void triggerAttackAnimation(String triggerName) {
        if (!level().isClientSide()) {
            setActionAnimation(triggerName, isFullBodyAction(triggerName), actionDurationTicks(triggerName));
        }
        String visualTriggerName = visualAttackTriggerName(triggerName);
        if (!visualTriggerName.isEmpty()) {
            GuardianBossAi.triggerAttackAnimation(this, visualTriggerName);
        }
    }

    public boolean forceAttack(ServerLevel level, String attackId) {
        return attackController.forceAttack(level, attackId);
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
        builder.define(DATA_ACTION_ANIMATION, "");
        builder.define(DATA_FULL_BODY_ACTION, false);
        builder.define(DATA_ACTION_TICKS, 0);
        builder.define(DATA_AGGROED, false);
    }

    public boolean isAggroed() {
        return getTarget() != null;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 16.0F) {
            @Override
            public boolean canUse() {
                return !isAggroed() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return !isAggroed() && super.canContinueToUse();
            }
        });
        this.goalSelector.addGoal(3, new RandomStrollGoal(this, 0.8) {
            @Override
            public boolean canUse() {
                return !isAggroed() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return !isAggroed() && super.canContinueToUse();
            }
        });
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new GuardianBossNavigation(this, level);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        // Attack 14: If shield is active, redirect damage to the shield entity
        if (hasActiveShield(level)) {
            HealingShieldEntity shield = getActiveShieldEntity(level);
            if (shield != null) {
                shield.hurtServer(level, source, amount);
            }
            return false;
        }
        // Attack 9: If statues are alive, apply 90% damage resistance
        if (hasActiveStatues(level)) {
            amount *= 0.10F;
        }
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

    /** Returns the active healing shield entity, or null if there is none. */
    public HealingShieldEntity getActiveShieldEntity(ServerLevel level) {
        if (activeShieldUUID == null) return null;
        Entity e = level.getEntity(activeShieldUUID);
        if (e instanceof HealingShieldEntity shield && shield.isAliveShield()) {
            return shield;
        }
        activeShieldUUID = null;
        return null;
    }

    /**
     * Returns true if at least one dormant statue block OR one revived statue mob is still alive.
     * While this returns true the boss has 90% damage resistance.
     */
    public boolean hasActiveStatues(ServerLevel level) {
        // Dormant blocks still in the world count as active statues
        if (!activeStatueBlocks.isEmpty()) {
            activeStatueBlocks.removeIf(pos -> !level.getBlockState(pos).is(ModBlocks.TEMPLE_STATUE));
            if (!activeStatueBlocks.isEmpty()) return true;
        }
        // Living zombie mobs
        activeStatues.removeIf(uuid -> {
            net.minecraft.world.entity.Entity e = level.getEntity(uuid);
            if (!(e instanceof TempleStatueEntity)) return true;
            return !((TempleStatueEntity) e).isAlive();
        });
        return !activeStatues.isEmpty();
    }

    /** Called by statue when it dies. */
    public void onStatueDied(UUID statueUUID) {
        activeStatues.remove(statueUUID);
    }

    /** Registers newly spawned zombie-statues (revived form). */
    public void addStatue(UUID uuid) {
        activeStatues.add(uuid);
    }

    /**
     * Registers dormant statue blocks placed in the world.
     * Clears any previous statue blocks (removing them from the world first).
     */
    public void setStatueBlocks(ServerLevel level, List<BlockPos> positions) {
        // Remove old blocks if any are still in the world
        for (BlockPos old : activeStatueBlocks) {
            if (level.getBlockState(old).is(ModBlocks.TEMPLE_STATUE)) {
                level.setBlock(old, Blocks.AIR.defaultBlockState(), 3);
            }
        }
        activeStatueBlocks.clear();
        activeStatueBlocks.addAll(positions);
    }

    /** Remove all dormant statue blocks from the world (called on wipe/cleanup). */
    public void clearStatueBlocks(ServerLevel level) {
        for (BlockPos pos : activeStatueBlocks) {
            if (level.getBlockState(pos).is(ModBlocks.TEMPLE_STATUE)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
        }
        activeStatueBlocks.clear();
    }

    /** Returns true if healing shield is still alive. */
    public boolean hasActiveShield(ServerLevel level) {
        if (activeShieldUUID == null) return false;
        net.minecraft.world.entity.Entity e = level.getEntity(activeShieldUUID);
        if (!(e instanceof HealingShieldEntity)) {
            activeShieldUUID = null;
            return false;
        }
        HealingShieldEntity shield = (HealingShieldEntity) e;
        if (!shield.isAliveShield()) {
            activeShieldUUID = null;
            return false;
        }
        return true;
    }

    /** Called when the healing shield is destroyed or expires. */
    public void onShieldDestroyed() {
        activeShieldUUID = null;
    }

    /** Spawns a healing shield and stores its UUID. Returns false if one already exists. */
    public boolean spawnHealingShield(ServerLevel level) {
        if (hasActiveShield(level)) return false;
        HealingShieldEntity shield = new HealingShieldEntity(level, this);
        level.addFreshEntity(shield);
        activeShieldUUID = shield.getUUID();
        return true;
    }

    /**
     * Closes the temple gates by placing {@link ModBlocks#TEMPLE_GATE} blocks in 4 walls
     * around the spawn center (radius 18). Each wall is 5 blocks wide and 4 blocks tall.
     * Gate is triggered when boss reaches phase 3, hidden step 3 (HP < ~11%).
     */
    public void closeGates(ServerLevel level) {
        if (gatesClosed || spawnCenter == null) return;
        gatesClosed = true;
        savedHealthFraction = this.getHealth() / this.getMaxHealth();
        gatePositions.clear();
        int cx = spawnCenter.getX();
        int cy = spawnCenter.getY();
        int cz = spawnCenter.getZ();
        int radius = 18;
        int wallWidth = 2;  // half-width = 2 → total 5 blocks wide
        int wallHeight = 4;
        // +X wall: spans Z axis
        for (int dz = -wallWidth; dz <= wallWidth; dz++) {
            for (int dy = 0; dy < wallHeight; dy++) {
                BlockPos pos = new BlockPos(cx + radius, cy + dy, cz + dz);
                boolean isCenter = (dz == 0 && dy == 0);
                net.minecraft.world.level.block.state.BlockState state = ModBlocks.TEMPLE_GATE.defaultBlockState()
                        .setValue(me.guardian.block.TempleGateBlock.CENTER, isCenter);
                level.setBlock(pos, state, 3);
                gatePositions.add(pos);
            }
        }
        // -X wall: spans Z axis
        for (int dz = -wallWidth; dz <= wallWidth; dz++) {
            for (int dy = 0; dy < wallHeight; dy++) {
                BlockPos pos = new BlockPos(cx - radius, cy + dy, cz + dz);
                boolean isCenter = (dz == 0 && dy == 0);
                net.minecraft.world.level.block.state.BlockState state = ModBlocks.TEMPLE_GATE.defaultBlockState()
                        .setValue(me.guardian.block.TempleGateBlock.CENTER, isCenter);
                level.setBlock(pos, state, 3);
                gatePositions.add(pos);
            }
        }
        // +Z wall: spans X axis
        for (int dx = -wallWidth; dx <= wallWidth; dx++) {
            for (int dy = 0; dy < wallHeight; dy++) {
                BlockPos pos = new BlockPos(cx + dx, cy + dy, cz + radius);
                boolean isCenter = (dx == 0 && dy == 0);
                net.minecraft.world.level.block.state.BlockState state = ModBlocks.TEMPLE_GATE.defaultBlockState()
                        .setValue(me.guardian.block.TempleGateBlock.CENTER, isCenter);
                level.setBlock(pos, state, 3);
                gatePositions.add(pos);
            }
        }
        // -Z wall: spans X axis
        for (int dx = -wallWidth; dx <= wallWidth; dx++) {
            for (int dy = 0; dy < wallHeight; dy++) {
                BlockPos pos = new BlockPos(cx + dx, cy + dy, cz - radius);
                boolean isCenter = (dx == 0 && dy == 0);
                net.minecraft.world.level.block.state.BlockState state = ModBlocks.TEMPLE_GATE.defaultBlockState()
                        .setValue(me.guardian.block.TempleGateBlock.CENTER, isCenter);
                level.setBlock(pos, state, 3);
                gatePositions.add(pos);
            }
        }
        GuardianMod.LOGGER.info("Overworld Guardian: closed {} gate blocks", gatePositions.size());
    }

    /** Opens all gate blocks (sets them to air) and clears the position list. */
    public void openGates(ServerLevel level) {
        if (!gatesClosed) return;
        gatesClosed = false;
        gates33Triggered = false;
        for (BlockPos pos : gatePositions) {
            if (level.getBlockState(pos).is(ModBlocks.TEMPLE_GATE)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
        }
        gatePositions.clear();
        GuardianMod.LOGGER.info("Overworld Guardian: opened gates");
    }

    public boolean isGateCooldownActive() {
        return gateCooldownUntilTick > 0 && this.tickCount < gateCooldownUntilTick;
    }

    public void setGateCooldown() {
        gateCooldownUntilTick = this.tickCount + BossArenaConfig.getGateWipeReopenTicks();
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (isAiDisabled()) {
            tickAnimationState();
            tickPhase(level);
            attackController.tick(level);
            tickBossBar(level);
            return;
        }
        GuardianBossAi.ensureSpawnHome(this);
        checkFightStart(level);
        triggerSpawnAnimation();
        threatTable.tick(this, level);
        tickTarget(level);
        tickAnimationState();
        tickPhase(level);
        tickSoftHomeReturn();
        attackController.tick(level);
        tickBossBar(level);
        tickGateWipe(level);
    }

    @Override
    public float getWalkTargetValue(BlockPos pos, LevelReader level) {
        return GuardianBossAi.applySpawnDistancePenalty(this, pos, super.getWalkTargetValue(pos, level));
    }

    @Override
    public void die(DamageSource damageSource) {
        if (!level().isClientSide()) {
            setActionAnimation(DEATH_TRIGGER_NAME, true, actionDurationTicks(DEATH_TRIGGER_NAME));
        }
        if (!deathEventTriggered && this.level() instanceof ServerLevel serverLevel) {
            deathEventTriggered = true;
            openGates(serverLevel);
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
        output.putBoolean("GatesClosed", gatesClosed);
        output.putBoolean("Gates33Triggered", gates33Triggered);
        output.putInt("GateCooldownUntilTick", (int) gateCooldownUntilTick);
        output.putFloat("SavedHealthFraction", savedHealthFraction);
        output.putBoolean("AiDisabled", aiDisabled);
        // Save gate block positions
        StringBuilder gbSb = new StringBuilder();
        for (int i = 0; i < gatePositions.size(); i++) {
            BlockPos p = gatePositions.get(i);
            gbSb.append(p.getX()).append(':').append(p.getY()).append(':').append(p.getZ());
            if (i < gatePositions.size() - 1) gbSb.append(',');
        }
        output.putString("GatePositions", gbSb.toString());
        // Save dormant statue block positions
        StringBuilder sbSb = new StringBuilder();
        for (int i = 0; i < activeStatueBlocks.size(); i++) {
            BlockPos p = activeStatueBlocks.get(i);
            sbSb.append(p.getX()).append(':').append(p.getY()).append(':').append(p.getZ());
            if (i < activeStatueBlocks.size() - 1) sbSb.append(',');
        }
        output.putString("StatueBlocks", sbSb.toString());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        setBossPhase(OverworldGuardianPhase.byId(input.getIntOr("BossPhase", 1)));
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
        this.gatesClosed = input.getBooleanOr("GatesClosed", false);
        this.gates33Triggered = input.getBooleanOr("Gates33Triggered", false);
        this.gateCooldownUntilTick = input.getIntOr("GateCooldownUntilTick", -1);
        this.savedHealthFraction = input.getFloatOr("SavedHealthFraction", 1.0F);
        this.aiDisabled = input.getBooleanOr("AiDisabled", false);
        gatePositions.clear();
        input.getString("GatePositions").ifPresent(s -> {
            if (!s.isEmpty()) {
                for (String part : s.split(",")) {
                    try {
                        String[] xyz = part.trim().split(":");
                        if (xyz.length == 3) {
                            gatePositions.add(new BlockPos(
                                    Integer.parseInt(xyz[0]),
                                    Integer.parseInt(xyz[1]),
                                    Integer.parseInt(xyz[2])));
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
        });
        activeStatueBlocks.clear();
        input.getString("StatueBlocks").ifPresent(s -> {
            if (!s.isEmpty()) {
                for (String part : s.split(",")) {
                    try {
                        String[] xyz = part.trim().split(":");
                        if (xyz.length == 3) {
                            activeStatueBlocks.add(new BlockPos(
                                    Integer.parseInt(xyz[0]),
                                    Integer.parseInt(xyz[1]),
                                    Integer.parseInt(xyz[2])));
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
        });
    }

    private void checkFightStart(ServerLevel level) {
        if (gatesClosed || spawnEventTriggered) {
            return;
        }
        if (isGateCooldownActive()) {
            return;
        }
        BlockPos center = spawnCenter == null ? this.blockPosition() : spawnCenter;
        double radius = 18.0D;
        boolean playerNear = false;
        for (ServerPlayer player : level.players()) {
            if (player.isAlive() && player.level() == this.level()
                    && player.distanceToSqr(Vec3.atCenterOf(center)) <= radius * radius) {
                playerNear = true;
                break;
            }
        }
        if (playerNear || getTarget() != null) {
            triggerSpawnEvent(level);
        }
    }

    public float getSubStageStartHealthFraction() {
        float healthRatio = this.getHealth() / this.getMaxHealth();
        double upperBound = 1.0D;
        double lowerBound = 0.66D;
        switch (getBossPhase()) {
            case TWO -> {
                upperBound = 0.66D;
                lowerBound = 0.33D;
            }
            case THREE -> {
                upperBound = 0.33D;
                lowerBound = 0.0D;
            }
        }
        double progress = (upperBound - healthRatio) / (upperBound - lowerBound);
        if (progress >= 2.0D / 3.0D) {
            return (float) (upperBound - (2.0D / 3.0D) * (upperBound - lowerBound));
        } else if (progress >= 1.0D / 3.0D) {
            return (float) (upperBound - (1.0D / 3.0D) * (upperBound - lowerBound));
        } else {
            return (float) upperBound;
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
        // Gates now close at phase 3.3, not at fight start
    }

    private void triggerSpawnAnimation() {
        if (spawnAnimationTriggered || this.tickCount < 2) {
            return;
        }
        spawnAnimationTriggered = true;
        setActionAnimation(SPAWN_TRIGGER_NAME, true, actionDurationTicks(SPAWN_TRIGGER_NAME));
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

    public void syncBossBarAfterRespawn(ServerPlayer oldPlayer, ServerPlayer newPlayer) {
        boolean trackedOldPlayer = bossEvent.getPlayers().contains(oldPlayer);
        if (trackedOldPlayer) {
            bossEvent.removePlayer(oldPlayer);
        }
        if (newPlayer.level() == this.level() && isBossBarEligible(newPlayer)) {
            bossEvent.addPlayer(newPlayer);
        }
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
        Entity entity = level.getEntity(counterTargetId);
        if (entity instanceof LivingEntity living && living.isAlive()) {
            if (consume) {
                unansweredHits = 0;
            }
            return living;
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

    private void tickAnimationState() {
        LivingEntity target = this.getTarget();
        this.entityData.set(DATA_AGGROED, target != null && target.isAlive());

        int actionTicks = this.entityData.get(DATA_ACTION_TICKS);
        if (actionTicks <= 0) {
            clearActionAnimation();
            return;
        }
        this.entityData.set(DATA_ACTION_TICKS, actionTicks - 1);
    }

    private void tickPhase(ServerLevel level) {
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
        // Close gates when boss reaches phase 3, hidden step 3 (HP < ~11% of max)
        if (!gates33Triggered && !gatesClosed
                && nextPhase == OverworldGuardianPhase.THREE
                && getHiddenStageStep() == 3) {
            gates33Triggered = true;
            closeGates(level);
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

    /**
     * Attack 15 wipe detection: if gates are closed and no players are alive within
     * the boss arena for 10 seconds, treat the fight as a wipe.
     * The boss restores health to the saved fraction, gates open, and a 1h cooldown is set.
     */
    private void tickGateWipe(ServerLevel level) {
        if (!gatesClosed || spawnCenter == null) {
            bossLostTick = -1L;
            return;
        }
        double arenaRadius = 22.0D;
        boolean anyPlayerInside = false;
        for (ServerPlayer player : level.players()) {
            if (player.isAlive()
                    && player.level() == this.level()
                    && player.distanceToSqr(Vec3.atCenterOf(spawnCenter)) <= arenaRadius * arenaRadius) {
                anyPlayerInside = true;
                break;
            }
        }
        if (anyPlayerInside) {
            bossLostTick = -1L;
            if (this.tickCount % 100 == 0) {
                savedHealthFraction = this.getHealth() / this.getMaxHealth();
            }
            return;
        }
        // No players in arena
        if (bossLostTick < 0) {
            bossLostTick = this.tickCount;
            return;
        }
        // Wait 10 seconds (200 ticks) before declaring wipe
        if (this.tickCount - bossLostTick < 200) {
            return;
        }
        // WIPE: restore boss health to start of current sub-stage, open gates, set cooldown
        bossLostTick = -1L;
        float restoreFraction = getSubStageStartHealthFraction();
        float restoreHealth = Math.max(1.0F, restoreFraction * this.getMaxHealth());
        this.setHealth(restoreHealth);
        openGates(level);
        setGateCooldown();
        this.gatesClosed = false;
        this.gates33Triggered = false;
        this.spawnEventTriggered = false;
        this.setTarget(null);
        this.threatTable.clear();
        // Remove dormant statue blocks
        clearStatueBlocks(level);
        // Discard revived statue mobs
        for (UUID uuid : new ArrayList<>(activeStatues)) {
            Entity e = level.getEntity(uuid);
            if (e != null) {
                e.discard();
            }
        }
        activeStatues.clear();
        if (activeShieldUUID != null) {
            Entity e = level.getEntity(activeShieldUUID);
            if (e != null) {
                e.discard();
            }
            activeShieldUUID = null;
        }
    }

    private boolean isBossBarEligible(ServerPlayer player) {
        return player.isAlive()
                && !player.isRemoved()
                && player.level() == this.level()
                && player.distanceToSqr(this) <= BOSS_BAR_RADIUS_SQR;
    }

    private static GuardianServerBossEvent createBossEvent(UUID id) {
        return new GuardianServerBossEvent(
                id,
                bossName(),
                BossEvent.BossBarColor.GREEN,
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
        return Component.translatable("entity.guardian_mod.boss_overworld");
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>("Movement", 10, state -> {
            if (isFullBodyActionActive() || isSpawning() || isDeadOrDyingForAnimation()) {
                return PlayState.STOP;
            }
            if (!isMovingForAnimation()) {
                return state.setAndContinue(IDLE_ANIMATION);
            }
            return state.setAndContinue(isAggroedForAnimation() ? RUN_ANIMATION : WALK_ANIMATION);
        }));
        controllers.add(new AnimationController<OverworldGuardianEntity>(GuardianBossAi.ATTACK_CONTROLLER, 2, state -> PlayState.STOP)
                .triggerableAnim("attack", RawAnimation.begin().thenPlay("attack"))
                .triggerableAnim("attack_right", RawAnimation.begin().thenPlay("attack_right"))
                .triggerableAnim("attack_left", RawAnimation.begin().thenPlay("attack_left"))
                .triggerableAnim("attack_both", RawAnimation.begin().thenPlay("attack_both"))
                .triggerableAnim("attack_hands_slam", RawAnimation.begin().thenPlay("attack_hands_slam"))
                .triggerableAnim("stamTopTopTop", RawAnimation.begin().thenPlay("StamTopTopTop")));
    }

    public boolean isMovingForAnimation() {
        double dx = this.getX() - this.xo;
        double dz = this.getZ() - this.zo;
        return (dx * dx + dz * dz) >= 0.001D;
    }

    public boolean isAggroedForAnimation() {
        return this.entityData.get(DATA_AGGROED);
    }

    public boolean isAttacking() {
        String animation = getCurrentActionAnimation();
        return !animation.isEmpty() && !isSpawning() && !isDeadOrDyingForAnimation();
    }

    public boolean isStomping() {
        String animation = getCurrentActionAnimation();
        return "attack_hands_slam".equals(animation) || "stamTopTopTop".equals(animation);
    }

    public boolean isSpawning() {
        return SPAWN_TRIGGER_NAME.equals(getCurrentActionAnimation()) && this.entityData.get(DATA_ACTION_TICKS) > 0;
    }

    public boolean isDeadOrDyingForAnimation() {
        return DEATH_TRIGGER_NAME.equals(getCurrentActionAnimation()) || this.isDeadOrDying();
    }

    public String getCurrentAttackAnimation() {
        return isAttacking() ? getCurrentActionAnimation() : "";
    }

    public String getCurrentActionAnimation() {
        return this.entityData.get(DATA_ACTION_ANIMATION);
    }

    private boolean isFullBodyActionActive() {
        return this.entityData.get(DATA_FULL_BODY_ACTION) && this.entityData.get(DATA_ACTION_TICKS) > 0;
    }

    private void setActionAnimation(String animation, boolean fullBody, int ticks) {
        this.entityData.set(DATA_ACTION_ANIMATION, animation);
        this.entityData.set(DATA_FULL_BODY_ACTION, fullBody);
        this.entityData.set(DATA_ACTION_TICKS, Math.max(1, ticks));
    }

    private void clearActionAnimation() {
        if (getCurrentActionAnimation().isEmpty() && !this.entityData.get(DATA_FULL_BODY_ACTION)) {
            return;
        }
        this.entityData.set(DATA_ACTION_ANIMATION, "");
        this.entityData.set(DATA_FULL_BODY_ACTION, false);
        this.entityData.set(DATA_ACTION_TICKS, 0);
    }

    private static boolean isFullBodyAction(String triggerName) {
        return switch (triggerName) {
            case "attack", "attack_right", "attack_left", "attack_both", "attack_hands_slam",
                 "stamTopTopTop", PHASE_SHIFT_TRIGGER, SPAWN_TRIGGER_NAME, DEATH_TRIGGER_NAME -> true;
            default -> false;
        };
    }

    private static int actionDurationTicks(String triggerName) {
        return switch (triggerName) {
            case "attack_hands_slam", "stamTopTopTop" -> 56;
            case "attack_both", PHASE_SHIFT_TRIGGER -> 44;
            case SPAWN_TRIGGER_NAME -> 40;
            case DEATH_TRIGGER_NAME -> 80;
            default -> 34;
        };
    }

    private static String visualAttackTriggerName(String triggerName) {
        return switch (triggerName) {
            case SPAWN_TRIGGER_NAME, DEATH_TRIGGER_NAME -> "";
            case PHASE_SHIFT_TRIGGER -> "attack_both";
            default -> triggerName;
        };
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
