package me.sty892.enchant.entity.ai;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.projectile.SmallFireballEntity;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;

public class FireballBarrageGoal extends Goal {
    private final MobEntity mob;
    private final int interval;
    private int cooldown;
    private int shotsLeft;
    private int shotTimer;

    public FireballBarrageGoal(MobEntity mob, int interval) {
        this.mob = mob;
        this.interval = interval;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        LivingEntity target = mob.getTarget();
        return target != null && target.isAlive() && --cooldown <= 0;
    }

    @Override
    public void start() {
        this.shotsLeft = 5;
        this.shotTimer = 0;
    }

    @Override
    public boolean shouldContinue() {
        return shotsLeft > 0 && mob.getTarget() != null;
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) return;

        mob.getLookControl().lookAt(target, 30.0F, 30.0F);

        if (++shotTimer >= 5) {
            shotTimer = 0;
            shotsLeft--;
            
            Vec3d targetDir = target.getPos().subtract(mob.getPos()).normalize();
            SmallFireballEntity fireball = new SmallFireballEntity(mob.getWorld(), mob, targetDir.x, targetDir.y, targetDir.z);
            fireball.setPosition(mob.getX() + targetDir.x, mob.getY() + mob.getStandingEyeHeight(), mob.getZ() + targetDir.z);
            mob.getWorld().spawnEntity(fireball);
            
            if (shotsLeft <= 0) {
                cooldown = interval;
            }
        }
    }
}
