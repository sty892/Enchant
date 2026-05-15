package me.guardian.entity;

import me.guardian.event.GuardianAltarPlacementHooks;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

public class AltarPlacementEntity extends Entity implements GeoEntity {
    public static final int ANIMATION_TICKS = 60;
    private static final RawAnimation APPEAR_ANIMATION = RawAnimation.begin().thenPlay("appear");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private boolean placed;

    public AltarPlacementEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
        noPhysics = true;
    }

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(0.0D, 0.0D, 0.0D);
        if (!(level() instanceof ServerLevel level) || placed || tickCount < ANIMATION_TICKS) {
            return;
        }
        placed = true;
        GuardianAltarPlacementHooks.placeAltar(level, blockPosition());
        discard();
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        placed = input.getBooleanOr("Placed", false);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putBoolean("Placed", placed);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>("Appear", state -> state.renderState().getAnimatableAge() <= ANIMATION_TICKS
                ? state.setAndContinue(APPEAR_ANIMATION)
                : PlayState.STOP)
                .triggerableAnim("appear", APPEAR_ANIMATION));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
