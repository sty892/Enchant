package me.sty892.enchant.block.entity;

import me.sty892.enchant.block.ModBlocks;
import me.sty892.enchant.config.AltarConfig;
import me.sty892.enchant.config.ConfigManager;
import me.sty892.enchant.state.GuardianWorldState;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AltarCoreBlockEntity extends BlockEntity {
    private int ritualTicks = 0;
    private boolean isRitualRunning = false;
    private UUID currentRitualPlayer;

    private static final UUID SPEED_MODIFIER_ID = UUID.fromString("6a3b2c1d-0e1f-2a3b-4c5d-6e7f8a9b0c1d");
    private static final UUID PROTECTION_MODIFIER_ID = UUID.fromString("7a3b2c1d-0e1f-2a3b-4c5d-6e7f8a9b0c1d");
    private static final UUID DAMAGE_MODIFIER_ID = UUID.fromString("8a3b2c1d-0e1f-2a3b-4c5d-6e7f8a9b0c1d");

    public AltarCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ALTAR_CORE_BE, pos, state);
    }

    public static void tick(World world, BlockPos pos, BlockState state, AltarCoreBlockEntity blockEntity) {
        if (world.isClient || !blockEntity.isRitualRunning) return;

        PlayerEntity player = world.getPlayerByUuid(blockEntity.currentRitualPlayer);
        if (player == null || player.squaredDistanceTo(pos.toCenterPos()) > 25.0) {
            blockEntity.cancelRitual();
            return;
        }

        blockEntity.ritualTicks++;
        spawnRitualParticles((ServerWorld) world, pos, player);

        if (blockEntity.ritualTicks >= 100) { // 5 seconds
            blockEntity.completeRitual((ServerPlayerEntity) player);
        }
    }

    private static void spawnRitualParticles(ServerWorld world, BlockPos pos, PlayerEntity player) {
        for (BlockPos altarPos : getNearbyAltars(world, pos)) {
            world.spawnParticles(ParticleTypes.CRIT, altarPos.getX() + 0.5, altarPos.getY() + 1.2, altarPos.getZ() + 0.5, 5, 0.1, 0.1, 0.1, 0.05);
            world.spawnParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.0, player.getZ(), 5, 0.2, 0.2, 0.2, 0.1);
        }
    }

    public ActionResult onUse(PlayerEntity player, ItemStack stack) {
        if (isRitualRunning) return ActionResult.FAIL;

        if (stack.getItem().toString().contains("fragment")) {
            List<BlockPos> altars = getNearbyAltars(world, pos);
            boolean foundMatching = false;
            for (BlockPos p : altars) {
                if (world.getBlockEntity(p) instanceof AltarBlockEntity altar) {
                    if (player.getUuid().equals(altar.getOwner())) {
                        foundMatching = true;
                        altar.setActive(true);
                    }
                }
            }

            if (foundMatching) {
                this.isRitualRunning = true;
                this.ritualTicks = 0;
                this.currentRitualPlayer = player.getUuid();
                return ActionResult.SUCCESS;
            }
        }
        return ActionResult.PASS;
    }

    private void cancelRitual() {
        this.isRitualRunning = false;
        this.ritualTicks = 0;
        List<BlockPos> altars = getNearbyAltars(world, pos);
        for (BlockPos p : altars) {
            if (world.getBlockEntity(p) instanceof AltarBlockEntity altar) {
                altar.setActive(false);
            }
        }
    }

    private void completeRitual(ServerPlayerEntity player) {
        this.isRitualRunning = false;
        AltarConfig config = ConfigManager.getAltarConfig();
        GuardianWorldState worldState = GuardianWorldState.getServerState(player.getServer());
        boolean stage2 = worldState.netherBossDefeated;
        Map<String, Integer> limits = stage2 ? config.stage_2 : config.stage_1;

        List<BlockPos> altars = getNearbyAltars(world, pos);
        for (BlockPos p : altars) {
            if (world.getBlockEntity(p) instanceof AltarBlockEntity altar && altar.isActive()) {
                applyBuff(player, world.getBlockState(p), limits);
                altar.clear();
            }
        }
        player.sendMessage(Text.literal("Ритуал завершен!"), true);
    }

    private void applyBuff(ServerPlayerEntity player, BlockState state, Map<String, Integer> limits) {
        NbtCompound nbt = player.getPersistentData();
        if (state.isOf(ModBlocks.ALTAR_SPEED)) {
            int level = nbt.getInt("guardian_speed_level");
            if (level < limits.get("max_speed")) {
                nbt.putInt("guardian_speed_level", level + 1);
                updateAttributes(player);
            } else {
                player.sendMessage(Text.literal("Достигнут максимум скорости"), true);
            }
        } else if (state.isOf(ModBlocks.ALTAR_PROTECTION)) {
            int level = nbt.getInt("guardian_protection_level");
            if (level < limits.get("max_protection")) {
                nbt.putInt("guardian_protection_level", level + 1);
                updateAttributes(player);
            }
        } else if (state.isOf(ModBlocks.ALTAR_DAMAGE)) {
            int level = nbt.getInt("guardian_damage_level");
            if (level < limits.get("max_damage")) {
                nbt.putInt("guardian_damage_level", level + 1);
                updateAttributes(player);
            }
        } else if (state.isOf(ModBlocks.ALTAR_RECOVERY)) {
            int level = nbt.getInt("guardian_recovery_level");
            if (level < limits.get("max_recovery")) {
                nbt.putInt("guardian_recovery_level", level + 1);
            }
        }
    }

    public static void updateAttributes(ServerPlayerEntity player) {
        NbtCompound nbt = player.getPersistentData();
        
        EntityAttributeInstance speed = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        speed.removeModifier(SPEED_MODIFIER_ID);
        speed.addTemporaryModifier(new EntityAttributeModifier(Identifier.of("guardian_mod", "altar_speed"), nbt.getInt("guardian_speed_level") * 0.02, EntityAttributeModifier.Operation.ADD_VALUE));

        EntityAttributeInstance armor = player.getAttributeInstance(EntityAttributes.GENERIC_ARMOR);
        armor.removeModifier(PROTECTION_MODIFIER_ID);
        armor.addTemporaryModifier(new EntityAttributeModifier(Identifier.of("guardian_mod", "altar_protection"), nbt.getInt("guardian_protection_level"), EntityAttributeModifier.Operation.ADD_VALUE));

        EntityAttributeInstance damage = player.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        damage.removeModifier(DAMAGE_MODIFIER_ID);
        damage.addTemporaryModifier(new EntityAttributeModifier(Identifier.of("guardian_mod", "altar_damage"), nbt.getInt("guardian_damage_level"), EntityAttributeModifier.Operation.ADD_VALUE));
    }

    private static List<BlockPos> getNearbyAltars(World world, BlockPos pos) {
        List<BlockPos> list = new ArrayList<>();
        for (int x = -5; x <= 5; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -5; z <= 5; z++) {
                    BlockPos p = pos.add(x, y, z);
                    if (world.getBlockState(p).getBlock() instanceof AltarBlock) {
                        list.add(p);
                    }
                }
            }
        }
        return list;
    }
}
