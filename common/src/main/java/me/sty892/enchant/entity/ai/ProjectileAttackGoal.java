package me.sty892.enchant.entity.ai;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;

public class ProjectileAttackGoal extends Goal {
    private final MobEntity mob;
    private final double speed;
    private final int interval;
    private int cooldown;

    public ProjectileAttackGoal(MobEntity mob, double speed, int interval) {
        this.mob = mob;
        this.speed = speed;
        this.interval = interval;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        return mob.getTarget() != null;
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) return;

        mob.getLookControl().lookAt(target, 30.0F, 30.0F);
        double distSq = mob.squaredDistanceTo(target);

        if (--cooldown <= 0 && distSq < 1024.0) {
            cooldown = interval;
            Vec3d vec3d = mob.getRotationVec(1.0F);
            FireballEntity fireball = new FireballEntity(mob.getWorld(), mob, vec3d.x, vec3d.y, vec3d.z, 1);
            fireball.setPosition(mob.getX() + vec3d.x * 2.0, mob.getY() + mob.getStandingEyeHeight(), mob.getZ() + vec3d.z * 2.0);
            mob.getWorld().spawnEntity(fireball);
        }
    }
}
