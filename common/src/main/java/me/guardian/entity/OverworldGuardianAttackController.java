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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class OverworldGuardianAttackController {
    private static final double MELEE_RANGE = 4.5D;
    private static final double MELEE_RANGE_SQR = MELEE_RANGE * MELEE_RANGE;
    private static final BlockParticleOption DIRT_PARTICLE = new BlockParticleOption(
            ParticleTypes.DUST_PILLAR,
            Blocks.DIRT.defaultBlockState()
    );
    private static final BlockParticleOption STONE_PARTICLE = new BlockParticleOption(
            ParticleTypes.DUST_PILLAR,
            Blocks.STONE.defaultBlockState()
    );
    private static final DustParticleOptions WHITE_DUST = new DustParticleOptions(0xF4FFFF, 1.2F);

    private final OverworldGuardianEntity boss;
    private final Attack[] attacks;
    private final Map<UUID, Integer> closeShieldTicks = new HashMap<>();
    private RunningAttack runningAttack;
    private int globalDelay = 20;

    public OverworldGuardianAttackController(OverworldGuardianEntity boss) {
        this.boss = boss;
        this.attacks = new Attack[]{
                new AntiShieldAttack(),
                new CounterLeapAttack(),
                new FissureAttack(),
                new RockfallAttack(),
                new ShockwaveAttack(),
                new MeleeAttack()
        };
    }

    public void tick(ServerLevel level) {
        tickCloseShieldPressure(level);
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
        ServerPlayer shieldTarget = findShieldPressureTarget(level, 14.0D);
        if (shieldTarget != null && !boss.shouldReturnTowardHome()) {
            target = shieldTarget;
            boss.setTarget(shieldTarget);
        }
        if (target != null && target.isAlive()) {
            boss.getLookControl().setLookAt(target, 30.0F, 30.0F);
            if (boss.shouldReturnTowardHome()) {
                Vec3 home = boss.homeCenter();
                boss.getNavigation().moveTo(home.x, home.y, home.z, 1.05D);
            } else if (boss.distanceToSqr(target) > MELEE_RANGE_SQR) {
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
        for (Attack attack : attacks) {
            if (attack.highPriority() && attack.canStart(boss, level)) {
                return attack;
            }
        }

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

        boolean highPriority() {
            return false;
        }

        abstract boolean canUse(OverworldGuardianEntity boss, ServerLevel level);

        abstract RunningAttack start(ServerLevel level);

        abstract int cooldownTicks(OverworldGuardianEntity boss);
    }

    private interface RunningAttack {
        boolean tick(ServerLevel level);
    }

    private final class AntiShieldAttack extends Attack {
        @Override
        boolean highPriority() {
            return true;
        }

        @Override
        int weight(OverworldGuardianEntity boss) {
            return 8;
        }

        @Override
        boolean canUse(OverworldGuardianEntity boss, ServerLevel level) {
            return findShieldPunishTarget(level) != null;
        }

        @Override
        RunningAttack start(ServerLevel level) {
            UUID targetId = findShieldPunishTarget(level);
            boss.swing(InteractionHand.MAIN_HAND);
            boss.swing(InteractionHand.OFF_HAND);
            boss.triggerAttackAnimation("shockwave");
            return new TimedAttack(26) {
                @Override
                protected void onTick(ServerLevel level, int tick) {
                    ServerPlayer target = targetId == null ? null : findPlayer(level, targetId);
                    if (target == null) {
                        return;
                    }
                    boss.getLookControl().setLookAt(target, 30.0F, 30.0F);
                    if (boss.distanceToSqr(target) > 4.5D * 4.5D) {
                        boss.getNavigation().moveTo(target, 1.22D);
                    }
                    if ((tick != 8 && tick != 16 && tick != 24) || boss.distanceToSqr(target) > 6.5D * 6.5D) {
                        return;
                    }
                    punishShield(level, target);
                    closeShieldTicks.remove(target.getUUID());
                }
            };
        }

        @Override
        int cooldownTicks(OverworldGuardianEntity boss) {
            return boss.getBossPhase().id() == 3 ? 10 : 14;
        }
    }

    private final class CounterLeapAttack extends Attack {
        @Override
        int minPhase() {
            return 2;
        }

        @Override
        boolean highPriority() {
            return true;
        }

        @Override
        int weight(OverworldGuardianEntity boss) {
            return 10;
        }

        @Override
        boolean canUse(OverworldGuardianEntity boss, ServerLevel level) {
            LivingEntity target = boss.peekCounterTarget(level);
            return target != null && target.isAlive() && boss.distanceToSqr(target) <= 24.0D * 24.0D;
        }

        @Override
        RunningAttack start(ServerLevel level) {
            LivingEntity target = boss.consumeCounterTarget(level);
            boss.triggerAttackAnimation("phase_shift");
            boss.setShiftKeyDown(true);
            return new TimedAttack(36) {
                private boolean landed;

                @Override
                protected void onTick(ServerLevel level, int tick) {
                    if (target == null || !target.isAlive()) {
                        boss.setShiftKeyDown(false);
                        return;
                    }
                    boss.getLookControl().setLookAt(target, 35.0F, 35.0F);
                    if (tick <= 12) {
                        boss.setShiftKeyDown(true);
                        level.sendParticles(DIRT_PARTICLE, boss.getX(), boss.getY() + 0.1D, boss.getZ(),
                                8, 1.0D, 0.1D, 1.0D, 0.02D);
                        return;
                    }
                    if (tick == 13) {
                        boss.setShiftKeyDown(false);
                        Vec3 direction = target.position().subtract(boss.position()).horizontal();
                        if (direction.lengthSqr() < 0.0001D) {
                            direction = boss.getLookAngle().horizontal();
                        }
                        boss.setDeltaMovement(direction.normalize().scale(1.55D).add(0.0D, 0.82D, 0.0D));
                        boss.hurtMarked = true;
                    }
                    if (!landed && (tick >= 19 && (boss.onGround() || boss.distanceToSqr(target) <= 3.2D * 3.2D) || tick == 30)) {
                        landed = true;
                        smashImpact(level, target.position());
                    }
                }
            };
        }

        @Override
        int cooldownTicks(OverworldGuardianEntity boss) {
            return boss.getBossPhase().id() == 3 ? 65 : 85;
        }
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
                    boss.recordSuccessfulHit();
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
                            double height = boss.getBossPhase().id() == 3 ? 10.0D : 6.0D;
                            level.sendParticles(DIRT_PARTICLE, player.getX(), player.getY() + height, player.getZ(),
                                    24, 1.4D, 0.7D, 1.4D, 0.03D);
                        }
                    }
                    if (tick == 24) {
                        for (ServerPlayer player : boss.getThreatTable().topAggroedPlayers(boss, level, 16.0D, maxTargets())) {
                            spawnRockfallCluster(level, player.blockPosition());
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
            List<Vec3> targetPositions = boss.getThreatTable().topAggroedPlayers(boss, level, 24.0D, 2)
                    .stream()
                    .map(ServerPlayer::position)
                    .toList();
            boss.triggerAttackAnimation("fissure");
            return new TimedAttack(42) {
                @Override
                protected void onTick(ServerLevel level, int tick) {
                    if (tick < 18 && tick % 3 == 0) {
                        for (Vec3 targetPosition : targetPositions) {
                            renderFissureLine(level, targetPosition, false);
                        }
                    }
                    if (tick == 23 || tick == 31) {
                        for (Vec3 targetPosition : targetPositions) {
                            renderFissureLine(level, targetPosition, true);
                            damageFissure(level, targetPosition);
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

    private void tickCloseShieldPressure(ServerLevel level) {
        closeShieldTicks.entrySet().removeIf(entry -> findPlayer(level, entry.getKey()) == null);
        for (ServerPlayer player : boss.getThreatTable().topAggroedPlayers(boss, level, 14.0D, 8)) {
            if (isHoldingOrBlockingWithShield(player)) {
                closeShieldTicks.merge(player.getUUID(), boss.distanceToSqr(player) <= 6.5D * 6.5D ? 5 : 2, Integer::sum);
            } else {
                closeShieldTicks.computeIfPresent(player.getUUID(), (uuid, ticks) -> ticks > 1 ? ticks - 2 : null);
            }
        }
    }

    private ServerPlayer findShieldPressureTarget(ServerLevel level, double radius) {
        ServerPlayer best = null;
        int bestTicks = 0;
        for (ServerPlayer player : boss.getThreatTable().topAggroedPlayers(boss, level, radius, 8)) {
            int ticks = closeShieldTicks.getOrDefault(player.getUUID(), 0);
            if (ticks > bestTicks && isHoldingOrBlockingWithShield(player)) {
                best = player;
                bestTicks = ticks;
            }
        }
        return best;
    }

    private UUID findShieldPunishTarget(ServerLevel level) {
        UUID best = null;
        int bestTicks = 0;
        for (ServerPlayer player : boss.getThreatTable().topAggroedPlayers(boss, level, 12.0D, 8)) {
            int ticks = closeShieldTicks.getOrDefault(player.getUUID(), 0);
            if (ticks >= 8 && ticks > bestTicks && isHoldingOrBlockingWithShield(player)) {
                best = player.getUUID();
                bestTicks = ticks;
            }
        }
        return best;
    }

    private void punishShield(ServerLevel level, ServerPlayer target) {
        ItemStack shield = target.getItemBlockingWith();
        if (!shield.is(Items.SHIELD)) {
            shield = target.getMainHandItem().is(Items.SHIELD) ? target.getMainHandItem() : target.getOffhandItem();
        }
        if (shield.is(Items.SHIELD)) {
            target.stopUsingItem();
            target.getCooldowns().addCooldown(shield, boss.getBossPhase().id() == 3 ? 180 : 140);
            shield.hurtAndBreak(boss.getBossPhase().id() == 3 ? 120 : 90, level, target, item -> {
            });
        }
        target.hurtServer(level, boss.damageSources().mobAttack(boss), boss.getBossPhase().id() == 3 ? 24.0F : 18.0F);
        boss.recordSuccessfulHit();
        Vec3 away = target.position().subtract(boss.position()).horizontal();
        if (away.lengthSqr() < 0.0001D) {
            away = new Vec3(0.0D, 0.0D, 1.0D);
        }
        target.setDeltaMovement(away.normalize().scale(0.75D).add(0.0D, 1.1D, 0.0D));
    }

    private boolean isHoldingOrBlockingWithShield(ServerPlayer player) {
        return player.getItemBlockingWith().is(Items.SHIELD)
                || player.getMainHandItem().is(Items.SHIELD)
                || player.getOffhandItem().is(Items.SHIELD);
    }

    private void smashImpact(ServerLevel level, Vec3 center) {
        level.sendParticles(ParticleTypes.GUST_EMITTER_LARGE, center.x, center.y + 0.15D, center.z,
                1, 0.05D, 0.05D, 0.05D, 0.0D);
        level.sendParticles(STONE_PARTICLE, center.x, center.y + 0.05D, center.z,
                52, 2.2D, 0.25D, 2.2D, 0.12D);
        level.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y + 0.3D, center.z,
                2, 0.4D, 0.1D, 0.4D, 0.0D);
        boolean hit = false;
        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, boss.getBoundingBox().inflate(4.0D, 2.0D, 4.0D))) {
            if (living == boss || !living.isAlive() || horizontalDistance(living.position(), center) > 3.8D) {
                continue;
            }
            if (living.hurtServer(level, boss.damageSources().mobAttack(boss), boss.getBossPhase().id() == 3 ? 30.0F : 26.0F)) {
                hit = true;
            }
            Vec3 away = living.position().subtract(center).horizontal();
            if (away.lengthSqr() > 0.0001D) {
                living.setDeltaMovement(away.normalize().scale(1.15D).add(0.0D, 0.75D, 0.0D));
            }
        }
        if (hit) {
            boss.recordSuccessfulHit();
        }
    }

    private void spawnRockfallCluster(ServerLevel level, BlockPos targetPos) {
        boolean phaseThree = boss.getBossPhase().id() == 3;
        int count = phaseThree ? 8 + boss.getRandom().nextInt(3) : 5 + boss.getRandom().nextInt(2);
        int height = phaseThree ? 12 : 8;
        for (int i = 0; i < count; i++) {
            double angle = boss.getRandom().nextDouble() * Math.PI * 2.0D;
            double radius = boss.getRandom().nextDouble() * (phaseThree ? 2.6D : 2.0D);
            int x = targetPos.getX() + (int) Math.round(Math.cos(angle) * radius);
            int z = targetPos.getZ() + (int) Math.round(Math.sin(angle) * radius);
            BlockState state = phaseThree && i % 3 == 0 ? Blocks.STONE.defaultBlockState() : Blocks.DIRT.defaultBlockState();
            spawnFallingBlockAt(level, new BlockPos(x, targetPos.getY(), z), height + boss.getRandom().nextInt(3), state);
        }
    }

    private void spawnFallingBlockAt(ServerLevel level, BlockPos targetPos, int height, BlockState state) {
        BlockPos start = targetPos.above(height);
        for (int i = 0; i < 6 && !level.getBlockState(start).isAir(); i++) {
            start = start.above();
        }
        if (!level.getBlockState(start).isAir()) {
            return;
        }
        FallingBlockEntity fallingBlock = FallingBlockEntity.fall(level, start, state);
        boolean stone = state.is(Blocks.STONE);
        fallingBlock.setHurtsEntities(stone ? 9.0F : 6.0F + boss.getBossPhase().id(), stone ? 32 : 24);
        level.sendParticles(stone ? STONE_PARTICLE : DIRT_PARTICLE, start.getX() + 0.5D, start.getY(), start.getZ() + 0.5D,
                8, 0.25D, 0.2D, 0.25D, 0.03D);
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
            boss.recordSuccessfulHit();
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
            boss.recordSuccessfulHit();
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
