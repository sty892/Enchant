package me.sty892.enchant.entity;

import me.sty892.enchant.entity.ai.ProjectileAttackGoal;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

public class OverworldGuardianEntity extends HostileEntity implements GeoEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Map<UUID, Float> damageContributors = new HashMap<>();
    public static BiConsumer<OverworldGuardianEntity, ServerWorld> deathCallback;

    private boolean phase75Reached = false;
    private boolean phase50Reached = false;
    private boolean phase25Reached = false;

    public OverworldGuardianEntity(EntityType<? extends HostileEntity> entityType, World world) {
        super(entityType, world);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 500.0)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 15.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.25)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 64.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.8);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(1, new SwimGoal(this));
        this.goalSelector.add(2, new LookAtEntityGoal(this, PlayerEntity.class, 8.0f));
        this.goalSelector.add(3, new WanderAroundFarGoal(this, 1.0));
        this.goalSelector.add(4, new MeleeAttackGoal(this, 1.2, false));
        this.goalSelector.add(5, new ProjectileAttackGoal(this, 1.0, 100)); // 5 seconds interval
    }

    @Override
    public void tick() {
        super.tick();
        if (!getWorld().isClient) {
            checkPhases();
        }
    }

    private void checkPhases() {
        float healthPercent = getHealth() / getMaxHealth();

        if (healthPercent <= 0.75f && !phase75Reached) {
            phase75Reached = true;
            spawnMinions();
            applySpeedBoost(0.05);
        }

        if (healthPercent <= 0.50f && !phase50Reached) {
            phase50Reached = true;
            // Leap attack logic could be added here or as a goal
        }

        if (healthPercent <= 0.25f && !phase25Reached) {
            phase25Reached = true;
            applySpeedBoost(0.2);
            getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE).setBaseValue(22.5); // 1.5x
        }
    }

    private void applySpeedBoost(double amount) {
        double current = getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED).getBaseValue();
        getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED).setBaseValue(current + amount);
    }

    private void spawnMinions() {
        if (getWorld() instanceof ServerWorld serverWorld) {
            for (int i = 0; i < 3; i++) {
                // In a real implementation, we would spawn ModEntities.GENERIC_BOSS
                // For now, let's assume we'll use a placeholder or add the logic later
            }
        }
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (source.getAttacker() instanceof PlayerEntity player) {
            damageContributors.merge(player.getUuid(), amount, Float::sum);
        }
        return super.damage(source, amount);
    }

    @Override
    public void onDeath(DamageSource damageSource) {
        super.onDeath(damageSource);
        if (!getWorld().isClient && deathCallback != null) {
            deathCallback.accept(this, (ServerWorld) getWorld());
        }
    }

    public Map<UUID, Float> getDamageContributors() {
        return damageContributors;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main_controller", 5, state -> {
            if (this.dead || this.getHealth() <= 0) {
                return state.setAndContinue(RawAnimation.begin().thenPlay("death"));
            }
            if (state.isMoving()) {
                return state.setAndContinue(RawAnimation.begin().thenLoop("walk"));
            }
            return state.setAndContinue(RawAnimation.begin().thenLoop("idle"));
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
