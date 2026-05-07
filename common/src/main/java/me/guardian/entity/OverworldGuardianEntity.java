package me.guardian.entity;

import me.guardian.event.GuardianBossEventHooks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.constant.DefaultAnimations;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class OverworldGuardianEntity extends Monster implements GeoEntity {
    private static final String BOSS_CONFIG_KEY = "overworld";

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Map<UUID, Float> damageContributors = new HashMap<>();
    private boolean spawnEventTriggered = false;
    private boolean deathEventTriggered = false;

    public OverworldGuardianEntity(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 500.0)
                .add(Attributes.ATTACK_DAMAGE, 15.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FOLLOW_RANGE, 64.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8);
    }

    public Map<UUID, Float> getDamageContributors() {
        return damageContributors;
    }

    @Override
    protected void registerGoals() {
        // Bosses are event targets for now; they intentionally do not move or attack.
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean damaged = super.hurtServer(level, source, amount);
        if (damaged && source.getEntity() instanceof ServerPlayer player) {
            damageContributors.merge(player.getUUID(), amount, Float::sum);
        }
        return damaged;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        triggerSpawnEvent(level);
        this.setTarget(null);
        this.getNavigation().stop();
        this.setDeltaMovement(0.0D, 0.0D, 0.0D);
    }

    @Override
    public void die(DamageSource damageSource) {
        if (!deathEventTriggered && this.level() instanceof ServerLevel serverLevel) {
            deathEventTriggered = true;
            GuardianBossEventHooks.triggerOnDeath(BOSS_CONFIG_KEY, serverLevel, this.blockPosition(), this, damageContributors);
        }
        super.die(damageSource);
    }

    private void triggerSpawnEvent(ServerLevel level) {
        if (spawnEventTriggered) {
            return;
        }
        spawnEventTriggered = true;
        GuardianBossEventHooks.triggerOnSpawn(BOSS_CONFIG_KEY, level, this.blockPosition(), this);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(DefaultAnimations.genericWalkIdleController());
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
