package me.guardian.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

public final class OverworldGuardianAttackController {
    private static final double MELEE_RANGE = 4.5D;
    private static final double MELEE_RANGE_SQR = MELEE_RANGE * MELEE_RANGE;
    private static final BlockParticleOption DIRT_PARTICLE = new BlockParticleOption(
            ParticleTypes.DUST_PILLAR,
            Blocks.DIRT.defaultBlockState()
    );
    private static final DustParticleOptions WHITE_DUST = new DustParticleOptions(0xF4FFFF, 1.2F);

    private final OverworldGuardianEntity boss;
    private final Attack[] attacks;
    private RunningAttack runningAttack;
    private int globalDelay = 20;

    public OverworldGuardianAttackController(OverworldGuardianEntity boss) {
        this.boss = boss;
        this.attacks = new Attack[]{
                new FissureAttack(),
                new RockfallAttack(),
                new ShockwaveAttack(),
                new MeleeAttack()
        };
    }

    public void tick(ServerLevel level) {
        for (Attack attack : attacks) {
            attack.tickCooldown();
        }
        if (runningAttack != null) {
            boss.getNavigation().stop();
            if (runningAttack.tick(level)) {
                runningAttack = null;
                globalDelay = Math.max(8, 18 - boss.getBossPhase().id() * 3);
            }
            return;
        }

        LivingEntity target = boss.getTarget();
        if (target != null && target.isAlive()) {
            boss.getLookControl().setLookAt(target, 30.0F, 30.0F);
            if (boss.distanceToSqr(target) > MELEE_RANGE_SQR) {
                boss.getNavigation().moveTo(target, boss.getBossPhase().id() >= 3 ? 1.18D : 1.0D);
            }
        }

        if (globalDelay > 0) {
            globalDelay--;
            return;
        }

        Attack selected = selectAttack(level);
        if (selected == null) {
            globalDelay = 5;
            return;
        }
        selected.startCooldown(boss);
        runningAttack = selected.start(level);
    }

    private Attack selectAttack(ServerLevel level) {
        int totalWeight = 0;
        for (Attack attack : attacks) {
            if (attack.canStart(boss, level)) {
                totalWeight += attack.weight(boss);
            }
        }
        if (totalWeight <= 0) {
            return null;
        }

        int roll = boss.getRandom().nextInt(totalWeight);
        for (Attack attack : attacks) {
            if (!attack.canStart(boss, level)) {
                continue;
            }
            roll -= attack.weight(boss);
            if (roll < 0) {
                return attack;
            }
        }
        return null;
    }

    private abstract static class Attack {
        private int cooldown;

        final void tickCooldown() {
            if (cooldown > 0) {
                cooldown--;
            }
        }

        final boolean canStart(OverworldGuardianEntity boss, ServerLevel level) {
            return cooldown <= 0 && boss.getBossPhase().id() >= minPhase() && canUse(boss, level);
        }

        final void startCooldown(OverworldGuardianEntity boss) {
            cooldown = cooldownTicks(boss);
        }

        int minPhase() {
            return 1;
        }

        int weight(OverworldGuardianEntity boss) {
            return 1;
        }

        abstract boolean canUse(OverworldGuardianEntity boss, ServerLevel level);

        abstract RunningAttack start(ServerLevel level);

        abstract int cooldownTicks(OverworldGuardianEntity boss);
    }

    private interface RunningAttack {
        boolean tick(ServerLevel level);
    }

    private final class MeleeAttack extends Attack {
        @Override
        int weight(OverworldGuardianEntity boss) {
            return boss.getBossPhase().id() == 1 ? 8 : 4;
        }

        @Override
        boolean canUse(OverworldGuardianEntity boss, ServerLevel level) {
            LivingEntity target = boss.getTarget();
            return target != null && target.isAlive() && boss.distanceToSqr(target) <= MELEE_RANGE_SQR;
        }

        @Override
        RunningAttack start(ServerLevel level) {
            LivingEntity target = boss.getTarget();
            boss.swing(InteractionHand.MAIN_HAND);
            boss.triggerAttackAnimation("melee");
            return new TimedAttack(20) {
                @Override
                protected void onTick(ServerLevel level, int tick) {
                    if (tick != 11 || target == null || !target.isAlive() || boss.distanceToSqr(target) > 5.6D * 5.6D) {
                        return;
                    }
                    float damage = switch (boss.getBossPhase()) {
                        case ONE -> 12.0F;
                        case TWO -> 15.0F;
                        case THREE -> 19.0F;
                    };
                    target.hurtServer(level, boss.damageSources().mobAttack(boss), damage);
                    target.knockback(0.65D, boss.getX() - target.getX(), boss.getZ() - target.getZ());
                }
            };
        }

