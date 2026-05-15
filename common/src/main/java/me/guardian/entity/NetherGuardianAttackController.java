package me.guardian.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class NetherGuardianAttackController {
    private static final double MELEE_RANGE = 4.7D;
    private static final double MELEE_RANGE_SQR = MELEE_RANGE * MELEE_RANGE;
    private static final DustParticleOptions TELEGRAPH_DUST = new DustParticleOptions(0xFF6A00, 1.4F);
    private static final DustParticleOptions SOUL_DUST = new DustParticleOptions(0x36D8FF, 1.5F);
    private static final DustParticleOptions BLACK_DUST = new DustParticleOptions(0x1A0A20, 1.7F);

    private final NetherGuardianEntity boss;
    private final Attack[] attacks;
    private final List<DangerZone> dangerZones = new ArrayList<>();
    private final Map<UUID, FlameBrand> flameBrands = new HashMap<>();
    private final List<UUID> aegisMinions = new ArrayList<>();
    private RunningAttack runningAttack;
    private int globalDelay = 30;
    private boolean aegisActive;

    public NetherGuardianAttackController(NetherGuardianEntity boss) {
        this.boss = boss;
        this.attacks = new Attack[]{
                new MinionAegisAttack(),
                new SoulGravityVortexAttack(),
                new DeathBeamsAttack(),
                new WhipGrabAttack(),
                new MeteorRainAttack(),
                new MoltenFissureAttack(),
                new MeleeAttack()
        };
    }

    public void tick(ServerLevel level) {
        tickFlameBrands(level);
        tickDangerZones(level);
        tickAegis(level);
        for (Attack attack : attacks) {
            attack.tickCooldown();
        }

        if (runningAttack != null) {
            boss.getNavigation().stop();
            if (runningAttack.tick(level)) {
                runningAttack = null;
                globalDelay = Math.max(8, 24 - boss.getBossPhase().id() * 5);
            }
            return;
        }

        LivingEntity target = boss.getTarget();
        if (target != null && target.isAlive()) {
            boss.getLookControl().setLookAt(target, 30.0F, 30.0F);
            if (boss.shouldReturnTowardHome()) {
                Vec3 home = boss.homeCenter();
                boss.getNavigation().moveTo(home.x, home.y, home.z, 1.15D);
            } else if (boss.distanceToSqr(target) > MELEE_RANGE_SQR) {
                boss.getNavigation().moveTo(target, boss.getBossPhase().id() == 3 ? 1.25D : 1.05D);
            }
        }

        if (globalDelay > 0) {
            globalDelay--;
            return;
        }

        Attack selected = selectAttack(level);
        if (selected == null) {
            globalDelay = 8;
            return;
        }
        selected.startCooldown(boss);
        runningAttack = selected.start(level);
    }

    public boolean forceAttack(ServerLevel level, String attackId) {
        for (Attack attack : attacks) {
            if (!attack.id().equals(attackId) || boss.getBossPhase().id() < attack.minPhase() || !attack.canUse(boss, level)) {
                continue;
            }
            attack.startCooldown(boss);
            runningAttack = attack.start(level);
            globalDelay = 0;
            return true;
        }
        return false;
    }

    public float modifyIncomingDamage(ServerLevel level, float amount) {
        tickAegis(level);
        if (aegisActive) {
            amount *= 0.01F;
        }
        if (boss.isVulnerable()) {
            amount *= 1.35F;
        }
        return amount;
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

        final boolean canStart(NetherGuardianEntity boss, ServerLevel level) {
            return cooldown <= 0 && boss.getBossPhase().id() >= minPhase() && canUse(boss, level);
        }

        final void startCooldown(NetherGuardianEntity boss) {
            cooldown = cooldownTicks(boss);
        }

        int minPhase() {
            return 1;
        }

        int weight(NetherGuardianEntity boss) {
            return 1;
        }

        boolean highPriority() {
            return false;
        }

        abstract boolean canUse(NetherGuardianEntity boss, ServerLevel level);

        abstract RunningAttack start(ServerLevel level);

        abstract int cooldownTicks(NetherGuardianEntity boss);

        abstract String id();
    }

    private interface RunningAttack {
        boolean tick(ServerLevel level);
    }

    private final class MeleeAttack extends Attack {
        @Override
        int weight(NetherGuardianEntity boss) {
            return boss.getBossPhase().id() == 1 ? 7 : 4;
        }

        @Override
        boolean canUse(NetherGuardianEntity boss, ServerLevel level) {
            LivingEntity target = boss.getTarget();
            return target != null && target.isAlive() && boss.distanceToSqr(target) <= MELEE_RANGE_SQR;
        }

        @Override
        RunningAttack start(ServerLevel level) {
            LivingEntity target = boss.getTarget();
            boss.swing(InteractionHand.MAIN_HAND);
            boss.triggerAttackAnimation("melee");
            return new TimedAttack(22) {
                @Override
                protected void onTick(ServerLevel level, int tick) {
                    if (target == null || !target.isAlive()) {
                        return;
                    }
                    boss.getLookControl().setLookAt(target, 35.0F, 35.0F);
                    if (tick != 11 || boss.distanceToSqr(target) > 5.8D * 5.8D) {
                        return;
                    }
                    float damage = switch (boss.getBossPhase()) {
                        case ONE -> 13.0F;
                        case TWO -> 16.0F;
                        case THREE -> 20.0F;
                    };
                    if (target.hurtServer(level, boss.damageSources().mobAttack(boss), damage) && target instanceof ServerPlayer player) {
                        if (boss.getBossPhase().id() >= 2) {
                            applyFlameBrand(player);
                        }
                    }
                    Vec3 away = target.position().subtract(boss.position()).horizontal();
                    if (away.lengthSqr() > 0.0001D) {
                        target.setDeltaMovement(away.normalize().scale(0.55D).add(0.0D, 0.25D, 0.0D));
                    }
                }
            };
        }

        @Override
        int cooldownTicks(NetherGuardianEntity boss) {
            return switch (boss.getBossPhase()) {
                case ONE -> 26;
                case TWO -> 22;
                case THREE -> 16;
            };
        }

        @Override
        String id() {
            return "melee";
        }
    }

    private final class MoltenFissureAttack extends Attack {
        @Override
        int weight(NetherGuardianEntity boss) {
            return boss.getBossPhase().id() == 1 ? 6 : 4;
        }

        @Override
        boolean canUse(NetherGuardianEntity boss, ServerLevel level) {
            return !boss.getThreatTable().topAggroedPlayers(boss, level, 14.0D, 6).isEmpty();
        }

        @Override
        RunningAttack start(ServerLevel level) {
            boss.triggerAttackAnimation("molten_fissure");
            return new TimedAttack(58) {
                @Override
                protected void onTick(ServerLevel level, int tick) {
                    if (tick <= 26) {
                        telegraphCircle(level, 3.0D + tick * 0.09D, boss.getBossPhase().id() == 3);
                        return;
                    }
                    if (tick == 30) {
                        moltenImpact(level);
                    }
                    if (tick >= 31 && tick <= 48 && tick % 3 == 0) {
                        moltenRing(level, 2.5D + (tick - 31) * 0.45D);
                    }
                }
            };
        }

        @Override
        int cooldownTicks(NetherGuardianEntity boss) {
            return switch (boss.getBossPhase()) {
                case ONE -> 92;
                case TWO -> 74;
                case THREE -> 58;
            };
        }

        @Override
        String id() {
            return "molten_fissure";
        }
    }

    private final class MeteorRainAttack extends Attack {
        @Override
        int weight(NetherGuardianEntity boss) {
            return boss.getBossPhase().id() == 3 ? 6 : 5;
        }

        @Override
        boolean canUse(NetherGuardianEntity boss, ServerLevel level) {
            return !boss.getThreatTable().topAggroedPlayers(boss, level, 30.0D, 5).isEmpty();
        }

        @Override
        RunningAttack start(ServerLevel level) {
            List<Meteor> meteors = createMeteors(level);
            boss.triggerAttackAnimation("meteor_rain");
            return new TimedAttack(96) {
                @Override
                protected void onTick(ServerLevel level, int tick) {
                    for (Meteor meteor : meteors) {
                        if (tick < meteor.landTick) {
                            groundMarker(level, meteor.pos, 1.4D, boss.getBossPhase().id() == 3);
                            if (tick % 5 == 0) {
                                level.sendParticles(ParticleTypes.LAVA, meteor.pos.getX() + 0.5D, meteor.pos.getY() + 7.0D, meteor.pos.getZ() + 0.5D,
                                        5, 0.25D, 0.3D, 0.25D, 0.02D);
                            }
                        } else if (tick == meteor.landTick) {
                            level.sendParticles(ParticleTypes.LAVA, meteor.pos.getX() + 0.5D, meteor.pos.getY() + 0.2D, meteor.pos.getZ() + 0.5D,
                                    20, 0.35D, 0.12D, 0.35D, 0.04D);
                        } else if (tick > meteor.landTick && tick < meteor.explodeTick) {
                            groundMarker(level, meteor.pos, 1.0D, boss.getBossPhase().id() == 3);
                        } else if (tick == meteor.explodeTick) {
                            explodeMeteor(level, meteor.pos);
                        }
                    }
                }
            };
        }

        @Override
        int cooldownTicks(NetherGuardianEntity boss) {
            return switch (boss.getBossPhase()) {
                case ONE -> 105;
                case TWO -> 88;
                case THREE -> 64;
            };
        }

        @Override
        String id() {
            return "meteor_rain";
        }
    }

    private final class WhipGrabAttack extends Attack {
        @Override
        int weight(NetherGuardianEntity boss) {
            return 7;
        }

        @Override
        boolean canUse(NetherGuardianEntity boss, ServerLevel level) {
            return boss.getThreatTable().farthestAggroedPlayer(boss, level, 10.0D, 32.0D) != null;
        }

        @Override
        RunningAttack start(ServerLevel level) {
            ServerPlayer target = boss.getThreatTable().farthestAggroedPlayer(boss, level, 10.0D, 32.0D);
            Vec3 start = boss.position().add(0.0D, 2.2D, 0.0D);
            Vec3 targetPoint = target == null ? start.add(boss.getLookAngle().scale(12.0D)) : target.position().add(0.0D, 1.0D, 0.0D);
            boss.triggerAttackAnimation("whip_grab");
            return new TimedAttack(38) {
                @Override
                protected void onTick(ServerLevel level, int tick) {
                    if (target == null || !target.isAlive()) {
                        return;
                    }
                    boss.getLookControl().setLookAt(target, 40.0F, 35.0F);
                    if (tick <= 18) {
                        particleLine(level, start, targetPoint, TELEGRAPH_DUST, 0.65D);
                        return;
                    }
                    if (tick == 24) {
                        particleLine(level, start, targetPoint, ParticleTypes.FLAME, 0.35D);
                        if (distanceToSegment2d(target.position().add(0.0D, 1.0D, 0.0D), start, targetPoint) <= 1.25D) {
                            target.hurtServer(level, boss.damageSources().mobAttack(boss), boss.getBossPhase().id() == 3 ? 9.0F : 6.0F);
                            Vec3 pull = boss.position().subtract(target.position()).horizontal();
                            if (pull.lengthSqr() > 0.0001D) {
                                target.setDeltaMovement(pull.normalize().scale(1.35D).add(0.0D, 0.25D, 0.0D));
                            }
                        }
                    }
                }
            };
        }

        @Override
        int cooldownTicks(NetherGuardianEntity boss) {
            return boss.getBossPhase().id() == 3 ? 52 : 70;
        }

        @Override
        String id() {
            return "whip_grab";
        }
    }

    private final class MinionAegisAttack extends Attack {
        @Override
        int minPhase() {
            return 2;
        }

        @Override
        boolean highPriority() {
            return true;
        }

        @Override
        int weight(NetherGuardianEntity boss) {
            return 8;
        }

        @Override
        boolean canUse(NetherGuardianEntity boss, ServerLevel level) {
            return !aegisActive && !boss.getThreatTable().topAggroedPlayers(boss, level, 32.0D, 1).isEmpty();
        }

        @Override
        RunningAttack start(ServerLevel level) {
            boss.triggerAttackAnimation("minion_aegis");
            return new TimedAttack(46) {
                @Override
                protected void onTick(ServerLevel level, int tick) {
                    boss.getNavigation().stop();
                    if (tick <= 28 && tick % 3 == 0) {
                        telegraphCircle(level, 7.0D + tick * 0.05D, true);
                    }
                    if (tick == 30) {
                        spawnAegisMinions(level);
                    }
                }
            };
        }

        @Override
        int cooldownTicks(NetherGuardianEntity boss) {
            return boss.getBossPhase().id() == 3 ? 280 : 360;
        }

        @Override
        String id() {
            return "minion_aegis";
        }
    }

    private final class SoulGravityVortexAttack extends Attack {
        @Override
        int minPhase() {
            return 3;
        }

        @Override
        int weight(NetherGuardianEntity boss) {
            return 8;
        }

        @Override
        boolean canUse(NetherGuardianEntity boss, ServerLevel level) {
            return !boss.getThreatTable().topAggroedPlayers(boss, level, 30.0D, 1).isEmpty();
        }

        @Override
        RunningAttack start(ServerLevel level) {
            boss.triggerAttackAnimation("soul_vortex");
            return new TimedAttack(128) {
                @Override
                protected void onTick(ServerLevel level, int tick) {
                    boss.getNavigation().stop();
                    if (tick <= 26) {
                        telegraphCircle(level, 6.5D, true);
                        return;
                    }
                    if (tick <= 108) {
                        pullPlayers(level);
                        spiralSoulProjectiles(level, tick);
                    }
                    if (tick == 109) {
                        boss.startVulnerabilityWindow(90);
                    }
                }
            };
        }

        @Override
        int cooldownTicks(NetherGuardianEntity boss) {
            return 220;
        }

        @Override
        String id() {
            return "soul_vortex";
        }
    }

    private final class DeathBeamsAttack extends Attack {
        @Override
        int minPhase() {
            return 3;
        }

        @Override
        int weight(NetherGuardianEntity boss) {
            return 6;
        }

        @Override
        boolean canUse(NetherGuardianEntity boss, ServerLevel level) {
            return !boss.getThreatTable().topAggroedPlayers(boss, level, 30.0D, 1).isEmpty();
        }

        @Override
        RunningAttack start(ServerLevel level) {
            boss.triggerAttackAnimation("death_beams");
            double baseAngle = boss.getRandom().nextDouble() * Math.PI * 2.0D;
            return new TimedAttack(112) {
                @Override
                protected void onTick(ServerLevel level, int tick) {
                    boss.getNavigation().stop();
                    double angle = baseAngle + Math.max(0, tick - 24) * 0.045D;
                    if (tick <= 24) {
                        renderBeam(level, angle, false);
                        renderBeam(level, angle + Math.PI, false);
                        return;
                    }
                    if (tick <= 84) {
                        renderBeam(level, angle, true);
                        renderBeam(level, angle + Math.PI, true);
                        if (tick % 8 == 0) {
                            leaveBeamFlames(angle);
                            leaveBeamFlames(angle + Math.PI);
                        }
                    }
                    if (tick == 85) {
                        boss.startVulnerabilityWindow(65);
                    }
                }
            };
        }

        @Override
        int cooldownTicks(NetherGuardianEntity boss) {
            return 175;
        }

        @Override
        String id() {
            return "death_beams";
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

    private void applyFlameBrand(ServerPlayer player) {
        FlameBrand brand = flameBrands.computeIfAbsent(player.getUUID(), ignored -> new FlameBrand());
        brand.stacks = Math.min(5, brand.stacks + 1);
        brand.expiresAt = boss.tickCount + 200;
    }

    private void tickFlameBrands(ServerLevel level) {
        Iterator<Map.Entry<UUID, FlameBrand>> iterator = flameBrands.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, FlameBrand> entry = iterator.next();
            ServerPlayer player = findPlayer(level, entry.getKey());
            FlameBrand brand = entry.getValue();
            if (player == null || boss.tickCount > brand.expiresAt) {
                iterator.remove();
                continue;
            }
            level.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 1.0D, player.getZ(),
                    brand.stacks, 0.25D, 0.45D, 0.25D, 0.01D);
            if (boss.tickCount % 40 == 0) {
                player.hurtServer(level, boss.damageSources().mobAttack(boss), 1.0F + brand.stacks);
            }
        }
    }

    private void tickDangerZones(ServerLevel level) {
        Iterator<DangerZone> iterator = dangerZones.iterator();
        while (iterator.hasNext()) {
            DangerZone zone = iterator.next();
            zone.ticks--;
            if (zone.ticks <= 0) {
                iterator.remove();
                continue;
            }
            if (zone.ticks % 4 == 0) {
                level.sendParticles(zone.soul ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME,
                        zone.center.x, zone.center.y + 0.05D, zone.center.z,
                        8, zone.radius * 0.35D, 0.05D, zone.radius * 0.35D, 0.01D);
            }
            if (zone.ticks % 20 == 0) {
                for (ServerPlayer player : boss.getThreatTable().topAggroedPlayers(boss, level, 32.0D, 8)) {
                    if (horizontalDistance(player.position(), zone.center) <= zone.radius) {
                        player.hurtServer(level, boss.damageSources().mobAttack(boss), zone.soul ? 5.0F : 4.0F);
                    }
                }
            }
        }
    }

    private void tickAegis(ServerLevel level) {
        if (aegisMinions.isEmpty()) {
            aegisActive = false;
            return;
        }
        boolean hadMinions = aegisActive;
        aegisMinions.removeIf(uuid -> {
            Entity entity = level.getEntity(uuid);
            return !(entity instanceof LivingEntity living) || !living.isAlive();
        });
        aegisActive = !aegisMinions.isEmpty();
        if (aegisActive) {
            for (UUID uuid : aegisMinions) {
                Entity entity = level.getEntity(uuid);
                if (entity != null && boss.tickCount % 3 == 0) {
                    particleLine(level, entity.position().add(0.0D, 1.0D, 0.0D), boss.position().add(0.0D, 2.0D, 0.0D), ParticleTypes.SOUL_FIRE_FLAME, 0.9D);
                }
            }
            level.sendParticles(ParticleTypes.SOUL, boss.getX(), boss.getY() + 2.0D, boss.getZ(), 8, 1.4D, 1.2D, 1.4D, 0.02D);
        } else if (hadMinions) {
            boss.startVulnerabilityWindow(80);
        }
    }

    private void spawnAegisMinions(ServerLevel level) {
        aegisMinions.clear();
        Vec3 center = boss.homeCenter();
        int count = boss.getBossPhase().id() == 3 ? 5 : 4;
        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2.0D * i / count;
            BlockPos pos = BlockPos.containing(center.x + Math.cos(angle) * 10.0D, boss.getY(), center.z + Math.sin(angle) * 10.0D);
            Entity entity = EntityType.BLAZE.create(level, EntitySpawnReason.MOB_SUMMONED);
            if (!(entity instanceof Mob mob)) {
                continue;
            }
            BlockPos spawnPos = findSurface(level, pos);
            mob.setPos(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D);
            LivingEntity target = boss.getTarget();
            if (target != null) {
                mob.setTarget(target);
            }
            level.addFreshEntity(mob);
            aegisMinions.add(mob.getUUID());
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, mob.getX(), mob.getY() + 1.0D, mob.getZ(),
                    20, 0.4D, 0.6D, 0.4D, 0.02D);
        }
        aegisActive = !aegisMinions.isEmpty();
    }

    private List<Meteor> createMeteors(ServerLevel level) {
        List<ServerPlayer> players = boss.getThreatTable().topAggroedPlayers(boss, level, 30.0D, 5);
        List<Meteor> meteors = new ArrayList<>();
        int count = switch (boss.getBossPhase()) {
            case ONE -> 5;
            case TWO -> 7;
            case THREE -> 9;
        };
        for (int i = 0; i < count; i++) {
            ServerPlayer player = players.get(i % players.size());
            double angle = boss.getRandom().nextDouble() * Math.PI * 2.0D;
            double radius = 1.5D + boss.getRandom().nextDouble() * 4.5D;
            BlockPos base = BlockPos.containing(player.getX() + Math.cos(angle) * radius, player.getY(), player.getZ() + Math.sin(angle) * radius);
            meteors.add(new Meteor(findSurface(level, base), 24 + i * 4, 46 + i * 4));
        }
        return meteors;
    }

    private void explodeMeteor(ServerLevel level, BlockPos pos) {
        Vec3 center = Vec3.atCenterOf(pos);
        level.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y + 0.2D, center.z, 1, 0.05D, 0.05D, 0.05D, 0.0D);
        level.sendParticles(ParticleTypes.LAVA, center.x, center.y + 0.1D, center.z, 20, 0.6D, 0.2D, 0.6D, 0.04D);
        for (ServerPlayer player : boss.getThreatTable().topAggroedPlayers(boss, level, 30.0D, 8)) {
            if (horizontalDistance(player.position(), center) <= 2.4D) {
                player.hurtServer(level, boss.damageSources().mobAttack(boss), boss.getBossPhase().id() == 3 ? 12.0F : 9.0F);
                Vec3 away = player.position().subtract(center).horizontal();
                if (away.lengthSqr() > 0.0001D) {
                    player.setDeltaMovement(away.normalize().scale(0.55D).add(0.0D, 0.35D, 0.0D));
                }
            }
        }
        dangerZones.add(new DangerZone(center, 2.0D, boss.getBossPhase().id() == 3 ? 90 : 120, boss.getBossPhase().id() == 3));
    }

    private void moltenImpact(ServerLevel level) {
        Vec3 center = boss.position();
        level.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y + 0.4D, center.z, 2, 0.4D, 0.1D, 0.4D, 0.0D);
        level.sendParticles(ParticleTypes.LAVA, center.x, center.y + 0.15D, center.z, 42, 1.5D, 0.2D, 1.5D, 0.04D);
        for (ServerPlayer player : boss.getThreatTable().topAggroedPlayers(boss, level, 14.0D, 8)) {
            double distance = horizontalDistance(player.position(), center);
            if (distance > 5.2D) {
                continue;
            }
            player.hurtServer(level, boss.damageSources().mobAttack(boss), boss.getBossPhase().id() == 3 ? 17.0F : 13.0F);
            Vec3 away = player.position().subtract(center).horizontal();
            if (away.lengthSqr() > 0.0001D) {
                player.setDeltaMovement(away.normalize().scale(0.85D).add(0.0D, 0.45D, 0.0D));
            }
        }
    }

    private void moltenRing(ServerLevel level, double radius) {
        Vec3 center = boss.position();
        for (int i = 0; i < Math.max(20, (int) (radius * 8.0D)); i++) {
            double angle = Math.PI * 2.0D * i / Math.max(20, (int) (radius * 8.0D));
            level.sendParticles(boss.getBossPhase().id() == 3 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME,
                    center.x + Math.cos(angle) * radius,
                    boss.getY() + 0.15D,
                    center.z + Math.sin(angle) * radius,
                    1, 0.03D, 0.02D, 0.03D, 0.0D);
        }
        for (ServerPlayer player : boss.getThreatTable().topAggroedPlayers(boss, level, 18.0D, 8)) {
            double distance = horizontalDistance(player.position(), center);
            if (distance >= radius - 0.65D && distance <= radius + 0.65D && player.getBoundingBox().minY <= boss.getY() + 1.1D) {
                player.hurtServer(level, boss.damageSources().mobAttack(boss), boss.getBossPhase().id() == 3 ? 7.0F : 5.0F);
            }
        }
    }

    private void telegraphCircle(ServerLevel level, double radius, boolean soul) {
        Vec3 center = boss.position();
        int points = Math.max(20, (int) (radius * 7.0D));
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2.0D * i / points;
            level.sendParticles(soul ? SOUL_DUST : TELEGRAPH_DUST,
                    center.x + Math.cos(angle) * radius,
                    boss.getY() + 0.08D,
                    center.z + Math.sin(angle) * radius,
                    1, 0.02D, 0.01D, 0.02D, 0.0D);
        }
    }

    private void groundMarker(ServerLevel level, BlockPos pos, double radius, boolean soul) {
        Vec3 center = Vec3.atCenterOf(pos);
        int points = 18;
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2.0D * i / points;
            level.sendParticles(soul ? SOUL_DUST : TELEGRAPH_DUST,
                    center.x + Math.cos(angle) * radius,
                    center.y + 0.04D,
                    center.z + Math.sin(angle) * radius,
                    1, 0.01D, 0.01D, 0.01D, 0.0D);
        }
    }

    private void pullPlayers(ServerLevel level) {
        Vec3 center = boss.position();
        for (ServerPlayer player : boss.getThreatTable().topAggroedPlayers(boss, level, 30.0D, 8)) {
            Vec3 pull = center.subtract(player.position()).horizontal();
            if (pull.lengthSqr() > 0.0001D) {
                player.addDeltaMovement(pull.normalize().scale(0.035D));
            }
        }
    }

    private void spiralSoulProjectiles(ServerLevel level, int tick) {
        Vec3 center = boss.position().add(0.0D, 1.0D, 0.0D);
        for (int arm = 0; arm < 4; arm++) {
            double angle = tick * 0.16D + arm * Math.PI * 0.5D;
            for (double radius = 2.0D; radius <= 12.0D; radius += 1.7D) {
                Vec3 point = center.add(Math.cos(angle + radius * 0.28D) * radius, 0.25D, Math.sin(angle + radius * 0.28D) * radius);
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, point.x, point.y, point.z, 1, 0.02D, 0.02D, 0.02D, 0.0D);
                for (ServerPlayer player : boss.getThreatTable().topAggroedPlayers(boss, level, 30.0D, 8)) {
                    if (player.position().add(0.0D, 1.0D, 0.0D).distanceToSqr(point) <= 0.9D) {
                        player.hurtServer(level, boss.damageSources().mobAttack(boss), 6.0F);
                    }
                }
            }
        }
    }

    private void renderBeam(ServerLevel level, double angle, boolean damaging) {
        Vec3 start = boss.position().add(0.0D, 1.5D, 0.0D);
        Vec3 direction = new Vec3(Math.cos(angle), 0.0D, Math.sin(angle));
        for (double distance = 1.5D; distance <= 18.0D; distance += damaging ? 0.35D : 0.8D) {
            Vec3 point = start.add(direction.scale(distance));
            level.sendParticles(damaging ? ParticleTypes.SOUL_FIRE_FLAME : SOUL_DUST,
                    point.x, point.y, point.z, damaging ? 2 : 1, 0.03D, 0.03D, 0.03D, 0.0D);
        }
        if (!damaging) {
            return;
        }
        Vec3 end = start.add(direction.scale(18.0D));
        for (ServerPlayer player : boss.getThreatTable().topAggroedPlayers(boss, level, 30.0D, 8)) {
            if (distanceToSegment2d(player.position(), start, end) <= 0.85D) {
                player.hurtServer(level, boss.damageSources().mobAttack(boss), 8.0F);
            }
        }
    }

    private void leaveBeamFlames(double angle) {
        Vec3 start = boss.position();
        Vec3 direction = new Vec3(Math.cos(angle), 0.0D, Math.sin(angle));
        for (double distance = 3.0D; distance <= 16.0D; distance += 3.0D) {
            dangerZones.add(new DangerZone(start.add(direction.scale(distance)), 1.0D, 60, true));
        }
    }

    private void particleLine(ServerLevel level, Vec3 start, Vec3 end, Object particle, double stepLength) {
        Vec3 delta = end.subtract(start);
        double length = delta.length();
        if (length < 0.0001D) {
            return;
        }
        Vec3 step = delta.normalize().scale(stepLength);
        int steps = Math.max(1, (int) (length / stepLength));
        for (int i = 0; i <= steps; i++) {
            Vec3 point = start.add(step.scale(i));
            if (particle instanceof DustParticleOptions dust) {
                level.sendParticles(dust, point.x, point.y, point.z, 1, 0.02D, 0.02D, 0.02D, 0.0D);
            } else if (particle == ParticleTypes.FLAME) {
                level.sendParticles(ParticleTypes.FLAME, point.x, point.y, point.z, 1, 0.02D, 0.02D, 0.02D, 0.0D);
            } else {
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, point.x, point.y, point.z, 1, 0.02D, 0.02D, 0.02D, 0.0D);
            }
        }
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

    private record Meteor(BlockPos pos, int landTick, int explodeTick) {
    }

    private static final class DangerZone {
        private final Vec3 center;
        private final double radius;
        private int ticks;
        private final boolean soul;

        private DangerZone(Vec3 center, double radius, int ticks, boolean soul) {
            this.center = center;
            this.radius = radius;
            this.ticks = ticks;
            this.soul = soul;
        }
    }

    private static final class FlameBrand {
        private int stacks;
        private int expiresAt;
    }
}
