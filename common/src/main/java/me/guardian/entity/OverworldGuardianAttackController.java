package me.guardian.entity;

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
                new StompPlayersAttack()
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
                    && boss.getBossPhase().id() == phase
                    && boss.getHiddenStageStep() == stageStep
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
                    if (tick >= 12 && tick < 20 && tick % 2 == 0) {
                        telegraphCircle(level, 2.8D + tick * 0.12D, 16);
                    }
                    if (tick == 22) {
                        radialImpact(level, 5.5D, 9.0F, 0.55D, 0.25D);
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
                    if (tick >= 14 && tick < 30 && tick % 3 == 0) {
                        telegraphCircle(level, 3.5D + tick * 0.13D, 20);
                    }
                    if (tick == 32) {
                        radialImpact(level, 7.5D, 12.0F, 1.05D, 0.45D);
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
            Vec3 target = currentTargetPosition(14.0D);
            boss.swing(InteractionHand.MAIN_HAND);
            boss.swing(InteractionHand.OFF_HAND);
            boss.triggerAttackAnimation("attack_hands_slam");
            return new TimedAttack(50) {
                @Override
                protected void onTick(ServerLevel level, int tick) {
                    if (tick >= 15 && tick < 31 && tick % 2 == 1) {
                        renderSlamLine(level, target, false);
                    }
                    if (tick == 34) {
                        renderSlamLine(level, target, true);
                        damageLine(level, target, 13.0F);
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
            return activeTarget() != null && !aggroedPlayers(level, 22.0D).isEmpty();
        }

        @Override
        RunningAttack start(ServerLevel level) {
            boss.triggerAttackAnimation("stamTopTopTop");
            return new TimedAttack(54) {
                @Override
                protected void onTick(ServerLevel level, int tick) {
                    if (tick == 12 || tick == 22 || tick == 32) {
                        for (ServerPlayer player : aggroedPlayers(level, 22.0D)) {
                            telegraphPlayerStomp(level, player);
                        }
                    }
                    if (tick == 38) {
                        boolean hit = false;
                        for (ServerPlayer player : aggroedPlayers(level, 22.0D)) {
                            if (horizontalDistance(player.position(), boss.position()) > 24.0D) {
                                continue;
                            }
                            level.sendParticles(ParticleTypes.GUST_EMITTER_LARGE,
                                    player.getX(), player.getY() + 0.15D, player.getZ(),
                                    1, 0.05D, 0.05D, 0.05D, 0.0D);
                            if (player.hurtServer(level, boss.damageSources().mobAttack(boss), 10.0F)) {
                                hit = true;
                            }
                            player.setDeltaMovement(player.getDeltaMovement().x, 0.95D, player.getDeltaMovement().z);
                        }
                        if (hit) {
                            boss.recordSuccessfulHit();
                        }
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
        double length = Math.min(18.0D, direction.length());
        for (double distance = 1.7D; distance <= length; distance += heavy ? 0.55D : 1.05D) {
            Vec3 point = start.add(step.scale(distance));
            level.sendParticles(heavy ? STONE_PARTICLE : TELEGRAPH_DUST,
                    point.x, boss.getY() + (heavy ? 0.2D : 0.1D), point.z,
                    heavy ? 8 : 1, 0.18D, heavy ? 0.35D : 0.02D, 0.18D, heavy ? 0.08D : 0.0D);
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
}