        @Override
        int cooldownTicks(OverworldGuardianEntity boss) {
            return switch (boss.getBossPhase()) {
                case ONE -> 28;
                case TWO -> 24;
                case THREE -> 18;
            };
        }
    }

    private final class RockfallAttack extends Attack {
        @Override
        int minPhase() {
            return 2;
        }

        @Override
        int weight(OverworldGuardianEntity boss) {
            return boss.getBossPhase().id() == 3 ? 5 : 4;
        }

        @Override
        boolean canUse(OverworldGuardianEntity boss, ServerLevel level) {
            return !boss.getThreatTable().topAggroedPlayers(boss, level, 16.0D, 3).isEmpty();
        }

        @Override
        RunningAttack start(ServerLevel level) {
            boss.triggerAttackAnimation("rockfall");
            return new TimedAttack(38) {
                @Override
                protected void onTick(ServerLevel level, int tick) {
                    if (tick == 14) {
                        for (ServerPlayer player : boss.getThreatTable().topAggroedPlayers(boss, level, 16.0D, maxTargets())) {
                            level.sendParticles(DIRT_PARTICLE, player.getX(), player.getY() + 6.0D, player.getZ(),
                                    16, 0.8D, 0.5D, 0.8D, 0.03D);
                        }
                    }
                    if (tick == 24) {
                        for (ServerPlayer player : boss.getThreatTable().topAggroedPlayers(boss, level, 16.0D, maxTargets())) {
                            spawnFallingDirt(level, player.blockPosition(), boss.getBossPhase().id() == 3);
                        }
                    }
                }
            };
        }

        @Override
        int cooldownTicks(OverworldGuardianEntity boss) {
            return boss.getBossPhase().id() == 3 ? 90 : 130;
        }

        private int maxTargets() {
            return boss.getBossPhase().id() == 3 ? 3 : 2;
        }
    }

    private final class ShockwaveAttack extends Attack {
        @Override
        int minPhase() {
            return 2;
        }

        @Override
        int weight(OverworldGuardianEntity boss) {
            return 4;
        }

        @Override
        boolean canUse(OverworldGuardianEntity boss, ServerLevel level) {
            return !boss.getThreatTable().topAggroedPlayers(boss, level, 13.0D, 4).isEmpty();
        }

        @Override
        RunningAttack start(ServerLevel level) {
            boss.triggerAttackAnimation("shockwave");
            return new TimedAttack(boss.getBossPhase().id() == 3 ? 50 : 42) {
                @Override
                protected void onTick(ServerLevel level, int tick) {
                    if (tick < 16) {
                        telegraphCircle(level, 3.0D + tick * 0.15D, 10);
                        return;
                    }
                    double radius = 2.5D + (tick - 16) * (boss.getBossPhase().id() == 3 ? 0.55D : 0.45D);
                    shockwaveRing(level, radius);
                    if (boss.getBossPhase().id() == 3 && tick >= 28) {
                        shockwaveRing(level, radius - 3.0D);
                    }
                }
            };
        }

        @Override
        int cooldownTicks(OverworldGuardianEntity boss) {
            return boss.getBossPhase().id() == 3 ? 70 : 105;
        }
    }

    private final class FissureAttack extends Attack {
        @Override
        int minPhase() {
            return 3;
        }

        @Override
        int weight(OverworldGuardianEntity boss) {
            return 5;
        }

        @Override
        boolean canUse(OverworldGuardianEntity boss, ServerLevel level) {
            return !boss.getThreatTable().topAggroedPlayers(boss, level, 24.0D, 2).isEmpty();
        }

        @Override
        RunningAttack start(ServerLevel level) {
            List<UUID> targetIds = boss.getThreatTable().topAggroedPlayers(boss, level, 24.0D, 2)
                    .stream()
                    .map(ServerPlayer::getUUID)
                    .toList();
            boss.triggerAttackAnimation("fissure");
            return new TimedAttack(42) {
                @Override
                protected void onTick(ServerLevel level, int tick) {
                    if (tick < 18 && tick % 3 == 0) {
                        for (UUID targetId : targetIds) {
                            ServerPlayer target = findPlayer(level, targetId);
                            if (target != null) {
                                renderFissureLine(level, target.position(), false);
                            }
                        }
                    }
                    if (tick == 23 || tick == 31) {
                        for (UUID targetId : targetIds) {
                            ServerPlayer target = findPlayer(level, targetId);
                            if (target != null) {
                                renderFissureLine(level, target.position(), true);
                                damageFissure(level, target.position());
                            }
                        }
                    }
                }
            };
        }

