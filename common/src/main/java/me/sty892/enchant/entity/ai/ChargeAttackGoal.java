package me.sty892.enchant.entity.ai;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;

public class ChargeAttackGoal extends Goal {
    private final MobEntity mob;
    private int cooldown;
    private int chargeTimer;
    private Vec3d chargeDir;

    public ChargeAttackGoal(MobEntity mob) {
        this.mob = mob;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        LivingEntity target = mob.getTarget();
        return target != null && target.isAlive() && --cooldown <= 0 && mob.squaredDistanceTo(target) < 100.0;
    }

    @Override
    public void start() {
        LivingEntity target = mob.getTarget();
        if (target != null) {
            this.chargeDir = target.getPos().subtract(mob.getPos()).normalize();
            this.chargeTimer = 20; // 1 second dash
        }
    }

    @Override
    public boolean shouldContinue() {
        return chargeTimer > 0;
    }

    @Override
    public void tick() {
        if (chargeDir != null) {
            mob.setVelocity(chargeDir.multiply(0.8));
            chargeTimer--;
            
            // Damage nearby entities during charge
            for (LivingEntity entity : mob.getWorld().getEntitiesByClass(LivingEntity.class, mob.getBoundingBox().expand(1.0), e -> e != mob)) {
                entity.damage(mob.getDamageSources().mobAttack(mob), (float) mob.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE));
            }
        }
        
        if (chargeTimer <= 0) {
            cooldown = 200; // 10 seconds
        }
    }
}
