package me.guardian.entity;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * The healing shield entity for the Overworld Guardian.
 * Has 300 HP, blocks all damage to the boss and heals it while active.
 * Lasts at most 40 seconds (800 ticks) before expiring on its own.
 */
public class HealingShieldEntity extends Entity {
    private static final int MAX_DURATION = 800; // 40 seconds
    private static final float MAX_HEALTH = 300.0F;

    private static final EntityDataAccessor<Float> DATA_HEALTH =
            SynchedEntityData.defineId(HealingShieldEntity.class, EntityDataSerializers.FLOAT);

    private UUID bossUUID = null;
    private int age = 0;

    public HealingShieldEntity(EntityType<HealingShieldEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public HealingShieldEntity(Level level, OverworldGuardianEntity boss) {
        this(ModEntities.HEALING_SHIELD, level);
        this.bossUUID = boss.getUUID();
        this.setPos(boss.getX(), boss.getY(), boss.getZ());
    }

    public float getShieldHealth() {
        return this.entityData.get(DATA_HEALTH);
    }

    public float getMaxHealth() {
        return MAX_HEALTH;
    }

    public boolean isAliveShield() {
        return !this.isRemoved() && getShieldHealth() > 0;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_HEALTH, MAX_HEALTH);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        // Shield absorbs damage from players
        if (source.getEntity() instanceof net.minecraft.world.entity.player.Player
                || source.getDirectEntity() instanceof net.minecraft.world.entity.player.Player) {
            float current = getShieldHealth();
            float newHp = Math.max(0.0F, current - amount);
            this.entityData.set(DATA_HEALTH, newHp);

            // Spark particles to show hits
            level.sendParticles(ParticleTypes.CRIT, this.getX(), this.getY() + 1.0D, this.getZ(),
                    8, 0.5D, 0.5D, 0.5D, 0.05D);

            if (newHp <= 0) {
                notifyBossShieldBroken(level);
                level.playSound(null, this.blockPosition(), net.minecraft.sounds.SoundEvents.GLASS_BREAK, 
                        net.minecraft.sounds.SoundSource.HOSTILE, 1.5F, 0.5F);
                level.sendParticles(ParticleTypes.EXPLOSION, this.getX(), this.getY() + 1.2D, this.getZ(), 
                        8, 0.5D, 0.5D, 0.5D, 0.05D);
                this.discard();
            }
            return true;
        }
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        age++;

        // Stick to boss position
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }

        if (bossUUID != null) {
            var bossEntity = serverLevel.getEntity(bossUUID);
            if (bossEntity instanceof OverworldGuardianEntity boss && boss.isAlive()) {
                this.setPos(boss.getX(), boss.getY(), boss.getZ());

                // Heal boss every second
                if (age % 20 == 0) {
                    boss.heal(2.0F);
                }
            } else {
                // Boss gone, shield disappears
                this.discard();
                return;
            }
        }

        // Visual effect: rotating blue ring of particles
        if (age % 2 == 0) {
            double radius = 1.8D;
            int points = 16;
            double angleOffset = (age * 0.15D) % (Math.PI * 2);
            for (int i = 0; i < points; i++) {
                double angle = angleOffset + (Math.PI * 2.0D * i / points);
                serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        this.getX() + Math.cos(angle) * radius,
                        this.getY() + 1.2D,
                        this.getZ() + Math.sin(angle) * radius,
                        1, 0.02D, 0.02D, 0.02D, 0.0D);
            }
        }

        // Expire after max duration
        if (age >= MAX_DURATION) {
            notifyBossShieldBroken(serverLevel);
            this.discard();
        }
    }

    private void notifyBossShieldBroken(ServerLevel level) {
        if (bossUUID == null) return;
        var bossEntity = level.getEntity(bossUUID);
        if (bossEntity instanceof OverworldGuardianEntity boss) {
            boss.onShieldDestroyed();
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        this.entityData.set(DATA_HEALTH, input.getFloatOr("ShieldHealth", MAX_HEALTH));
        this.age = input.getIntOr("ShieldAge", 0);
        input.getString("BossUUID").ifPresent(s -> {
            try { this.bossUUID = java.util.UUID.fromString(s); }
            catch (IllegalArgumentException ignored) {}
        });
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putFloat("ShieldHealth", getShieldHealth());
        output.putInt("ShieldAge", age);
        if (bossUUID != null) {
            output.putString("BossUUID", bossUUID.toString());
        }
    }
}
