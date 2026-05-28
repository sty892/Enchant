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
    private static final BlockParticleOption STONE_PARTICLE = new BlockParticleOption(
            ParticleTypes.DUST_PILLAR,
            Blocks.STONE.defaultBlockState()
    );
    private static final DustParticleOptions TELEGRAPH_DUST = new DustParticleOptions(0xC9FFF3, 1.25F);

    private final OverworldGuardianEntity boss;
    private final Attack[] attacks;
    private RunningAttack runningAttack;
    private int globalDelay = 20;

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
                new HealingShieldAttack()
        };
    }

    public void tick(ServerLevel level) {
        OverworldGuardianAttackConfig.tickAutoReload(level);
        for (Attack attack : attacks) {
            attack.tickCooldown();
        }

        if (runningAttack != null) {
            boss.getNavigation().stop();
            if (runningAttack.tick(level)) {
                runningAttack = null;
                globalDelay = 26;
            }
            return;
        }

        if (boss.isAiDisabled()) {
            boss.getNavigation().stop();
            return;
        }

        LivingEntity target = activeTarget();
        if (target == null) {
            globalDelay = 10;
            return;
        }

        boolean chasing = false;
        if (boss.shouldReturnTowardHome()) {
            Vec3 home = boss.homeCenter();
            boss.getNavigation().moveTo(home.x, home.y, home.z, 1.05D);
        } else if (boss.distanceToSqr(target) > CHASE_RANGE_SQR) {
            chasing = true;
            boss.getNavigation().moveTo(target, 1.18D);
        } else {
            boss.getNavigation().stop();
        }
        if (!chasing) {
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
            runningAttack = attack.start(level);
            globalDelay = 0;
            return true;
        }
        return false;
    }

    private Attack selectAttack(ServerLevel level) {
        int totalWeight = 0;
        for (Attack attack : attacks) {
            if (attack.canStart(level)) {
                totalWeight += attack.weight();
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
            roll -= attack.weight();
            if (roll < 0) {
                return attack;
            }
        }
        return null;
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
            return new TimedAttack(timing.durationTicks()) {
                @Override
                protected void onTick(ServerLevel level, int tick) {
                    if (tick == timing.hitTick()) {
                        Vec3 center = boss.position();
                        Vec3 look = safeHorizontalLook();
                        Vec3 right = new Vec3(-look.z, 0, look.x);
                        Vec3 left = new Vec3(look.z, 0, -look.x);
                        Vec3 handPos = hand == InteractionHand.MAIN_HAND
                                ? center.add(look.scale(1.5D)).add(right.scale(1.0D))
                                : center.add(look.scale(1.5D)).add(left.scale(1.0D));

                        float closeDamage = hand == InteractionHand.MAIN_HAND ? 10.0F : 9.0F;
                        float waveDamage = 5.0F;
                        double waveRadius = 5.5D;

                        boolean hit = false;
                        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, boss.getBoundingBox().inflate(waveRadius, 2.0D, waveRadius))) {
                            if (living == boss || !living.isAlive() || horizontalDistance(living.position(), center) > waveRadius) {
                                continue;
                            }

                            float finalDamage = horizontalDistance(living.position(), handPos) <= 1.5D ? closeDamage : waveDamage;
                            if (living.hurtServer(level, boss.damageSources().mobAttack(boss), finalDamage)) {
                                hit = true;
                            }
                            Vec3 away = living.position().subtract(center).horizontal();
                            if (away.lengthSqr() > 0.0001D) {
                                living.setDeltaMovement(away.normalize().scale(0.55D).add(0.0D, 0.25D, 0.0D));
                            }
                        }
                        if (hit) {
                            boss.recordSuccessfulHit();
                        }

                        radialBlockWaveRing(level, boss.position(), 1.5D, 12);
                    } else if (tick == timing.hitTick() + 2) {
                        radialBlockWaveRing(level, boss.position(), 3.5D, 24);
                    } else if (tick == timing.hitTick() + 4) {
                        radialBlockWaveRing(level, boss.position(), 5.5D, 36);
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
            return new TimedAttack(timing.durationTicks()) {
                @Override
                protected void onTick(ServerLevel level, int tick) {
                    if (tick == timing.hitTick()) {
                        directionalImpact(level, 7.5D, 12.0F, 1.05D, 0.45D);
                        radialBlockWaveRing(level, boss.position(), 2.0D, 16);

                        if (boss.getRandom().nextFloat() < 0.5F) {
                            LivingEntity target = activeTarget();
                            if (target != null) {
                                BlockPos ceilingPos = findCeiling(level, target.blockPosition());
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
                    } else if (tick == timing.hitTick() + 2) {
                        radialBlockWaveRing(level, boss.position(), 4.5D, 32);
                    } else if (tick == timing.hitTick() + 4) {
                        radialBlockWaveRing(level, boss.position(), 7.0D, 48);
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
            Vec3 dir = safeHorizontalLook();
            Vec3 lineEnd = start.add(dir.scale(14.0D));

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
                        damageLine(level, lineEnd, 13.0F);
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
                    if (tick == Math.max(1, timing.hitTick() - 26) || tick == Math.max(1, timing.hitTick() - 16) || tick == Math.max(1, timing.hitTick() - 6)) {
                        telegraphCircle(level, 7.5D, 32);
                    }
                    if (tick == timing.hitTick()) {
                        radialImpact(level, 7.5D, 10.0F, 1.25D, 0.45D);
                        radialBlockWaveRing(level, boss.position(), 1.5D, 16);
                    } else if (tick == timing.hitTick() + 2) {
                        radialBlockWaveRing(level, boss.position(), 3.5D, 24);
                    } else if (tick == timing.hitTick() + 4) {
                        radialBlockWaveRing(level, boss.position(), 5.5D, 32);
                    } else if (tick == timing.hitTick() + 6) {
                        radialBlockWaveRing(level, boss.position(), 7.5D, 40);
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
            return 3;
        }

        @Override
        boolean canUse(ServerLevel level) {
            return targetWithinConfiguredRange();
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
                    int count = 4 + boss.getRandom().nextInt(3);
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
            return 110;
        }

        @Override
        String id() {
            return "bomb_traps";
        }
    }

    private final class StatueRevivalAttack extends Attack {
        private static final int COOLDOWN = 600;
        private static final int MAX_STATUES = 3;

        StatueRevivalAttack() {
            super(2, 1);
        }

        @Override
        int cooldownTicks() {
            return COOLDOWN;
        }

        @Override
        boolean canUse(ServerLevel level) {
            return targetWithinConfiguredRange() && !boss.hasActiveStatues(level);
        }

        @Override
        RunningAttack start(ServerLevel level) {
            AttackTiming timing = timing();
            Vec3 center = boss.homeCenter();

            // 1. Find surface positions for statues
            List<BlockPos> positions = new ArrayList<>();
            for (int attempt = 0; attempt < 30 && positions.size() < MAX_STATUES; attempt++) {
                double angle = boss.getRandom().nextDouble() * Math.PI * 2;
                double dist = 16.0;
                double sx = center.x + Math.cos(angle) * dist;
                double sz = center.z + Math.sin(angle) * dist;
                BlockPos surfacePos = findSurface(level, new BlockPos((int) sx, (int) center.y, (int) sz));
                // Skip if air above (floating)
                if (!level.getBlockState(surfacePos.below()).blocksMotion()) {
                    continue;
                }
                positions.add(surfacePos);
            }

            // 2. Place dormant statue blocks
            for (BlockPos pos : positions) {
                level.setBlock(pos, ModBlocks.TEMPLE_STATUE.defaultBlockState(), 3);
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE_BRICKS.defaultBlockState()),
                        pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                        20, 0.3, 0.4, 0.3, 0.08);
            }
            boss.setStatueBlocks(level, positions);

            // 3. Return timed attack: at hit_tick revive blocks into zombies
            return new TimedAttack(timing.durationTicks()) {
                @Override
                protected void onTick(ServerLevel level, int tick) {
                    // Particle buildup leading up to revival
                    if (tick < timing.hitTick() && tick % 5 == 0) {
                        for (BlockPos pos : positions) {
                            level.sendParticles(TELEGRAPH_DUST,
                                    pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D,
                                    4, 0.25, 0.5, 0.25, 0.0);
                        }
                    }
                    // At hit tick: remove blocks, spawn zombie mobs
                    if (tick == timing.hitTick()) {
                        for (BlockPos pos : positions) {
                            // Remove the statue block
                            if (level.getBlockState(pos).is(ModBlocks.TEMPLE_STATUE)) {
                                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                            }
                            // Spawn revived zombie statue entity
                            TempleStatueEntity statue = ModEntities.TEMPLE_STATUE.create(
                                    level, EntitySpawnReason.MOB_SUMMONED);
                            if (statue == null) continue;
                            statue.setBossUUID(boss.getUUID());
                            statue.setPos(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
                            level.addFreshEntity(statue);
                            boss.addStatue(statue.getUUID());
                            // Revive FX
                            level.playSound(null, pos, net.minecraft.sounds.SoundEvents.STONE_BREAK,
                                    net.minecraft.sounds.SoundSource.HOSTILE, 1.5F, 0.8F);
                            level.sendParticles(ParticleTypes.LARGE_SMOKE,
                                    pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D,
                                    20, 0.3, 0.5, 0.3, 0.04);
                            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE_BRICKS.defaultBlockState()),
                                    pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D,
                                    40, 0.3, 0.5, 0.3, 0.1);
                        }
                        // Clear dormant block tracking — they've been converted to mobs
                        boss.clearStatueBlocks(level);
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
            return targetWithinConfiguredRange() && !boss.hasActiveShield(level) && boss.getHealth() / boss.getMaxHealth() < 0.75F;
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

    private void damageLine(ServerLevel level, Vec3 target, float damage) {
        Vec3 start = boss.position();
        boolean hit = false;
        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, boss.getBoundingBox().inflate(20.0D, 2.5D, 20.0D))) {
            if (living == boss || !living.isAlive() || distanceToSegment2d(living.position(), start, target) > 1.45D) {
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

    private void radialBlockWaveRing(ServerLevel level, Vec3 center, double radius, int count) {
        for (int i = 0; i < count; i++) {
            double angle = (Math.PI * 2.0D) * i / count;
            double x = center.x + Math.cos(angle) * radius;
            double z = center.z + Math.sin(angle) * radius;
            BlockPos pos = BlockPos.containing(x, boss.getY() - 0.5D, z);
            var state = level.getBlockState(pos);
            if (state.isAir()) {
                pos = pos.below();
                state = level.getBlockState(pos);
            }
            if (!state.isAir()) {
                var option = new BlockParticleOption(ParticleTypes.BLOCK, state);
                level.sendParticles(option, x, boss.getY() + 0.1D, z, 2, 0.05D, 0.2D, 0.05D, 0.12D);
            }
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
}
