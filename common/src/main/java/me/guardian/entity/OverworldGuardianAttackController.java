package me.guardian.entity;

import me.guardian.block.ModBlocks;
import me.guardian.config.OverworldGuardianAttackConfig;
import me.guardian.config.OverworldGuardianAttackConfig.AttackTiming;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Display;
import com.mojang.math.Transformation;
import org.joml.Vector3f;
import org.joml.Quaternionf;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class OverworldGuardianAttackController {
    private static final double CHASE_RANGE = 5.0D;
    private static final double CHASE_RANGE_SQR = CHASE_RANGE * CHASE_RANGE;
    private static final BlockParticleOption DIRT_PARTICLE = new BlockParticleOption(
            ParticleTypes.DUST_PILLAR,
            Blocks.DIRT.defaultBlockState()
    );
    private static final DustParticleOptions TELEGRAPH_DUST = new DustParticleOptions(0xC9FFF3, 1.25F);

    private final OverworldGuardianEntity boss;
    private final Attack[] attacks;
    private RunningAttack runningAttack;
    private int globalDelay = 20;
    private final List<TemporaryDisplay> tempDisplays = new ArrayList<>();
    private final List<Shockwave> activeShockwaves = new ArrayList<>();
    private String pendingCombo = null;
    private boolean pendingFollowUpMelee = false;

    public OverworldGuardianAttackController(OverworldGuardianEntity boss) {
        this.boss = boss;
        this.attacks = new Attack[]{
                new ArmWaveAttack("right_hand_wave", "attack_right", InteractionHand.MAIN_HAND),
                new ArmWaveAttack("left_hand_wave", "attack_left", InteractionHand.OFF_HAND),
                new DoubleHandWaveAttack(),
                new HandsSlamLineAttack(),
                new StompPlayersAttack(),
                new BombTrapsAttack(),
                new StatueRevivalAttack(),
                new HealingShieldAttack(),
                new ChargeRamAttack(),
                new GroundVinesAttack(),
                new VinePullAttack(),
                new ArenaWallsAttack(),
                new LeapAttack()
        };
    }

    public void tick(ServerLevel level) {
        tempDisplays.removeIf(TemporaryDisplay::tick);
        activeShockwaves.removeIf(s -> s.tick(level));
        OverworldGuardianAttackConfig.tickAutoReload(level);
        for (Attack attack : attacks) {
            attack.tickCooldown();
        }

        if (boss.isChargeStunned()) {
            boss.getNavigation().stop();
            return;
        }

        if (runningAttack != null) {
            boss.getNavigation().stop();
            if (runningAttack.tick(level)) {
                boss.setNoAi(false);
                boss.noPhysics = false;
                runningAttack = null;
                notifyAttackEnded();
                // Phase 3 = faster combos (shorter post-attack delay)
                globalDelay = switch (boss.getBossPhase()) {
                    case THREE -> 16;
                    case TWO   -> 22;
                    default    -> 28;
                };
            }
            return;
        }

        if (boss.isAiDisabled()) {
            boss.getNavigation().stop();
            return;
        }

        if (pendingCombo != null) {
            String combo = pendingCombo;
            pendingCombo = null;
            if (forceAttack(level, combo)) {
                return;
            }
        }

        if (pendingFollowUpMelee) {
            pendingFollowUpMelee = false;
            LivingEntity nearTarget = nearestPlayer(level, 6.0D);
            if (nearTarget != null) {
                boss.setTarget(nearTarget);
                String side = boss.getRandom().nextBoolean() ? "right_hand_wave" : "left_hand_wave";
                if (forceAttack(level, side)) {
                    return;
                }
            }
        }

        LivingEntity target = activeTarget();
        if (target == null) {
            globalDelay = 10;
            return;
        }

        // ── Healing shield: boss just stands still and heals, no movement, no attacks ──
        boolean shieldActive = boss.hasActiveShield(level);
        if (shieldActive) {
            boss.getNavigation().stop();
            boss.getLookControl().setLookAt(target, 30.0F, 30.0F);
            return;
        }

        // ── Phase-aware movement ──────────────────────────────────────────────
        double idealMin = switch (boss.getBossPhase()) {
            case THREE -> 2.0D;
            case TWO   -> 3.0D;
            default    -> 2.5D;
        };
        double idealMax = switch (boss.getBossPhase()) {
            case THREE -> 5.0D;
            case TWO   -> 7.0D;
            default    -> 6.0D;
        };

        boolean chasing = false;
        double distSqr = boss.distanceToSqr(target);
        if (shieldActive) {
            boss.getNavigation().stop();
        } else if (boss.shouldReturnTowardHome()) {
            Vec3 home = boss.homeCenter();
            boss.getNavigation().moveTo(home.x, home.y, home.z, 1.05D);
        } else if (distSqr > idealMax * idealMax) {
            chasing = true;
            double speed = boss.getBossPhase() == OverworldGuardianPhase.THREE ? 1.25D : 1.22D;
            boss.getNavigation().moveTo(target, speed);
        } else if (distSqr < idealMin * idealMin && boss.getBossPhase() == OverworldGuardianPhase.TWO) {
            Vec3 away = boss.position().subtract(target.position()).horizontal();
            if (away.lengthSqr() > 0.001D) {
                boss.addDeltaMovement(away.normalize().scale(0.04D));
            }
            boss.getNavigation().stop();
        } else if (distSqr > idealMin * idealMin) {
            chasing = true;
            boss.getNavigation().moveTo(target, 1.15D);
        } else {
            boss.getNavigation().stop();
        }
        if (!chasing || shieldActive) {
            boss.getLookControl().setLookAt(target, 30.0F, 30.0F);
        }

        if (globalDelay > 0) {
            globalDelay--;
            return;
        }

        Attack selected = selectAttack(level);
        if (selected == null) {
            globalDelay = 10;
            return;
        }
        selected.startCooldown();
        notifyAttackStarted(level, selected.id());
        runningAttack = selected.start(level);
    }

    public boolean forceAttack(ServerLevel level, String attackId) {
        OverworldGuardianAttackConfig.tickAutoReload(level);
        if (runningAttack != null) {
            return false;
        }
        for (Attack attack : attacks) {
            if (!attack.id().equals(attackId)) {
                continue;
            }
            attack.startCooldown();
            notifyAttackStarted(level, attackId);
            runningAttack = attack.start(level);
            globalDelay = 0;
            return true;
        }
        return false;
    }

    private void notifyAttackStarted(ServerLevel level, String attackId) {
        boss.setCurrentAttackId(attackId);
        boss.broadcastAttackDebug(level, attackId);
    }

    private void notifyAttackEnded() {
        boss.setCurrentAttackId("");
    }

    private Attack selectAttack(ServerLevel level) {
        LivingEntity target = activeTarget();
        double distSqr = target != null ? boss.distanceToSqr(target) : Double.MAX_VALUE;
        boolean isClose   = distSqr < 5.0D * 5.0D;   // < 5 blocks
        boolean isFar     = distSqr > 12.0D * 12.0D;  // > 12 blocks
        boolean lowHp     = boss.getHealth() / boss.getMaxHealth() < 0.20F;
        boolean wantCounter = boss.peekCounterTarget(level) != null;

        int totalWeight = 0;
        for (Attack attack : attacks) {
            if (attack.canStart(level)) {
                totalWeight += situationalWeight(attack, isClose, isFar, lowHp, wantCounter);
            }
        }
        if (totalWeight <= 0) {
            return null;
        }

        int roll = boss.getRandom().nextInt(totalWeight);
        for (Attack attack : attacks) {
            if (!attack.canStart(level)) {
                continue;
            }
            roll -= situationalWeight(attack, isClose, isFar, lowHp, wantCounter);
            if (roll < 0) {
                return attack;
            }
        }
        return null;
    }

    /**
     * Returns the effective weight of an attack given current situation.
     * Boosts close-range attacks when target is near, boosts shield when HP is critical,
     * boosts counter-attack when boss was hit several times without retaliation.
     */
    private int situationalWeight(Attack attack, boolean isClose, boolean isFar, boolean lowHp, boolean wantCounter) {
        int w = attack.weight();
        String id = attack.id();
        // Low-HP emergency: prioritise healing shield × 4
        if (lowHp && id.equals("healing_shield")) return w * 4;
        // When target is close → prefer melee
        if (isClose && (id.equals("right_hand_wave") || id.equals("left_hand_wave")
                || id.equals("stomp_players") || id.equals("two_hand_wave"))) {
            return w * 2;
        }
        // Counter-attack: prefer arm waves
        if (wantCounter && (id.equals("right_hand_wave") || id.equals("left_hand_wave"))) {
            return w * 3;
        }
        if (id.equals("bomb_traps")) {
            return isFar ? 1 : 0;
        }
        // When target is far: prefer statue or charge ram
        if (isFar) {
            if (id.equals("statue_revival")) {
                return w * 2;
            }
            if (id.equals("charge_ram")) {
                return w * 3;
            }
        }
        if (isClose && id.equals("charge_ram")) {
            return 0; // Don't charge if target is already close
        }
        // Arena walls: require 2+ players within 5 blocks
        if (id.equals("arena_walls")) {
            int playersNear = 0;
            for (Player player : boss.level().players()) {
                if (player.isAlive() && player.level() == boss.level() && boss.distanceToSqr(player) <= 5.0D * 5.0D) {
                    playersNear++;
                }
            }
            if (playersNear >= 2) {
                return w * 4;
            }
            return 0;
        }
        // Leap attack: prefer when target is low HP
        if (id.equals("leap_attack")) {
            LivingEntity target = activeTarget();
            if (target != null && target.getHealth() / target.getMaxHealth() < 0.35F) {
                return w * 3;
            }
        }
        // Slam line only makes sense when target is roughly in front — canUse already checks that
        return w;
    }

    /** Returns a damage/radius multiplier based on current boss phase. */
    private float phaseMultiplier() {
        return switch (boss.getBossPhase()) {
            case THREE -> 1.35F;
            case TWO   -> 1.15F;
            default    -> 1.00F;
        };
    }

    private abstract class Attack {
        private final int phase;
        private final int stageStep;
        private int cooldown;

        private Attack(int phase, int stageStep) {
            this.phase = phase;
            this.stageStep = stageStep;
        }

        final void tickCooldown() {
            if (cooldown > 0) {
                cooldown--;
            }
        }

        final boolean canStart(ServerLevel level) {
            return cooldown <= 0 && activeTarget() != null && isUnlockedForCurrentPhase() && canUse(level);
        }

        private boolean isUnlockedForCurrentPhase() {
            int currentPhase = boss.getBossPhase().id();
            if (currentPhase < phase) {
                return false;
            }
            return currentPhase > phase || boss.getHiddenStageStep() >= stageStep;
        }

        final void startCooldown() {
            cooldown = cooldownTicks();
        }

        final AttackTiming timing() {
            return OverworldGuardianAttackConfig.get(id());
        }

        final boolean targetWithinConfiguredRange() {
            LivingEntity target = activeTarget();
            if (target == null) {
                return false;
            }
            double range = timing().maxStartDistance();
            return boss.distanceToSqr(target) <= range * range;
        }

        int weight() {
            return 1;
        }

        abstract boolean canUse(ServerLevel level);

        abstract RunningAttack start(ServerLevel level);

        abstract int cooldownTicks();

        abstract String id();
    }

    private interface RunningAttack {
        boolean tick(ServerLevel level);

        static RunningAttack instant() {
            return level -> true;
        }
    }

    private final class ArmWaveAttack extends Attack {
        private final String id;
        private final String animation;
        private final InteractionHand hand;

        private ArmWaveAttack(String id, String animation, InteractionHand hand) {
            super(1, 1);
            this.id = id;
            this.animation = animation;
            this.hand = hand;
        }

        @Override
        int weight() {
            return 4;
        }

        @Override
        boolean canUse(ServerLevel level) {
            return targetWithinConfiguredRange();
        }

        @Override
        RunningAttack start(ServerLevel level) {
            AttackTiming timing = timing();
            boss.swing(hand);
            boss.triggerAttackAnimation(animation);
            final Vec3 startLook = safeHorizontalLook();
            return new TimedAttack(timing.durationTicks()) {
                @Override
                protected void onTick(ServerLevel level, int tick) {
                    if (tick == timing.hitTick()) {
                        Vec3 center = boss.position();
                        Vec3 look = startLook;
                        Vec3 right = new Vec3(-look.z, 0, look.x);
                        Vec3 left = new Vec3(look.z, 0, -look.x);
                        Vec3 handPos = hand == InteractionHand.MAIN_HAND
                                ? center.add(look.scale(1.5D)).add(right.scale(1.0D))
                                : center.add(look.scale(1.5D)).add(left.scale(1.0D));

                        float closeDamage = (hand == InteractionHand.MAIN_HAND ? 10.0F : 9.0F) * phaseMultiplier();
                        float waveDamage = 5.0F * phaseMultiplier();
                        double waveRadius = 5.5D;

                        boolean hit = false;
                        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, boss.getBoundingBox().inflate(waveRadius, 2.0D, waveRadius))) {
                            if (living == boss || !living.isAlive() || horizontalDistance(living.position(), center) > waveRadius) {
                                continue;
                            }

                            Vec3 toTarget = living.position().subtract(center).horizontal();
                            boolean isMelee = false;
                            if (toTarget.lengthSqr() > 0.0001D) {
                                Vec3 toTargetNorm = toTarget.normalize();
                                double dot = look.dot(toTargetNorm);
                                double distToHand = horizontalDistance(living.position(), handPos);
                                double distToBoss = horizontalDistance(living.position(), center);
                                double crossY = look.z * toTarget.x - look.x * toTarget.z;
                                boolean correctSide = (hand == InteractionHand.MAIN_HAND) ? (crossY < 0) : (crossY > 0);
                                if (dot >= 0.5D && (correctSide || distToHand <= 1.5D) && (distToHand <= 1.5D || distToBoss <= 1.5D)) {
                                    isMelee = true;
                                }
                            }

                            float finalDamage = isMelee ? closeDamage : waveDamage;
                            if (living.hurtServer(level, boss.damageSources().mobAttack(boss), finalDamage)) {
                                hit = true;
                            }
                            Vec3 away = living.position().subtract(center).horizontal();
                            if (away.lengthSqr() > 0.0001D) {
                                double kb = boss.getBossPhase() == OverworldGuardianPhase.THREE ? 0.66D : 0.55D;
                                living.setDeltaMovement(away.normalize().scale(kb).add(0.0D, 0.25D, 0.0D));
                            }
                        }
                        if (hit) {
                            boss.recordSuccessfulHit();
                        }

                        emitGroundShockwave(level, center, 5.5D);
                    }
                }
            };
        }

        @Override
        int cooldownTicks() {
            return 58;
        }

        @Override
        String id() {
            return id;
        }
    }

    private final class DoubleHandWaveAttack extends Attack {
        private DoubleHandWaveAttack() {
            super(1, 1);
        }

        @Override
        int weight() {
            return 3;
        }

        @Override
        boolean canUse(ServerLevel level) {
            LivingEntity target = activeTarget();
            return targetWithinConfiguredRange() && target != null && isInFront(target, 0.15D);
        }

        @Override
        RunningAttack start(ServerLevel level) {
            AttackTiming timing = timing();
            boss.swing(InteractionHand.MAIN_HAND);
            boss.swing(InteractionHand.OFF_HAND);
            boss.triggerAttackAnimation("attack_both");
            final Vec3 startLook = safeHorizontalLook();
            return new TimedAttack(timing.durationTicks()) {
                @Override
                protected void onTick(ServerLevel level, int tick) {
                    if (tick == timing.hitTick()) {
                        Vec3 center = boss.position();
                        Vec3 look = startLook;
                        float meleeDmg = 14.0F * phaseMultiplier();
                        float waveDmg = 7.0F * phaseMultiplier();
                        double meleeRadius = 4.0D;
                        double waveRadius = 5.0D;
                        boolean hit = false;
                        
                        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, boss.getBoundingBox().inflate(waveRadius, 2.0D, waveRadius))) {
                            if (living == boss || !living.isAlive()) {
                                continue;
                            }
                            double dist = horizontalDistance(living.position(), center);
                            if (dist > waveRadius) {
                                continue;
                            }
                            
                            boolean isMelee = false;
                            Vec3 toTarget = living.position().subtract(center).horizontal();
                            if (dist <= meleeRadius && toTarget.lengthSqr() > 0.0001D) {
                                if (look.dot(toTarget.normalize()) >= 0.35D) {
                                    isMelee = true;
                                }
                            }
                            
                            float finalDamage = isMelee ? meleeDmg : waveDmg;
                            double kb = isMelee ? 1.2D : 0.85D;
                            double yVel = isMelee ? 0.45D : 0.35D;
                            
                            if (living.hurtServer(level, boss.damageSources().mobAttack(boss), finalDamage)) {
                                hit = true;
                            }
                            Vec3 away = living.position().subtract(center).horizontal();
                            if (away.lengthSqr() > 0.0001D) {
                                living.setDeltaMovement(away.normalize().scale(kb).add(0.0D, yVel, 0.0D));
                            }
                        }
                        if (hit) {
                            boss.recordSuccessfulHit();
                        }

                        emitGroundShockwave(level, center, 5.0D);

                        if (boss.getRandom().nextFloat() < 0.5F) {
                            LivingEntity target = activeTarget();
                            if (target != null) {
                                double offsetX = (boss.getRandom().nextDouble() - 0.5D) * 2.0D;
                                double offsetZ = (boss.getRandom().nextDouble() - 0.5D) * 2.0D;
                                BlockPos startBlockPos = BlockPos.containing(target.getX() + offsetX, target.getY() + 1.0D, target.getZ() + offsetZ);
                                BlockPos ceilingPos = findCeiling(level, startBlockPos);
                                if (ceilingPos != null) {
                                    var ceilingState = level.getBlockState(ceilingPos);
                                    CeilingFallingBlockEntity fallingBlock = new CeilingFallingBlockEntity(
                                            level,
                                            ceilingPos.getX() + 0.5D,
                                            ceilingPos.getY() - 0.5D,
                                            ceilingPos.getZ() + 0.5D,
                                            ceilingState
                                    );
                                    level.addFreshEntity(fallingBlock);
                                }
                            }
                        }
                    }
                }
            };
        }

        @Override
        int cooldownTicks() {
            return 84;
        }

        @Override
        String id() {
            return "two_hand_wave";
        }
    }

    private final class HandsSlamLineAttack extends Attack {
        private HandsSlamLineAttack() {
            super(1, 1);
        }

        @Override
        int weight() {
            return 3;
        }

        @Override
        boolean canUse(ServerLevel level) {
            LivingEntity target = activeTarget();
            return targetWithinConfiguredRange() && target != null && isInFront(target, 0.35D);
        }

        @Override
        RunningAttack start(ServerLevel level) {
            AttackTiming timing = timing();
            Vec3 start = boss.position();
            final Vec3 slamDir = safeHorizontalLook();
            final Vec3 lineEnd = start.add(slamDir.scale(14.0D));

            boss.swing(InteractionHand.MAIN_HAND);
            boss.swing(InteractionHand.OFF_HAND);
            boss.triggerAttackAnimation("attack_hands_slam");
            return new TimedAttack(timing.durationTicks()) {
                @Override
                protected void onTick(ServerLevel level, int tick) {
                    if (tick >= Math.max(1, timing.hitTick() - 19) && tick < timing.hitTick() - 3 && tick % 2 == 1) {
                        renderSlamLine(level, lineEnd, false);
                    }
                    if (tick == timing.hitTick()) {
                        renderSlamLine(level, lineEnd, true);
                        damageLine(level, lineEnd, slamDir, 13.0F * phaseMultiplier());
                        emitGroundShockwave(level, boss.position(), 6.0D);
                    }
                }
            };
        }

        @Override
        int cooldownTicks() {
            return 96;
        }

        @Override
        String id() {
            return "hands_slam_line";
        }
    }

    private final class StompPlayersAttack extends Attack {
        private StompPlayersAttack() {
            super(1, 2);
        }

        @Override
        int weight() {
            return 6;
        }

        @Override
        boolean canUse(ServerLevel level) {
            return targetWithinConfiguredRange();
        }

        @Override
        RunningAttack start(ServerLevel level) {
            AttackTiming timing = timing();
            boss.triggerAttackAnimation("stamTopTopTop");
            return new TimedAttack(timing.durationTicks()) {
                @Override
                protected void onTick(ServerLevel level, int tick) {
                    double radius = boss.getBossPhase() != OverworldGuardianPhase.ONE ? 9.5D : 7.5D;
                    if (tick == Math.max(1, timing.hitTick() - 26) || tick == Math.max(1, timing.hitTick() - 16) || tick == Math.max(1, timing.hitTick() - 6)) {
                        telegraphCircle(level, radius, 32);
                    }
                    // Expanding shockwave: close players are launched up first, farther ones later.
                    double midRadius = radius * 0.6D;
                    if (tick == timing.hitTick()) {
                        // Damage everyone in radius (no knockback yet — handled by the wave rings)
                        damageInRadius(level, boss.position(), radius, 10.0F * phaseMultiplier());
                        emitGroundShockwave(level, boss.position(), Math.min(3.0D, radius));
                        // Inner disc (0..3 blocks): immediate strong upward pop
                        applyKnockbackRing(level, 1.5D, 3.0D, 0.45D, 0.95D);
                    } else if (tick == timing.hitTick() + 4) {
                        emitGroundShockwave(level, boss.position(), midRadius);
                        // Mid ring: launched up strongly too, not just nudged
                        applyKnockbackRing(level, midRadius, 2.5D, 0.55D, 1.0D);
                    } else if (tick == timing.hitTick() + 8) {
                        emitGroundShockwave(level, boss.position(), radius);
                        // Outer ring: highest pop, so far players are thrown up as well
                        applyKnockbackRing(level, radius, 2.5D, 0.65D, 1.05D);
                    }
                }
            };
        }

        @Override
        int cooldownTicks() {
            return 92;
        }

        @Override
        String id() {
            return "stomp_players";
        }
    }

    private final class BombTrapsAttack extends Attack {
        private BombTrapsAttack() {
            super(1, 1);
        }

        @Override
        int weight() {
            return 1;
        }

        @Override
        boolean canUse(ServerLevel level) {
            return targetWithinConfiguredRange() && boss.distanceToSqr(activeTarget()) > 14.0D * 14.0D;
        }

        @Override
        RunningAttack start(ServerLevel level) {
            AttackTiming timing = timing();
            boss.triggerAttackAnimation("attack_both");
            return new TimedAttack(timing.durationTicks()) {
                @Override
                protected void onTick(ServerLevel level, int tick) {
                    if (tick != timing.hitTick()) {
                        return;
                    }
                    int count = Math.min(5, 3 + boss.getRandom().nextInt(2));
                    LivingEntity target = activeTarget();
                    Vec3 center = target != null ? target.position() : boss.position();
                    for (int i = 0; i < count; i++) {
                        double angle = boss.getRandom().nextDouble() * Math.PI * 2.0D;
                        double distance = 2.0D + boss.getRandom().nextDouble() * 8.0D;
                        double x = center.x + Math.cos(angle) * distance;
                        double z = center.z + Math.sin(angle) * distance;
                        BlockPos surfacePos = findSurface(level, BlockPos.containing(x, center.y, z));
                        BombTrapEntity bomb = new BombTrapEntity(level, x, surfacePos.getY() + 0.1D, z);
                        level.addFreshEntity(bomb);
                    }
                }
            };
        }

        @Override
        int cooldownTicks() {
            return 420;
        }

        @Override
        String id() {
            return "bomb_traps";
        }
    }

    private final class StatueRevivalAttack extends Attack {
        private static final int COOLDOWN = 600;

        StatueRevivalAttack() {
            super(2, 1);
        }

        @Override
        int cooldownTicks() {
            return COOLDOWN;
        }

        @Override
        boolean canUse(ServerLevel level) {
            // Only usable if there are player-placed statue blocks to revive.
            return targetWithinConfiguredRange()
                    && !boss.hasActiveStatues(level)
                    && !boss.hasActiveShield(level)
                    && !boss.hasSpawnedStatuesInCurrentPhase()
                    && !boss.findStatueBlocks(level, 1).isEmpty();
        }

        @Override
        RunningAttack start(ServerLevel level) {
            AttackTiming timing = timing();

            // Find up to 3 player-placed temple statue blocks — the boss only revives, never places.
            List<BlockPos> candidates = boss.findStatueBlocks(level, 16);
            java.util.Collections.shuffle(candidates, new java.util.Random(boss.getRandom().nextLong()));
            final List<BlockPos> positions = new ArrayList<>(candidates.subList(0, Math.min(3, candidates.size())));
            if (positions.isEmpty()) {
                return RunningAttack.instant();
            }
            boss.setStatuesSpawnedInCurrentPhase(true);

            return new TimedAttack(timing.durationTicks()) {
                @Override
                protected void onTick(ServerLevel level, int tick) {
                    if (tick < timing.hitTick() && tick % 5 == 0) {
                        for (BlockPos pos : positions) {
                            level.sendParticles(TELEGRAPH_DUST,
                                    pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D,
                                    4, 0.25, 0.5, 0.25, 0.0);
                        }
                    }
                    if (tick == timing.hitTick()) {
                        for (BlockPos pos : positions) {
                            if (!level.getBlockState(pos).is(ModBlocks.TEMPLE_STATUE)) {
                                continue; // block removed by player meanwhile
                            }
                            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                            TempleStatueEntity statue = ModEntities.TEMPLE_STATUE.create(
                                    level, EntitySpawnReason.MOB_SUMMONED);
                            if (statue == null) continue;
                            statue.setBossUUID(boss.getUUID());
                            statue.setPos(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
                            level.addFreshEntity(statue);
                            boss.addStatue(statue.getUUID());
                            boss.recordRevivedStatue(pos);
                            level.playSound(null, pos, net.minecraft.sounds.SoundEvents.STONE_BREAK,
                                    net.minecraft.sounds.SoundSource.HOSTILE, 1.5F, 0.8F);
                            level.sendParticles(ParticleTypes.LARGE_SMOKE,
                                    pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D,
                                    20, 0.3, 0.5, 0.3, 0.04);
                            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE_BRICKS.defaultBlockState()),
                                    pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D,
                                    40, 0.3, 0.5, 0.3, 0.1);
                        }
                    }
                }
            };
        }

        @Override
        String id() {
            return "statue_revival";
        }
    }

    private final class HealingShieldAttack extends Attack {
        private static final int COOLDOWN = 800;

        HealingShieldAttack() {
            super(2, 1);
        }

        @Override
        int cooldownTicks() {
            return COOLDOWN;
        }

        @Override
        boolean canUse(ServerLevel level) {
            return targetWithinConfiguredRange()
                    && !boss.hasActiveShield(level)
                    && !boss.hasActiveStatues(level)
                    && boss.getHealth() / boss.getMaxHealth() < 0.75F;
        }

        @Override
        RunningAttack start(ServerLevel level) {
            boss.spawnHealingShield(level);
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    boss.getX(), boss.getY() + 1.5, boss.getZ(),
                    30, 1.0, 1.0, 1.0, 0.05);
            return RunningAttack.instant();
        }

        @Override
        String id() {
            return "healing_shield";
        }
    }

    private final class ChargeRamAttack extends Attack {
        private static final double TARGET_RANGE = 32.0D;
        private static final double DASH_DISTANCE = 28.0D;
        private static final int DASH_TICKS = 24;
        private static final double WAVE_HALF_WIDTH = 1.5D;

        private ChargeRamAttack() {
            super(2, 2);
        }

        @Override
        int weight() {
            return 2;
        }

        @Override
        boolean canUse(ServerLevel level) {
            return furthestPlayerInRange(level, TARGET_RANGE) != null && !boss.shouldReturnTowardHome();
        }

        @Override
        RunningAttack start(ServerLevel level) {
            AttackTiming timing = timing();
            Player target = furthestPlayerInRange(level, TARGET_RANGE);
            if (target == null) {
                return RunningAttack.instant();
            }

            boss.triggerAttackAnimation("stamTopTopTop");
            final Player finalTarget = target;

            return new TimedAttack(timing.durationTicks()) {
                private Vec3 chargeDir = Vec3.ZERO;
                private Vec3 startPos = Vec3.ZERO;
                private Vec3 endPos = Vec3.ZERO;
                private boolean dashFinished = false;

                @Override
                protected void onTick(ServerLevel level, int tick) {
                    if (tick < timing.hitTick()) {
                        boss.getLookControl().setLookAt(finalTarget, 30.0F, 30.0F);
                        if (tick % 4 == 0) {
                            level.sendParticles(ParticleTypes.CRIT, boss.getX(), boss.getY() + 0.5D, boss.getZ(),
                                    4, 0.5D, 0.2D, 0.5D, 0.05D);
                        }
                    } else if (tick == timing.hitTick()) {
                        startPos = boss.position();
                        Vec3 toTarget = finalTarget.position().subtract(boss.position()).horizontal();
                        chargeDir = toTarget.lengthSqr() > 0.0001D ? toTarget.normalize() : safeHorizontalLook();
                        endPos = startPos.add(chargeDir.scale(DASH_DISTANCE));
                        boss.noPhysics = true;
                        boss.setDeltaMovement(Vec3.ZERO);
                    } else if (tick > timing.hitTick() && tick <= timing.hitTick() + DASH_TICKS) {
                        double progress = (tick - timing.hitTick()) / (double) DASH_TICKS;
                        Vec3 newPos = startPos.add(chargeDir.scale(DASH_DISTANCE * progress));
                        boss.setPos(newPos.x, newPos.y, newPos.z);
                        boss.setDeltaMovement(Vec3.ZERO);

                        boolean hit = false;
                        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, boss.getBoundingBox().inflate(1.2D, 0.6D, 1.2D))) {
                            if (living == boss || !living.isAlive()) {
                                continue;
                            }
                            if (living.hurtServer(level, boss.damageSources().mobAttack(boss), 14.0F * phaseMultiplier())) {
                                hit = true;
                            }
                            Vec3 away = living.position().subtract(boss.position()).horizontal();
                            if (away.lengthSqr() > 0.0001D) {
                                living.setDeltaMovement(away.normalize().scale(1.2D).add(0.0D, 0.4D, 0.0D));
                            }
                        }
                        if (hit) {
                            boss.recordSuccessfulHit();
                        }
                        level.sendParticles(DIRT_PARTICLE, boss.getX(), boss.getY() + 0.1D, boss.getZ(),
                                8, 0.5D, 0.1D, 0.5D, 0.12D);
                    } else if (!dashFinished && tick > timing.hitTick() + DASH_TICKS) {
                        dashFinished = true;
                        boss.setPos(endPos.x, endPos.y, endPos.z);
                        boss.noPhysics = false;
                        boss.setDeltaMovement(Vec3.ZERO);

                        double segmentLength = 1.5D;
                        Vec3 diff = endPos.subtract(startPos);
                        double totalDist = horizontalDistance(startPos, endPos);
                        if (totalDist > 0.1D) {
                            Vec3 step = new Vec3(diff.x, 0, diff.z).normalize();
                            // Visual wave rings along the dash line
                            for (double dist = segmentLength; dist <= totalDist; dist += segmentLength) {
                                Vec3 point = startPos.add(step.scale(dist));
                                spawnBlockRing(level, point, 1.5D);
                            }
                            // Damage every entity near the line exactly once
                            Vec3 mid = startPos.add(diff.scale(0.5D));
                            double halfLen = totalDist / 2.0D + 2.0D;
                            boolean hit = false;
                            for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class,
                                    boss.getBoundingBox().move(mid.subtract(boss.position())).inflate(halfLen, 4.0D, halfLen))) {
                                if (living == boss || !living.isAlive()) {
                                    continue;
                                }
                                double distToLine = distanceToSegment2d(living.position(), startPos, endPos);
                                if (distToLine <= WAVE_HALF_WIDTH) {
                                    double lerp = 1.0D - (distToLine / WAVE_HALF_WIDTH);
                                    float dmg = (float) (4.0D + lerp * 6.0D) * phaseMultiplier();
                                    if (living.hurtServer(level, boss.damageSources().mobAttack(boss), dmg)) {
                                        hit = true;
                                    }
                                    Vec3 away = living.position().subtract(mid).horizontal();
                                    if (away.lengthSqr() > 0.0001D) {
                                        living.setDeltaMovement(away.normalize().scale(0.8D).add(0.0D, 0.35D, 0.0D));
                                    }
                                }
                            }
                            if (hit) {
                                boss.recordSuccessfulHit();
                            }
                        }

                        emitGroundShockwave(level, endPos, 4.0D);
                        boss.setChargeStunned(200);
                    }
                }
            };
        }

        @Override
        int cooldownTicks() {
            return 400;
        }

        @Override
        String id() {
            return "charge_ram";
        }
    }

    private final class GroundVinesAttack extends Attack {
        private GroundVinesAttack() {
            super(2, 1);
        }

        @Override
        int weight() {
            return 2;
        }

        @Override
        boolean canUse(ServerLevel level) {
            return targetWithinConfiguredRange();
        }

        @Override
        RunningAttack start(ServerLevel level) {
            AttackTiming timing = timing();
            boss.triggerAttackAnimation("attack_both");

            List<Player> targets = new ArrayList<>();
            for (Player player : level.players()) {
                if (player.isAlive() && player.level() == boss.level() && boss.distanceToSqr(player) <= 30.0D * 30.0D) {
                    targets.add(player);
                }
            }
            java.util.Collections.shuffle(targets, new java.util.Random(boss.getRandom().nextLong()));
            final List<Player> finalTargets = targets.subList(0, Math.min(3, targets.size()));
            final List<Vec3> targetPositions = new ArrayList<>();
            for (Player p : finalTargets) {
                targetPositions.add(p.position());
            }

            return new TimedAttack(timing.durationTicks()) {
                @Override
                protected void onTick(ServerLevel level, int tick) {
                    if (tick < timing.hitTick() && tick % 4 == 0) {
                        level.sendParticles(DIRT_PARTICLE, boss.getX(), boss.getY() + 0.1D, boss.getZ(),
                                12, 1.5D, 0.2D, 1.5D, 0.05D);
                    }
                    if (tick >= timing.hitTick() - 20 && tick < timing.hitTick()) {
                        for (Vec3 pos : targetPositions) {
                            level.sendParticles(TELEGRAPH_DUST, pos.x, pos.y + 0.1D, pos.z,
                                    3, 0.2D, 0.05D, 0.2D, 0.0D);
                        }
                    }
                    if (tick == timing.hitTick()) {
                        for (int i = 0; i < finalTargets.size(); i++) {
                            Player p = finalTargets.get(i);
                            Vec3 targetPos = targetPositions.get(i);

                            Display.BlockDisplay display = new Display.BlockDisplay(EntityType.BLOCK_DISPLAY, level);
                            display.setPos(targetPos.x - 0.3D, targetPos.y, targetPos.z - 0.3D);
                            setBlockDisplayState(display, Blocks.JUNGLE_LEAVES.defaultBlockState());
                            Vector3f scale = new Vector3f(0.8F, 2.6F, 0.8F);
                            Transformation transformation = new Transformation(new Vector3f(0.0F, 0.0F, 0.0F), new Quaternionf(), scale, new Quaternionf());
                            setTransformation(display, transformation);
                            level.addFreshEntity(display);
                            // Vine lingers ~3s, matching the damaging area below
                            tempDisplays.add(new TemporaryDisplay(display, 60));

                            level.playSound(null, BlockPos.containing(targetPos), net.minecraft.sounds.SoundEvents.BIG_DRIPLEAF_TILT_DOWN,
                                    net.minecraft.sounds.SoundSource.HOSTILE, 1.5F, 0.7F);
                            level.sendParticles(DIRT_PARTICLE, targetPos.x, targetPos.y + 0.5D, targetPos.z,
                                    25, 0.5D, 0.5D, 0.5D, 0.12D);

                            // Damage happens ONLY on appearance: hit anyone near where the vine erupts.
                            // Afterwards the vine model just stands there doing nothing and then vanishes.
                            for (Player nearby : level.players()) {
                                if (!nearby.isAlive() || nearby.level() != boss.level()) {
                                    continue;
                                }
                                if (horizontalDistance(nearby.position(), targetPos) <= 3.0D
                                        && Math.abs(nearby.getY() - targetPos.y) <= 3.0D) {
                                    if (nearby.hurtServer(level, boss.damageSources().mobAttack(boss), 12.0F * phaseMultiplier())) {
                                        boss.recordSuccessfulHit();
                                    }
                                    nearby.setDeltaMovement(nearby.getDeltaMovement().x, 0.9D, nearby.getDeltaMovement().z);
                                    nearby.hurtMarked = true;
                                }
                            }
                        }

                        pendingFollowUpMelee = true;
                    }
                }
            };
        }

        @Override
        int cooldownTicks() {
            return 300;
        }

        @Override
        String id() {
            return "ground_vines";
        }
    }

    private final class VinePullAttack extends Attack {
        private VinePullAttack() {
            super(2, 3);
        }

        @Override
        int weight() {
            return 3;
        }

        @Override
        boolean canUse(ServerLevel level) {
            LivingEntity target = activeTarget();
            return targetWithinConfiguredRange() && target != null && boss.distanceToSqr(target) > 4.0D * 4.0D;
        }

        @Override
        RunningAttack start(ServerLevel level) {
            AttackTiming timing = timing();
            LivingEntity target = resolveTarget(level, 30.0D);
            if (target == null) {
                return RunningAttack.instant();
            }

            final LivingEntity finalTarget = target;
            boss.setTarget(finalTarget);
            boss.triggerAttackAnimation("attack_right");

            // Stack several vine segments along the boss→target line so they actually reach the
            // target; as the target is reeled in, segments are removed so fewer remain.
            int initialCount = (int) Math.ceil(boss.distanceTo(finalTarget) / 5.0D);
            initialCount = Math.max(2, Math.min(6, initialCount));
            final int segCount = initialCount;
            final List<VineLashEntity> vines = new ArrayList<>();
            for (int i = 0; i < segCount; i++) {
                VineLashEntity vine = new VineLashEntity(level, boss, finalTarget, timing.durationTicks() + 5, i, segCount);
                level.addFreshEntity(vine);
                vines.add(vine);
            }

            return new TimedAttack(timing.durationTicks()) {
                @Override
                protected void onTick(ServerLevel level, int tick) {
                    if (!finalTarget.isAlive()) {
                        vines.forEach(VineLashEntity::discard);
                        vines.clear();
                        return;
                    }

                    if (tick < timing.hitTick()) {
                        boss.getLookControl().setLookAt(finalTarget, 40.0F, 30.0F);
                    } else if (tick >= timing.hitTick() && tick < timing.durationTicks() - 2) {
                        pullLivingTowardBoss(level, finalTarget, 2.25D);
                        // Fewer vines as the target gets closer: keep one segment per ~5 blocks.
                        int desired = Math.max(1, Math.min(vines.size(),
                                (int) Math.ceil(boss.distanceTo(finalTarget) / 5.0D)));
                        while (vines.size() > desired) {
                            vines.remove(vines.size() - 1).discard();
                        }
                    }

                    if (tick == timing.durationTicks() - 1) {
                        vines.forEach(VineLashEntity::discard);
                        vines.clear();
                        snapNearBoss(finalTarget, 2.25D);
                        pendingCombo = "two_hand_wave";
                    }
                }
            };
        }

        @Override
        int cooldownTicks() {
            return 320;
        }

        @Override
        String id() {
            return "vine_pull";
        }
    }

    private final class ArenaWallsAttack extends Attack {
        private ArenaWallsAttack() {
            super(3, 1);
        }

        @Override
        int weight() {
            return 2;
        }

        @Override
        boolean canUse(ServerLevel level) {
            int playersNear = 0;
            for (Player player : level.players()) {
                if (player.isAlive() && player.level() == boss.level() && boss.distanceToSqr(player) <= 5.0D * 5.0D) {
                    playersNear++;
                }
            }
            return targetWithinConfiguredRange() && playersNear >= 2;
        }

        @Override
        RunningAttack start(ServerLevel level) {
            AttackTiming timing = timing();
            boss.triggerAttackAnimation("stamTopTopTop");

            return new TimedAttack(timing.durationTicks()) {
                @Override
                protected void onTick(ServerLevel level, int tick) {
                    if (tick == timing.hitTick()) {
                        Vec3 center = boss.position();
                        double radius = 4.5D;
                        int segments = 10;
                        level.playSound(null, boss.blockPosition(), net.minecraft.sounds.SoundEvents.WITHER_SPAWN,
                                net.minecraft.sounds.SoundSource.HOSTILE, 1.5F, 0.8F);

                        for (int i = 0; i < segments; i++) {
                            double angle = (Math.PI * 2.0D) * i / segments;
                            double wx = center.x + Math.cos(angle) * radius;
                            double wz = center.z + Math.sin(angle) * radius;
                            BlockPos surfacePos = findSurface(level, new BlockPos((int) wx, (int) center.y, (int) wz));

                            for (int dy = 0; dy < 3; dy++) {
                                TempleWallSegmentEntity segment = ModEntities.TEMPLE_WALL_SEGMENT.create(level, EntitySpawnReason.MOB_SUMMONED);
                                if (segment != null) {
                                    segment.setRingCenter(center);
                                    segment.setPos(wx, surfacePos.getY() + dy, wz);
                                    level.addFreshEntity(segment);
                                }
                            }

                            level.sendParticles(ParticleTypes.LARGE_SMOKE, wx, surfacePos.getY() + 1.5D, wz,
                                    5, 0.2, 0.5, 0.2, 0.02);
                        }

                        // Wind-charge-style shove: pull entities near/outside the wall ring back toward
                        // the centre so they are trapped inside instead of left stuck in the wall.
                        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class,
                                boss.getBoundingBox().inflate(radius + 3.0D, 3.0D, radius + 3.0D))) {
                            if (living == boss || !living.isAlive()) {
                                continue;
                            }
                            double dist = horizontalDistance(living.position(), center);
                            if (dist < radius - 1.0D) {
                                continue; // already safely inside
                            }
                            Vec3 inward = center.subtract(living.position()).horizontal();
                            if (inward.lengthSqr() > 0.0001D) {
                                living.setDeltaMovement(inward.normalize().scale(0.9D).add(0.0D, 0.35D, 0.0D));
                                living.hurtMarked = true;
                            }
                        }
                    }
                }
            };
        }

        @Override
        int cooldownTicks() {
            return 500;
        }

        @Override
        String id() {
            return "arena_walls";
        }
    }

    private final class LeapAttack extends Attack {
        private LeapAttack() {
            super(3, 2);
        }

        @Override
        int weight() {
            return 1;
        }

        @Override
        boolean canUse(ServerLevel level) {
            return targetWithinConfiguredRange();
        }

        @Override
        RunningAttack start(ServerLevel level) {
            AttackTiming timing = timing();
            LivingEntity target = resolveTarget(level, 24.0D);
            if (target == null) {
                return RunningAttack.instant();
            }

            final LivingEntity finalTarget = target;
            boss.setTarget(finalTarget);
            boss.triggerAttackAnimation("stamTopTopTop");

            return new TimedAttack(timing.durationTicks()) {
                private Vec3 startPos = Vec3.ZERO;
                private Vec3 landingPos = Vec3.ZERO;

                @Override
                protected void onTick(ServerLevel level, int tick) {
                    if (tick < timing.hitTick()) {
                        boss.getLookControl().setLookAt(finalTarget, 30.0F, 30.0F);
                        if (tick % 5 == 0) {
                            telegraphCircle(level, 2.5D, 16);
                        }
                    } else if (tick == timing.hitTick()) {
                        startPos = boss.position();
                        landingPos = finalTarget.position();
                        boss.noPhysics = true;
                    } else if (tick > timing.hitTick() && tick < timing.hitTick() + 15) {
                        double progress = (double) (tick - timing.hitTick()) / 15.0D;
                        Vec3 currentPos = startPos.lerp(landingPos, progress);
                        double heightOffset = Math.sin(progress * Math.PI) * 10.0D;
                        boss.setPos(currentPos.x, currentPos.y + heightOffset, currentPos.z);

                        level.sendParticles(ParticleTypes.GUST, boss.getX(), boss.getY() + 0.5D, boss.getZ(),
                                4, 0.5D, 0.5D, 0.5D, 0.05D);
                    } else if (tick == timing.hitTick() + 15) {
                        boss.noPhysics = false;
                        boss.setPos(landingPos.x, landingPos.y, landingPos.z);
                        boss.setDeltaMovement(0, 0, 0);

                        radialImpact(level, 2.5D, 19.0F * phaseMultiplier(), 1.4D, 0.6D);
                        level.playSound(null, boss.blockPosition(), net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(),
                                net.minecraft.sounds.SoundSource.HOSTILE, 1.5F, 0.8F);
                    }
                }
            };
        }

        @Override
        int cooldownTicks() {
            return 600;
        }

        @Override
        String id() {
            return "leap_attack";
        }
    }

    private abstract class TimedAttack implements RunningAttack {
        private final int duration;
        private int tick;

        private TimedAttack(int duration) {
            this.duration = duration;
        }

        @Override
        public final boolean tick(ServerLevel level) {
            tick++;
            onTick(level, tick);
            return tick >= duration;
        }

        protected abstract void onTick(ServerLevel level, int tick);
    }

    private void radialImpact(ServerLevel level, double radius, float damage, double knockback, double yVelocity) {
        Vec3 center = boss.position();
        level.sendParticles(ParticleTypes.GUST_EMITTER_LARGE, center.x, center.y + 0.25D, center.z,
                1, 0.05D, 0.05D, 0.05D, 0.0D);
        level.sendParticles(DIRT_PARTICLE, center.x, center.y + 0.1D, center.z,
                60, radius * 0.35D, 0.2D, radius * 0.35D, 0.12D);

        boolean hit = false;
        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, boss.getBoundingBox().inflate(radius, 2.0D, radius))) {
            if (living == boss || !living.isAlive() || horizontalDistance(living.position(), center) > radius) {
                continue;
            }
            if (living.hurtServer(level, boss.damageSources().mobAttack(boss), damage)) {
                hit = true;
            }
            Vec3 away = living.position().subtract(center).horizontal();
            if (away.lengthSqr() > 0.0001D) {
                living.setDeltaMovement(away.normalize().scale(knockback).add(0.0D, yVelocity, 0.0D));
            }
        }
        if (hit) {
            boss.recordSuccessfulHit();
        }
    }

    /** Deals damage to all living entities within a horizontal radius, without applying knockback. */
    private void damageInRadius(ServerLevel level, Vec3 center, double radius, float damage) {
        level.sendParticles(ParticleTypes.GUST_EMITTER_LARGE, center.x, center.y + 0.25D, center.z,
                1, 0.05D, 0.05D, 0.05D, 0.0D);
        boolean hit = false;
        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, boss.getBoundingBox().inflate(radius, 2.0D, radius))) {
            if (living == boss || !living.isAlive() || horizontalDistance(living.position(), center) > radius) {
                continue;
            }
            if (living.hurtServer(level, boss.damageSources().mobAttack(boss), damage)) {
                hit = true;
            }
        }
        if (hit) {
            boss.recordSuccessfulHit();
        }
    }

    /** Returns the current target, or the nearest player within range if there is none (for /guardian boss attack testing). */
    private LivingEntity resolveTarget(ServerLevel level, double range) {
        LivingEntity target = activeTarget();
        if (target != null) {
            return target;
        }
        return nearestPlayer(level, range);
    }

    private void telegraphCircle(ServerLevel level, double radius, int points) {
        Vec3 center = boss.position();
        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2.0D) * i / points;
            level.sendParticles(TELEGRAPH_DUST,
                    center.x + Math.cos(angle) * radius,
                    boss.getY() + 0.12D,
                    center.z + Math.sin(angle) * radius,
                    1, 0.03D, 0.01D, 0.03D, 0.0D);
        }
    }

    private void renderSlamLine(ServerLevel level, Vec3 target, boolean heavy) {
        Vec3 start = boss.position();
        Vec3 direction = target.subtract(start).horizontal();
        if (direction.lengthSqr() < 0.0001D) {
            return;
        }
        Vec3 step = direction.normalize();
        double length = direction.length();
        for (double distance = 1.7D; distance <= length; distance += heavy ? 0.55D : 1.05D) {
            Vec3 point = start.add(step.scale(distance));
            if (heavy) {
                BlockPos pos = BlockPos.containing(point.x, boss.getY() - 0.5D, point.z);
                var state = level.getBlockState(pos);
                if (state.isAir()) {
                    pos = pos.below();
                    state = level.getBlockState(pos);
                }
                if (!state.isAir()) {
                    var option = new BlockParticleOption(ParticleTypes.BLOCK, state);
                    level.sendParticles(option, point.x, boss.getY() + 0.1D, point.z,
                            12, 0.15D, 0.4D, 0.15D, 0.2D);
                    level.sendParticles(ParticleTypes.POOF, point.x, boss.getY() + 0.1D, point.z,
                            2, 0.1D, 0.1D, 0.1D, 0.01D);
                }
            } else {
                level.sendParticles(ParticleTypes.CRIT,
                        point.x, boss.getY() + 0.1D, point.z,
                        2, 0.1D, 0.1D, 0.1D, 0.01D);
            }
        }
    }

    private void damageLine(ServerLevel level, Vec3 target, Vec3 slamDir, float damage) {
        Vec3 start = boss.position();
        boolean hit = false;
        double halfWidth = boss.getBossPhase() == OverworldGuardianPhase.THREE ? 1.85D : 1.25D;
        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, boss.getBoundingBox().inflate(20.0D, 2.5D, 20.0D))) {
            if (living == boss || !living.isAlive() || distanceToSegment2d(living.position(), start, target) > halfWidth) {
                continue;
            }
            Vec3 toTarget = living.position().subtract(start).horizontal();
            if (toTarget.lengthSqr() > 0.0001D && slamDir.dot(toTarget.normalize()) < 0.25D) {
                continue;
            }
            if (living.hurtServer(level, boss.damageSources().mobAttack(boss), damage)) {
                hit = true;
            }
            Vec3 away = living.position().subtract(start).horizontal();
            if (away.lengthSqr() > 0.0001D) {
                living.setDeltaMovement(away.normalize().scale(0.75D).add(0.0D, 0.4D, 0.0D));
            }
        }
        if (hit) {
            boss.recordSuccessfulHit();
        }
    }

    private void directionalImpact(ServerLevel level, double radius, float damage, double knockback, double yVelocity) {
        Vec3 center = boss.position();
        Vec3 look = safeHorizontalLook();
        Vec3 gustPos = center.add(look.scale(1.5D));
        level.sendParticles(ParticleTypes.GUST_EMITTER_LARGE, gustPos.x, gustPos.y + 0.25D, gustPos.z,
                1, 0.05D, 0.05D, 0.05D, 0.0D);

        boolean hit = false;
        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, boss.getBoundingBox().inflate(radius, 2.0D, radius))) {
            if (living == boss || !living.isAlive() || horizontalDistance(living.position(), center) > radius) {
                continue;
            }
            Vec3 toTarget = living.position().subtract(center).horizontal();
            if (toTarget.lengthSqr() < 0.0001D || look.dot(toTarget.normalize()) < 0.35D) {
                continue;
            }

            if (living.hurtServer(level, boss.damageSources().mobAttack(boss), damage)) {
                hit = true;
            }
            Vec3 away = living.position().subtract(center).horizontal();
            if (away.lengthSqr() > 0.0001D) {
                living.setDeltaMovement(away.normalize().scale(knockback).add(0.0D, yVelocity, 0.0D));
            }
        }
        if (hit) {
            boss.recordSuccessfulHit();
        }
    }

    private void pullLivingTowardBoss(ServerLevel level, LivingEntity target, double stopDistance) {
        Vec3 bossPos = boss.position();
        Vec3 targetPos = target.position();
        Vec3 diff = bossPos.subtract(targetPos);
        double dist = horizontalDistance(targetPos, bossPos);
        if (dist <= stopDistance || dist < 0.001D) {
            return;
        }
        Vec3 horizontal = new Vec3(diff.x, 0.0D, diff.z).normalize();
        double speed = Math.min(0.85D, 0.2D + dist * 0.08D);
        target.setDeltaMovement(horizontal.scale(speed).add(0.0D, 0.12D, 0.0D));
        target.resetFallDistance();
        target.hurtMarked = true;
    }

    private void snapNearBoss(LivingEntity target, double stopDistance) {
        Vec3 bossPos = boss.position();
        Vec3 targetPos = target.position();
        Vec3 diff = bossPos.subtract(targetPos);
        double dist = horizontalDistance(targetPos, bossPos);
        if (dist <= stopDistance || dist < 0.001D) {
            return;
        }
        Vec3 horizontal = new Vec3(diff.x, 0.0D, diff.z).normalize();
        Vec3 snapped = bossPos.subtract(horizontal.scale(stopDistance - 0.35D));
        target.setPos(snapped.x, target.getY(), snapped.z);
        target.setDeltaMovement(Vec3.ZERO);
        target.resetFallDistance();
    }

    /**
     * Starts a Wildfire-style ground shockwave: an expanding ring of block particles that travels
     * outward from the centre over the following ticks. No white particles — only fragments of the
     * actual ground blocks.
     */
    private void emitGroundShockwave(ServerLevel level, Vec3 center, double maxRadius) {
        activeShockwaves.add(new Shockwave(center, Math.max(1.5D, maxRadius)));
    }

    /**
     * Spawns ONE ring of block particles at the given radius. The block state is read from the
     * ground directly under each point, so the wave kicks up the real floor blocks
     * (BlockParticleOption(ParticleTypes.BLOCK, state) — the Wildfire / Friends&Foes look).
     * Block particles spawned with count=0 use (dx,dy,dz) as a directed velocity, giving the
     * upward "blocks rising" motion. No FallingBlockEntity, no persistent entities.
     */
    private void spawnBlockRing(ServerLevel level, Vec3 center, double radius) {
        int count = Math.max(8, (int) Math.round(radius * 6.0D));
        for (int i = 0; i < count; i++) {
            double angle = (Math.PI * 2.0D) * i / count;
            double x = center.x + Math.cos(angle) * radius;
            double z = center.z + Math.sin(angle) * radius;
            BlockPos pos = BlockPos.containing(x, center.y - 0.5D, z);
            var state = level.getBlockState(pos);
            if (state.isAir()) {
                pos = pos.below();
                state = level.getBlockState(pos);
            }
            if (state.isAir() || state.is(Blocks.BARRIER) || state.is(Blocks.LIGHT)) {
                continue;
            }
            BlockParticleOption option = new BlockParticleOption(ParticleTypes.BLOCK, state);
            double topY = pos.getY() + 1.0D;
            // Spray of block fragments rising from the floor
            level.sendParticles(option, x, topY, z, 6, 0.12D, 0.05D, 0.12D, 0.08D);
            // A couple of chunks launched straight up
            for (int k = 0; k < 2; k++) {
                double vx = (boss.getRandom().nextDouble() - 0.5D) * 0.12D;
                double vy = 0.45D + boss.getRandom().nextDouble() * 0.25D;
                double vz = (boss.getRandom().nextDouble() - 0.5D) * 0.12D;
                level.sendParticles(option, x, topY, z, 0, vx, vy, vz, 1.0D);
            }
        }
    }

    /**
     * Expanding ground shockwave (Wildfire / Friends&Foes style). Each tick the ring radius grows
     * and a fresh ring of ground-block particles is spawned, so the wave visibly travels outward
     * across the floor. Pure particles — no entities.
     */
    private final class Shockwave {
        private final Vec3 center;
        private final double maxRadius;
        private double radius;

        private Shockwave(Vec3 center, double maxRadius) {
            this.center = center;
            this.maxRadius = maxRadius;
            this.radius = 0.8D;
        }

        private boolean tick(ServerLevel level) {
            spawnBlockRing(level, center, radius);
            radius += 1.0D;
            return radius > maxRadius;
        }
    }

    private BlockPos findCeiling(ServerLevel level, BlockPos startPos) {
        BlockPos.MutableBlockPos pos = startPos.mutable();
        for (int y = 0; y < 30; y++) {
            pos.move(0, 1, 0);
            var state = level.getBlockState(pos);
            if (!state.isAir() && !state.liquid()) {
                return pos.immutable();
            }
        }
        return null;
    }

    private BlockPos findSurface(ServerLevel level, BlockPos pos) {
        BlockPos cursor = pos;
        for (int i = 0; i < 10 && !level.getBlockState(cursor).isAir(); i++) {
            cursor = cursor.above();
        }
        for (int i = 0; i < 18 && level.getBlockState(cursor.below()).isAir(); i++) {
            cursor = cursor.below();
        }
        return cursor;
    }

    private boolean isInFront(LivingEntity target, double minDot) {
        Vec3 toTarget = target.position().subtract(boss.position()).horizontal();
        if (toTarget.lengthSqr() < 0.0001D) {
            return true;
        }
        return safeHorizontalLook().dot(toTarget.normalize()) >= minDot;
    }

    private Vec3 safeHorizontalLook() {
        // Prefer direction toward the active target — LookControl updates yaw gradually,
        // so getLookAngle() can lag behind the actual target position at attack start.
        LivingEntity target = activeTarget();
        if (target != null) {
            Vec3 toTarget = target.position().subtract(boss.position()).horizontal();
            if (toTarget.lengthSqr() > 0.0001D) {
                return toTarget.normalize();
            }
        }
        Vec3 look = boss.getLookAngle().horizontal();
        if (look.lengthSqr() < 0.0001D) {
            return new Vec3(0.0D, 0.0D, 1.0D);
        }
        return look.normalize();
    }

    private LivingEntity activeTarget() {
        LivingEntity target = boss.getTarget();
        return target != null && target.isAlive() ? target : null;
    }

    private static double horizontalDistance(Vec3 first, Vec3 second) {
        double x = first.x - second.x;
        double z = first.z - second.z;
        return Math.sqrt(x * x + z * z);
    }

    private static double distanceToSegment2d(Vec3 point, Vec3 start, Vec3 end) {
        double dx = end.x - start.x;
        double dz = end.z - start.z;
        double lengthSqr = dx * dx + dz * dz;
        if (lengthSqr < 0.0001D) {
            return horizontalDistance(point, start);
        }
        double t = ((point.x - start.x) * dx + (point.z - start.z) * dz) / lengthSqr;
        t = Math.max(0.0D, Math.min(1.0D, t));
        double nearestX = start.x + dx * t;
        double nearestZ = start.z + dz * t;
        double x = point.x - nearestX;
        double z = point.z - nearestZ;
        return Math.sqrt(x * x + z * z);
    }

    private static void setBlockDisplayState(Display.BlockDisplay display, net.minecraft.world.level.block.state.BlockState state) {
        try {
            java.lang.reflect.Field field = Display.BlockDisplay.class.getDeclaredField("DATA_BLOCK_STATE_ID");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            net.minecraft.network.syncher.EntityDataAccessor<net.minecraft.world.level.block.state.BlockState> accessor = 
                    (net.minecraft.network.syncher.EntityDataAccessor<net.minecraft.world.level.block.state.BlockState>) field.get(null);
            display.getEntityData().set(accessor, state);
        } catch (Exception e) {
            me.guardian.GuardianMod.LOGGER.error("Failed to set BlockDisplay BlockState via reflection", e);
        }
    }

    private static void setTransformation(Display display, com.mojang.math.Transformation transformation) {
        try {
            java.lang.reflect.Method method = Display.class.getDeclaredMethod("setTransformation", com.mojang.math.Transformation.class);
            method.setAccessible(true);
            method.invoke(display, transformation);
        } catch (Exception e) {
            me.guardian.GuardianMod.LOGGER.error("Failed to set Display Transformation via reflection", e);
        }
    }

    private void applyKnockbackRing(ServerLevel level, double radius, double thickness, double strength, double yVelocity) {
        Vec3 center = boss.position();
        double minRadius = radius - thickness / 2.0D;
        double maxRadius = radius + thickness / 2.0D;
        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, boss.getBoundingBox().inflate(maxRadius, 2.0D, maxRadius))) {
            if (living == boss || !living.isAlive()) {
                continue;
            }
            double dist = horizontalDistance(living.position(), center);
            if (dist >= minRadius && dist <= maxRadius) {
                Vec3 away = living.position().subtract(center).horizontal();
                if (away.lengthSqr() > 0.0001D) {
                    living.setDeltaMovement(away.normalize().scale(strength).add(0.0D, yVelocity, 0.0D));
                }
            }
        }
    }

    private LivingEntity nearestPlayer(ServerLevel level, double range) {
        Player nearest = null;
        double minDist = Double.MAX_VALUE;
        for (Player player : level.players()) {
            if (player.isAlive() && player.level() == boss.level()) {
                double dist = boss.distanceToSqr(player);
                if (dist <= range * range && dist < minDist) {
                    minDist = dist;
                    nearest = player;
                }
            }
        }
        return nearest;
    }

    private Player furthestPlayerInRange(ServerLevel level, double range) {
        Player furthest = null;
        double maxDist = -1.0D;
        double rangeSqr = range * range;
        for (Player player : level.players()) {
            if (!player.isAlive() || player.level() != boss.level()) {
                continue;
            }
            double distSqr = boss.distanceToSqr(player);
            if (distSqr <= rangeSqr && distSqr > maxDist) {
                maxDist = distSqr;
                furthest = player;
            }
        }
        return furthest;
    }

    private static final class TemporaryDisplay {
        private final Display.BlockDisplay entity;
        private final int maxAge;
        private int age;

        private TemporaryDisplay(Display.BlockDisplay entity, int maxAge) {
            this.entity = entity;
            this.maxAge = maxAge;
            this.age = 0;
        }

        private boolean tick() {
            age++;
            if (entity.isRemoved()) {
                return true;
            }
            if (age >= maxAge) {
                entity.discard();
                return true;
            }
            return false;
        }
    }
}
