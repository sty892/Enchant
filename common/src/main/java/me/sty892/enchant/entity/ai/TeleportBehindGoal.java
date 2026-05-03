package me.sty892.enchant.entity.ai;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;

public class TeleportBehindGoal extends Goal {
    private final MobEntity mob;
    private int cooldown;

    public TeleportBehindGoal(MobEntity mob) {
        this.mob = mob;
        this.setControls(EnumSet.of(Control.MOVE));
    }

    @Override
    public boolean canStart() {
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive() || --cooldown > 0) return false;
        
        // Start if pathfinding fails or too far and obstructed
        return mob.getNavigation().isIdle() && mob.squaredDistanceTo(target) > 16.0;
    }

    @Override
    public void start() {
        LivingEntity target = mob.getTarget();
        if (target == null) return;

        Vec3d behind = target.getPos().add(target.getRotationVec(1.0F).multiply(-2.0));
        mob.teleport(behind.x, behind.y, behind.z, true);
        cooldown = 60; // 3 seconds
    }
}
