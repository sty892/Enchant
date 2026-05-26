package me.guardian.entity;

import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class CeilingFallingBlockEntity extends FallingBlockEntity {
    private BlockState blockState = Blocks.STONE.defaultBlockState();

    public CeilingFallingBlockEntity(EntityType<CeilingFallingBlockEntity> type, Level level) {
        super(type, level);
        this.dropItem = false;
    }

    public CeilingFallingBlockEntity(Level level, double x, double y, double z, BlockState state) {
        super(ModEntities.CEILING_FALLING_BLOCK, level);
        this.blockState = state;
        this.setPos(x, y, z);
        this.xo = x;
        this.yo = y;
        this.zo = z;
        this.dropItem = false;
    }

    @Override
    public BlockState getBlockState() {
        return this.blockState;
    }

    public void setBlockState(BlockState state) {
        this.blockState = state;
    }

    @Override
    public void tick() {
        if (this.onGround() || this.verticalCollision) {
            explodeAndDiscard();
            return;
        }
        super.tick();
    }

    private void explodeAndDiscard() {
        if (this.level() instanceof ServerLevel serverLevel) {
            Vec3 pos = this.position();
            serverLevel.playSound(null, pos.x, pos.y, pos.z, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 1.0F, 1.0F);
            BlockParticleOption option = new BlockParticleOption(ParticleTypes.BLOCK, this.blockState);
            serverLevel.sendParticles(option, pos.x, pos.y + 0.1D, pos.z, 40, 0.5D, 0.2D, 0.5D, 0.15D);
            serverLevel.sendParticles(ParticleTypes.POOF, pos.x, pos.y + 0.1D, pos.z, 5, 0.2D, 0.2D, 0.2D, 0.05D);
            
            double radius = 2.0D;
            float damage = 8.0F;
            for (LivingEntity living : serverLevel.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(radius, 1.5D, radius))) {
                if (living instanceof net.minecraft.world.entity.player.Player player && player.isAlive()) {
                    player.hurtServer(serverLevel, player.damageSources().fallingBlock(this), damage);
                }
            }
        }
        this.discard();
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.store("CeilingBlockState", BlockState.CODEC, this.blockState);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.blockState = input.read("CeilingBlockState", BlockState.CODEC).orElse(Blocks.STONE.defaultBlockState());
    }
}
