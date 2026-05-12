package me.guardian.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import software.bernie.geckolib.animatable.GeoEntity;

public final class GuardianBossAi {
    public static final int HOME_RADIUS = 15;
    public static final double ATTACK_RANGE = 16.0D;
    public static final float ATTACK_DAMAGE = 3.0F;
    public static final String ATTACK_CONTROLLER = "attack_controller";
    public static final String ATTACK_TRIGGER = "attack";

    private static final float OUT_OF_HOME_COST_PER_BLOCK = 8.0F;

    private GuardianBossAi() {
    }

    public static void ensureSpawnHome(Mob mob) {
        if (!mob.hasHome()) {
            mob.setHomeTo(mob.blockPosition(), HOME_RADIUS);
        }
    }

    public static float applySpawnDistancePenalty(Mob mob, BlockPos pos, float baseValue) {
        return baseValue - spawnDistancePenalty(mob, pos);
    }

    public static void addSpawnDistancePenalty(Mob mob, net.minecraft.world.level.pathfinder.Node node) {
        node.costMalus += spawnDistancePenalty(mob, node.asBlockPos());
    }

    public static void triggerAttackAnimation(Mob mob) {
        triggerAttackAnimation(mob, ATTACK_TRIGGER);
    }

    public static void triggerAttackAnimation(Mob mob, String triggerName) {
        if (mob instanceof GeoEntity geoEntity) {
            geoEntity.triggerAnim(ATTACK_CONTROLLER, triggerName);
        }
    }

    private static float spawnDistancePenalty(Mob mob, BlockPos pos) {
        BlockPos spawn = mob.hasHome() ? mob.getHomePosition() : mob.blockPosition();
        double distance = Math.sqrt(pos.distSqr(spawn));
        double outsideRadius = distance - HOME_RADIUS;
        if (outsideRadius <= 0.0D) {
            return 0.0F;
        }
        return (float) outsideRadius * OUT_OF_HOME_COST_PER_BLOCK;
    }
}
