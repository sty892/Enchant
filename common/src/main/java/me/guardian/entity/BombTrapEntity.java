package me.guardian.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class BombTrapEntity extends Entity implements ItemSupplier {
    private int age;

    public BombTrapEntity(EntityType<BombTrapEntity> type, Level level) {
        super(type, level);
    }

    public BombTrapEntity(Level level, double x, double y, double z) {
        this(ModEntities.BOMB_TRAP, level);
        this.setPos(x, y, z);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        this.age++;

        if (!this.onGround()) {
            this.setDeltaMovement(this.getDeltaMovement().add(0.0D, -0.04D, 0.0D));
        } else {
            this.setDeltaMovement(0.0D, 0.0D, 0.0D);
        }
        this.move(net.minecraft.world.entity.MoverType.SELF, this.getDeltaMovement());
        this.setDeltaMovement(this.getDeltaMovement().scale(0.98D));

        if (this.level().isClientSide()) {
            if (this.random.nextInt(5) == 0) {
                this.level().addParticle(ParticleTypes.SMOKE, this.getX(), this.getY() + 0.2D, this.getZ(), 0.0D, 0.0D, 0.0D);
            }
            return;
        }

        ServerLevel serverLevel = (ServerLevel) this.level();
        
        if (this.age > 600) {
            this.discard();
            return;
        }

        boolean triggered = false;
        for (LivingEntity living : serverLevel.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(1.0D, 0.8D, 1.0D))) {
            if (living.isAlive() && !(living instanceof OverworldGuardianEntity) && !(living instanceof NetherGuardianEntity)) {
                triggered = true;
                break;
            }
        }

        if (triggered) {
            explode(serverLevel);
        }
    }

    private void explode(ServerLevel level) {
        Vec3 pos = this.position();
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 1.0F, 1.2F);
        level.sendParticles(ParticleTypes.EXPLOSION, pos.x, pos.y + 0.5D, pos.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.x, pos.y + 0.5D, pos.z, 30, 1.0D, 0.5D, 1.0D, 0.05D);

        double radius = 3.0D;
        float damage = 6.0F;
        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(radius, 1.5D, radius))) {
            if (living.isAlive() && !(living instanceof OverworldGuardianEntity) && !(living instanceof NetherGuardianEntity)) {
                living.hurtServer(level, level.damageSources().explosion(null, null), damage);
                living.addEffect(new MobEffectInstance(MobEffects.POISON, 160, 0));
            }
        }
        this.discard();
    }

    @Override
    public ItemStack getItem() {
        return new ItemStack(Items.FIRE_CHARGE);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        this.age = input.getIntOr("Age", 0);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putInt("Age", this.age);
    }
}
