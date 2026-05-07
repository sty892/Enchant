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
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.constant.DefaultAnimations;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GenericBossEntity extends Monster implements GeoEntity {
    private static final String BOSS_CONFIG_KEY = "generic";

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Map<UUID, Float> damageContributors = new HashMap<>();
    private String variant = "default";
    private boolean summoned = false;
    private boolean deathEventTriggered = false;

    public GenericBossEntity(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 300.0)
                .add(Attributes.ATTACK_DAMAGE, 12.0)
                .add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.FOLLOW_RANGE, 48.0);
    }

    public String getVariant() {
        return variant;
    }

    public void setVariant(String variant) {
        this.variant = variant == null || variant.isBlank() ? "default" : variant;
    }

    public boolean isSummoned() {
        return summoned;
    }

    public void setSummoned(boolean summoned) {
        this.summoned = summoned;
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

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putString("variant", variant);
        output.putBoolean("isSummoned", summoned);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.variant = input.getStringOr("variant", "default");
        this.summoned = input.getBooleanOr("isSummoned", false);
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
