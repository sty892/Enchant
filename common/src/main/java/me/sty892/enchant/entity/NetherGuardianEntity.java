package me.sty892.enchant.entity;

import net.minecraft.entity.EntityType;
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

import me.sty892.enchant.entity.ai.FireballBarrageGoal;
import me.sty892.enchant.entity.ai.TeleportBehindGoal;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

public class NetherGuardianEntity extends HostileEntity implements GeoEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Map<UUID, Float> damageContributors = new HashMap<>();
    public static BiConsumer<NetherGuardianEntity, ServerWorld> deathCallback;

    private boolean phase50Reached = false;
    private boolean phase25Reached = false;

    public NetherGuardianEntity(EntityType<? extends HostileEntity> entityType, World world) {
        super(entityType, world);
        this.setFireImmune(true);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 750.0)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 20.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 80.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(1, new SwimGoal(this));
        this.goalSelector.add(2, new LookAtEntityGoal(this, PlayerEntity.class, 8.0f));
        this.goalSelector.add(3, new MeleeAttackGoal(this, 1.2, false) {
            @Override
            public boolean attack(LivingEntity target, double squaredDistance) {
                boolean result = super.attack(target, squaredDistance);
                if (result) {
                    target.setOnFireFor(5);
                }
                return result;
            }
        });
        this.goalSelector.add(4, new FireballBarrageGoal(this, 160)); // 8s cooldown
        this.goalSelector.add(5, new TeleportBehindGoal(this));
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

        if (healthPercent <= 0.50f && !phase50Reached) {
            phase50Reached = true;
            this.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, 60, 0));
            // Fire pillars logic would go here
        }

        if (healthPercent <= 0.25f && !phase25Reached) {
            phase25Reached = true;
            for (LivingEntity entity : getWorld().getEntitiesByClass(LivingEntity.class, getBoundingBox().expand(10.0), e -> e != this)) {
                entity.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 200, 1));
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
