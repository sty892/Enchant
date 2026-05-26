package me.guardian.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

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
                new BombTrapsAttack()
        };
    }

    public void tick(ServerLevel level) {
        for (Attack attack : attacks) {
            attack.tickCooldown();
        }
        if (runningAttack != null) {
            if (activeTarget() == null) {
                runningAttack = null;
                globalDelay = 20;
                return;
            }
            boss.getNavigation().stop();
            if (runningAttack.tick(level)) {
                runningAttack = null;
                globalDelay = 26;
            }
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
            // Close enough to target and inside home — stop any leftover pathing
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
        for (Attack attack : attacks) {
            if (!attack.id().equals(attackId) || !attack.canStart(level)) {
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
            return cooldown <= 0
                    && activeTarget() != null
                    && canUse(level);
        }

        final void startCooldown() {
            cooldown = cooldownTicks();
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
            return activeTarget() != null;
        }

        @Override
        RunningAttack start(ServerLevel level) {
            boss.swing(hand);
            boss.triggerAttackAnimation(animation);
            return new TimedAttack(34) {
                @Override
                protected void onTick(ServerLevel level, int tick) {
                    if (tick == 22) {
                        Vec3 center = boss.position();
                        Vec3 look = boss.getLookAngle().horizontal().normalize();
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
                            
                            double distToHand = horizontalDistance(living.position(), handPos);
                            float finalDamage;
                            if (distToHand <= 1.5D) {
                                finalDamage = closeDamage;
                            } else {
                                finalDamage = waveDamage;
                            }

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
                    } else if (tick == 24) {
                        radialBlockWaveRing(level, boss.position(), 3.5D, 24);
                    } else if (tick == 26) {
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
            return activeTarget() != null;
        }

        @Override
        RunningAttack start(ServerLevel level) {
            boss.swing(InteractionHand.MAIN_HAND);
            boss.swing(InteractionHand.OFF_HAND);
            boss.triggerAttackAnimation("attack_both");
            return new TimedAttack(44) {
                @Override
                protected void onTick(ServerLevel level, int tick) {
                    if (tick == 32) {
                        directionalImpact(level, 7.5D, 12.0F, 1.05D, 0.45D);
                        radialBlockWaveRing(level, boss.position(), 2.0D, 16);

                        if (boss.getRandom().nextFloat() < 0.5F) {
                            LivingEntity target = activeTarget();
                            if (target != null) {
                                BlockPos targetBlockPos = target.blockPosition();
                                BlockPos ceilingPos = findCeiling(level, targetBlockPos);
                                if (ceilingPos != null) {
                                    net.minecraft.world.level.block.state.BlockState ceilingState = level.getBlockState(ceilingPos);
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
                    } else if (tick == 34) {
                        radialBlockWaveRing(level, boss.position(), 4.5D, 32);
                    } else if (tick == 36) {
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
            return target != null && boss.distanceToSqr(target) <= 24.0D * 24.0D;
        }

        @Override
        RunningAttack start(ServerLevel level) {
            Vec3 start = boss.position();
            Vec3 dir = boss.getLookAngle().horizontal();
            if (dir.lengthSqr() < 0.0001D) {
                dir = new Vec3(0.0D, 0.0D, 1.0D);
            }
            Vec3 lineEnd = start.add(dir.normalize().scale(14.0D));

            boss.swing(InteractionHand.MAIN_HAND);
            boss.swing(InteractionHand.OFF_HAND);
            boss.triggerAttackAnimation("attack_hands_slam");
            return new TimedAttack(50) {
                @Override
                protected void onTick(ServerLevel level, int tick) {
                    if (tick >= 15 && tick < 31 && tick % 2 == 1) {
                        renderSlamLine(level, lineEnd, false);
                    }
                    if (tick == 34) {
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
            return activeTarget() != null;
        }

        @Override
        RunningAttack start(ServerLevel level) {
            boss.triggerAttackAnimation("stamTopTopTop");
            return new TimedAttack(54) {
                @Override
                protected void onTick(ServerLevel level, int tick) {
                    if (tick == 12 || tick == 22 || tick == 32) {
                        telegraphCircle(level, 7.5D, 32);
                    }
                    if (tick == 38) {
                        radialImpact(level, 7.5D, 10.0F, 1.25D, 0.45D);
                        radialBlockWaveRing(level, boss.position(), 1.5D, 16);
                    } else if (tick == 40) {
                        radialBlockWaveRing(level, boss.position(), 3.5D, 24);
                    } else if (tick == 42) {
                        radialBlockWaveRing(level, boss.position(), 5.5D, 32);
                    } else if (tick == 44) {
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

    private void telegraphPlayerStomp(ServerLevel level, ServerPlayer player) {
        Vec3 center = player.position();
        level.sendParticles(TELEGRAPH_DUST, center.x, center.y + 0.1D, center.z,
                18, 1.15D, 0.03D, 1.15D, 0.0D);
        level.sendParticles(DIRT_PARTICLE, center.x, center.y + 0.1D, center.z,
                18, 0.75D, 0.18D, 0.75D, 0.05D);
    }

    private Vec3 currentTargetPosition(double fallbackDistance) {
        LivingEntity target = activeTarget();
        if (target != null) {
            return target.position();
        }
        Vec3 look = boss.getLookAngle().horizontal();
        if (look.lengthSqr() < 0.0001D) {
            look = new Vec3(0.0D, 0.0D, 1.0D);
        }
        return boss.position().add(look.normalize().scale(fallbackDistance));
    }

    private LivingEntity activeTarget() {
        LivingEntity target = boss.getTarget();
        return target != null && target.isAlive() ? target : null;
    }

    private List<ServerPlayer> aggroedPlayers(ServerLevel level, double radius) {
        return boss.getThreatTable().topAggroedPlayers(boss, level, radius, 8);
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

    private void directionalImpact(ServerLevel level, double radius, float damage, double knockback, double yVelocity) {
        Vec3 center = boss.position();
        Vec3 look = boss.getLookAngle().horizontal().normalize();

        Vec3 gustPos = center.add(look.scale(1.5D));
        level.sendParticles(ParticleTypes.GUST_EMITTER_LARGE, gustPos.x, gustPos.y + 0.25D, gustPos.z,
                1, 0.05D, 0.05D, 0.05D, 0.0D);

        boolean hit = false;
        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, boss.getBoundingBox().inflate(radius, 2.0D, radius))) {
            if (living == boss || !living.isAlive() || horizontalDistance(living.position(), center) > radius) {
                continue;
            }
            Vec3 toTarget = living.position().subtract(center).horizontal().normalize();
            double dot = look.dot(toTarget);
            if (dot < 0.35D) {
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
            return activeTarget() != null;
        }

        @Override
        RunningAttack start(ServerLevel level) {
            boss.triggerAttackAnimation("attack_both");
            return new TimedAttack(44) {
                @Override
                protected void onTick(ServerLevel level, int tick) {
                    if (tick == 20) {
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

    private BlockPos findCeiling(ServerLevel level, BlockPos startPos) {
        BlockPos.MutableBlockPos pos = startPos.mutable();
        for (int y = 0; y < 30; y++) {
            pos.move(0, 1, 0);
            net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
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
}
