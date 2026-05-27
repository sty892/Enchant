package me.guardian.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;

public final class GuardianBossAttackGoal extends MeleeAttackGoal {
    private final Monster boss;

    public GuardianBossAttackGoal(Monster boss, double speedModifier) {
        super(boss, speedModifier, true);
        this.boss = boss;
    }

    @Override
    public boolean canUse() {
        Player player = boss.level().getNearestPlayer(boss, GuardianBossAi.ATTACK_RANGE);
        if (player == null || !player.isAlive()) {
            return false;
        }
        boss.setTarget(player);
        return super.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = boss.getTarget();
        return target instanceof Player
                && target.isAlive()
                && boss.distanceToSqr(target) <= GuardianBossAi.ATTACK_RANGE * GuardianBossAi.ATTACK_RANGE
                && super.canContinueToUse();
    }

    @Override
    protected void checkAndPerformAttack(LivingEntity target) {
        if (!isTimeToAttack() || !canPerformAttack(target)) {
            return;
        }

        resetAttackCooldown();
        boss.swing(InteractionHand.MAIN_HAND);
        GuardianBossAi.triggerAttackAnimation(boss);
        float damage = (float) boss.getAttributeValue(Attributes.ATTACK_DAMAGE);
        target.hurtServer((ServerLevel) boss.level(), boss.damageSources().mobAttack(boss), damage);
    }
}