        @Override
        int cooldownTicks(OverworldGuardianEntity boss) {
            return 110;
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

    private void spawnFallingDirt(ServerLevel level, BlockPos targetPos, boolean cluster) {
        spawnFallingDirtAt(level, targetPos);
        if (!cluster) {
            return;
        }
        spawnFallingDirtAt(level, targetPos.north());
        spawnFallingDirtAt(level, targetPos.east());
    }

    private void spawnFallingDirtAt(ServerLevel level, BlockPos targetPos) {
        BlockPos start = targetPos.above(8);
        for (int i = 0; i < 6 && !level.getBlockState(start).isAir(); i++) {
            start = start.above();
        }
        if (!level.getBlockState(start).isAir()) {
            return;
        }
        FallingBlockEntity fallingBlock = FallingBlockEntity.fall(level, start, Blocks.DIRT.defaultBlockState());
        fallingBlock.setHurtsEntities(4.0F + boss.getBossPhase().id() * 1.5F, 24);
    }

    private void telegraphCircle(ServerLevel level, double radius, int points) {
        Vec3 center = boss.position();
        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2.0D) * i / points;
            level.sendParticles(WHITE_DUST,
                    center.x + Math.cos(angle) * radius,
                    boss.getY() + 0.15D,
                    center.z + Math.sin(angle) * radius,
                    1, 0.02D, 0.01D, 0.02D, 0.0D);
        }
    }

    private void shockwaveRing(ServerLevel level, double radius) {
        if (radius <= 0.0D) {
            return;
        }
        telegraphCircle(level, radius, Math.max(16, (int) (radius * 8.0D)));
        double inner = radius - 0.75D;
        double outer = radius + 0.75D;
        for (ServerPlayer player : boss.getThreatTable().topAggroedPlayers(boss, level, 18.0D, 8)) {
            double horizontalDistance = horizontalDistance(player.position(), boss.position());
            if (horizontalDistance < inner || horizontalDistance > outer || !player.onGround()) {
                continue;
            }
            player.hurtServer(level, boss.damageSources().mobAttack(boss), boss.getBossPhase().id() == 3 ? 12.0F : 8.0F);
            Vec3 away = player.position().subtract(boss.position()).horizontal();
            if (away.lengthSqr() > 0.0001D) {
                player.setDeltaMovement(away.normalize().scale(0.9D).add(0.0D, 0.45D, 0.0D));
            }
        }
    }

    private void renderFissureLine(ServerLevel level, Vec3 target, boolean heavy) {
        Vec3 start = boss.position();
        Vec3 direction = target.subtract(start).horizontal();
        if (direction.lengthSqr() < 0.0001D) {
            return;
        }
        Vec3 step = direction.normalize();
        double length = Math.min(24.0D, direction.length());
        for (double distance = 1.5D; distance <= length; distance += heavy ? 0.65D : 1.2D) {
            Vec3 point = start.add(step.scale(distance));
            level.sendParticles(heavy ? DIRT_PARTICLE : WHITE_DUST, point.x, boss.getY() + 0.1D, point.z,
                    heavy ? 4 : 1, 0.12D, 0.05D, 0.12D, heavy ? 0.04D : 0.0D);
        }
    }

    private void damageFissure(ServerLevel level, Vec3 target) {
        Vec3 start = boss.position();
        for (ServerPlayer player : boss.getThreatTable().topAggroedPlayers(boss, level, 24.0D, 8)) {
            if (distanceToSegment2d(player.position(), start, target) > 1.35D) {
                continue;
            }
            player.hurtServer(level, boss.damageSources().mobAttack(boss), 14.0F);
            Vec3 away = player.position().subtract(start).horizontal();
            if (away.lengthSqr() > 0.0001D) {
                player.setDeltaMovement(away.normalize().scale(0.7D).add(0.0D, 0.35D, 0.0D));
            }
        }
    }

    private ServerPlayer findPlayer(ServerLevel level, UUID id) {
        for (ServerPlayer player : level.players()) {
            if (player.getUUID().equals(id) && player.isAlive()) {
                return player;
            }
        }
        return null;
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
