package me.guardian.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.world.level.block.Blocks;

import java.util.UUID;

/**
 * Temple Statue — summoned by the Overworld Guardian (Attack 9).
 * Extends Husk (so it renders as a Husk client-side and doesn't burn in sun).
 * While at least one statue is alive, the boss takes only 10% damage (90% resistance).
 * Statues die without respawning in the current phase.
 */
public class TempleStatueEntity extends Husk {

    private UUID bossUUID = null;

    public TempleStatueEntity(EntityType<? extends Husk> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 120.0)
                .add(Attributes.ATTACK_DAMAGE, 7.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.5);
    }

    public void setBossUUID(UUID uuid) {
        this.bossUUID = uuid;
    }

    public UUID getBossUUID() {
        return bossUUID;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, false));
        this.goalSelector.addGoal(3, new RandomStrollGoal(this, 0.8D));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected boolean convertsInWater() {
        return false;
    }

    @Override
    public void die(DamageSource cause) {
        super.die(cause);
        if (this.level() instanceof ServerLevel sl) {
            if (bossUUID != null) {
                var bossEntity = sl.getEntity(bossUUID);
                if (bossEntity instanceof OverworldGuardianEntity boss) {
                    boss.onStatueDied(this.getUUID());
                }
            }
            // Crumbling sound
            sl.playSound(null, this.blockPosition(), net.minecraft.sounds.SoundEvents.STONE_BREAK, 
                    net.minecraft.sounds.SoundSource.HOSTILE, 1.5F, 0.8F);
            // Particle explosion
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 1.0D, this.getZ(), 
                    20, 0.3D, 0.5D, 0.3D, 0.05D);
            sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()), 
                    this.getX(), this.getY() + 1.0D, this.getZ(), 
                    40, 0.3D, 0.5D, 0.3D, 0.1D);
            
            // Discard immediately to prevent default death animation
            this.discard();
        }
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }
}

