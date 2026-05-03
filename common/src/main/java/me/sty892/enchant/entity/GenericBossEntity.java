package me.sty892.enchant.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
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

import me.sty892.enchant.entity.ai.ChargeAttackGoal;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

public class GenericBossEntity extends HostileEntity implements GeoEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Map<UUID, Float> damageContributors = new HashMap<>();
    public static BiConsumer<GenericBossEntity, ServerWorld> deathCallback;

    private static final TrackedData<String> VARIANT = DataTracker.registerData(GenericBossEntity.class, TrackedDataHandlerRegistry.STRING);
    private static final TrackedData<Boolean> IS_SUMMONED = DataTracker.registerData(GenericBossEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    private boolean phase33Reached = false;

    public GenericBossEntity(EntityType<? extends HostileEntity> entityType, World world) {
        super(entityType, world);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 300.0)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 12.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.28);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(VARIANT, "default");
        builder.add(IS_SUMMONED, false);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(1, new SwimGoal(this));
        this.goalSelector.add(2, new LookAtEntityGoal(this, PlayerEntity.class, 8.0f));
        this.goalSelector.add(3, new ChargeAttackGoal(this));
        this.goalSelector.add(4, new MeleeAttackGoal(this, 1.2, false));
    }

    @Override
    public void tick() {
        super.tick();
        if (!getWorld().isClient) {
            checkPhases();
            checkGroupBuff();
        }
    }

    private void checkPhases() {
        float healthPercent = getHealth() / getMaxHealth();
        if (healthPercent <= 0.33f && !phase33Reached) {
            phase33Reached = true;
            spawnMinions();
        }
    }

    private void checkGroupBuff() {
        List<GenericBossEntity> nearby = getWorld().getEntitiesByClass(GenericBossEntity.class, getBoundingBox().expand(10.0), e -> e != this);
        if (nearby.size() >= 2) { // Total 3+ including self
            this.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 40, 0));
            this.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 40, 0));
        }
    }

    private void spawnMinions() {
        if (getWorld() instanceof ServerWorld serverWorld) {
            for (int i = 0; i < 2; i++) {
                GenericBossEntity minion = new GenericBossEntity(ModEntities.GENERIC_BOSS, serverWorld);
                minion.setVariant(this.getVariant());
                minion.setSummoned(true);
                minion.setPosition(this.getX(), this.getY(), this.getZ());
                serverWorld.spawnEntity(minion);
            }
        }
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putString("variant", getVariant());
        nbt.putBoolean("isSummoned", isSummoned());
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        setVariant(nbt.getString("variant"));
        setSummoned(nbt.getBoolean("isSummoned"));
    }

    public String getVariant() {
        return this.dataTracker.get(VARIANT);
    }

    public void setVariant(String variant) {
        this.dataTracker.set(VARIANT, variant);
    }

    public boolean isSummoned() {
        return this.dataTracker.get(IS_SUMMONED);
    }

    public void setSummoned(boolean summoned) {
        this.dataTracker.set(IS_SUMMONED, summoned);
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
