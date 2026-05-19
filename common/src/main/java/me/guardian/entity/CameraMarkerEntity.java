package me.guardian.entity;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;

public class CameraMarkerEntity extends Entity implements GeoEntity {
    private static final EntityDataAccessor<String> DATA_CUTSCENE_ID = SynchedEntityData.defineId(
            CameraMarkerEntity.class,
            EntityDataSerializers.STRING
    );
    private static final EntityDataAccessor<Integer> DATA_INDEX = SynchedEntityData.defineId(
            CameraMarkerEntity.class,
            EntityDataSerializers.INT
    );
    private static final EntityDataAccessor<Float> DATA_DURATION = SynchedEntityData.defineId(
            CameraMarkerEntity.class,
            EntityDataSerializers.FLOAT
    );

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public CameraMarkerEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
        noPhysics = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_CUTSCENE_ID, "on_diamond_pickup");
        builder.define(DATA_INDEX, 1);
        builder.define(DATA_DURATION, 5.0f);
    }

    public String getCutsceneId() {
        return this.entityData.get(DATA_CUTSCENE_ID);
    }

    public void setCutsceneId(String cutsceneId) {
        this.entityData.set(DATA_CUTSCENE_ID, cutsceneId);
    }

    public int getIndex() {
        return this.entityData.get(DATA_INDEX);
    }

    public void setIndex(int index) {
        this.entityData.set(DATA_INDEX, index);
    }

    public float getDuration() {
        return this.entityData.get(DATA_DURATION);
    }

    public void setDuration(float duration) {
        this.entityData.set(DATA_DURATION, duration);
    }

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(0.0D, 0.0D, 0.0D);
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        return InteractionResult.PASS;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        setCutsceneId(input.getString("CutsceneId").orElse("on_diamond_pickup"));
        setIndex(input.getIntOr("Index", 1));
        setDuration(input.getFloatOr("Duration", 5.0f));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putString("CutsceneId", getCutsceneId());
        output.putInt("Index", getIndex());
        output.putFloat("Duration", getDuration());
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
