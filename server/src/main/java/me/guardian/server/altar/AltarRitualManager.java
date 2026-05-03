package me.guardian.server.altar;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import me.guardian.GuardianMod;
import me.guardian.block.AltarBlock;
import me.guardian.block.ModBlocks;
import me.guardian.block.entity.AltarBlockEntity;
import me.guardian.config.ConfigManager;
import me.guardian.server.state.GuardianWorldState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public final class AltarRitualManager {
    private static final Gson GSON = new Gson();
    private static final int RITUAL_TICKS = 100;
    private static final double MAX_DISTANCE_SQR = 25.0D;
    private static final List<ActiveRitual> ACTIVE_RITUALS = new ArrayList<>();

    private AltarRitualManager() {
    }

    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(AltarRitualManager::tick);
    }

    public static InteractionResult tryActivateRitual(ServerLevel level, BlockPos corePos, ServerPlayer player, ItemStack heldStack) {
        if (!AltarBlock.isGuardianFragment(heldStack)) {
            return InteractionResult.PASS;
        }

        if (!level.getBlockState(player.blockPosition()).is(ModBlocks.ALTAR_CORE)) {
            player.displayClientMessage(Component.literal("Встаньте на altar_core для начала ритуала"), true);
            return InteractionResult.SUCCESS;
        }

        List<BlockPos> matchedAltars = findMatchedAltars(level, corePos, player.getUUID());
        if (matchedAltars.isEmpty()) {
            player.displayClientMessage(Component.literal("Нет алтарей с вашим фрагментом рядом"), true);
            return InteractionResult.SUCCESS;
        }

        for (ActiveRitual ritual : ACTIVE_RITUALS) {
            if (ritual.ownerUuid.equals(player.getUUID())) {
                player.displayClientMessage(Component.literal("Ритуал уже выполняется"), true);
                return InteractionResult.SUCCESS;
            }
        }

        for (BlockPos altarPos : matchedAltars) {
            altar(level, altarPos).ifPresent(altar -> {
                altar.setActive(true);
                altar.setRitualTicks(0);
                level.sendBlockUpdated(altarPos, level.getBlockState(altarPos), level.getBlockState(altarPos), 3);
            });
        }

        ACTIVE_RITUALS.add(new ActiveRitual(level.dimension().identifier().toString(), corePos.immutable(), player.getUUID(), List.copyOf(matchedAltars), 0));
        player.displayClientMessage(Component.literal("Ритуал начат"), true);
        return InteractionResult.SUCCESS;
    }

    private static void tick(MinecraftServer server) {
        Iterator<ActiveRitual> iterator = ACTIVE_RITUALS.iterator();
        while (iterator.hasNext()) {
            ActiveRitual ritual = iterator.next();
            ServerLevel level = findLevel(server, ritual.levelId);
            ServerPlayer player = server.getPlayerList().getPlayer(ritual.ownerUuid);
            if (level == null || player == null) {
                clearRitual(level, ritual, false);
                iterator.remove();
                continue;
            }

            if (player.distanceToSqr(Vec3.atCenterOf(ritual.corePos)) > MAX_DISTANCE_SQR) {
                clearRitual(level, ritual, false);
                player.displayClientMessage(Component.literal("Ритуал отменён"), true);
                iterator.remove();
                continue;
            }

            ritual.ticks++;
            for (BlockPos altarPos : ritual.altarPositions) {
                altar(level, altarPos).ifPresent(altar -> {
                    altar.setRitualTicks(ritual.ticks);
                    spawnBeam(level, altarPos, player);
                });
            }

            if (ritual.ticks >= RITUAL_TICKS) {
                finishRitual(level, player, ritual);
                iterator.remove();
            }
        }
    }

    private static List<BlockPos> findMatchedAltars(ServerLevel level, BlockPos corePos, UUID ownerUuid) {
        List<BlockPos> result = new ArrayList<>();
        BlockPos.betweenClosedStream(corePos.offset(-5, -5, -5), corePos.offset(5, 5, 5)).forEach(pos -> {
            BlockStateMatch match = matchAltar(level, pos, ownerUuid);
            if (match != null) {
                result.add(pos.immutable());
            }
        });
        return result;
    }

    private static BlockStateMatch matchAltar(ServerLevel level, BlockPos pos, UUID ownerUuid) {
        AltarUpgradeType type = AltarUpgradeType.fromBlock(level.getBlockState(pos).getBlock());
        if (type == null) {
            return null;
        }

        return altar(level, pos)
                .filter(altar -> !altar.getFragment().isEmpty())
                .filter(altar -> ownerUuid.equals(altar.getOwnerUuid()))
                .map(altar -> new BlockStateMatch(type, altar))
                .orElse(null);
    }

    private static java.util.Optional<AltarBlockEntity> altar(ServerLevel level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof AltarBlockEntity altar) {
            return java.util.Optional.of(altar);
        }
        return java.util.Optional.empty();
    }

    private static void finishRitual(ServerLevel level, ServerPlayer player, ActiveRitual ritual) {
        AltarConfig config = readConfig();
        boolean stageTwo = GuardianWorldState.get(level).netherBossDefeated;
        boolean maxReached = false;

        for (BlockPos altarPos : ritual.altarPositions) {
            AltarUpgradeType type = AltarUpgradeType.fromBlock(level.getBlockState(altarPos).getBlock());
            if (type == null) {
                continue;
            }

            AltarBlockEntity altar = altar(level, altarPos).orElse(null);
            if (altar == null || altar.getFragment().isEmpty()) {
                continue;
            }

            int current = GuardianPlayerUpgrades.get(player).get(type);
            int max = config.maxFor(type, stageTwo);
            if (current < max) {
                GuardianPlayerUpgrades.applyUpgrade(player, type, current + 1);
                altar.setFragment(ItemStack.EMPTY);
                altar.setOwnerUuid(null);
                altar.setActive(false);
                altar.setRitualTicks(0);
                level.sendBlockUpdated(altarPos, level.getBlockState(altarPos), level.getBlockState(altarPos), 3);
            } else {
                altar.setActive(false);
                altar.setRitualTicks(0);
                maxReached = true;
            }
        }

        if (maxReached) {
            player.displayClientMessage(Component.literal("Достигнут максимум для текущей стадии"), true);
        }
    }

    private static void clearRitual(ServerLevel level, ActiveRitual ritual, boolean dropFragments) {
        if (level == null) {
            return;
        }

        for (BlockPos altarPos : ritual.altarPositions) {
            altar(level, altarPos).ifPresent(altar -> {
                if (dropFragments && !altar.getFragment().isEmpty()) {
                    ItemEntity item = new ItemEntity(level, altarPos.getX() + 0.5D, altarPos.getY() + 1.0D, altarPos.getZ() + 0.5D, altar.getFragment());
                    level.addFreshEntity(item);
                    altar.setFragment(ItemStack.EMPTY);
                    altar.setOwnerUuid(null);
                }
                altar.setActive(false);
                altar.setRitualTicks(0);
                level.sendBlockUpdated(altarPos, level.getBlockState(altarPos), level.getBlockState(altarPos), 3);
            });
        }
    }

    private static void spawnBeam(ServerLevel level, BlockPos altarPos, ServerPlayer player) {
        Vec3 start = Vec3.atCenterOf(altarPos).add(0.0D, 0.7D, 0.0D);
        Vec3 end = player.position().add(0.0D, 1.0D, 0.0D);
        Vec3 step = end.subtract(start).scale(1.0D / 10.0D);
        for (int i = 0; i <= 10; i++) {
            Vec3 point = start.add(step.scale(i));
            level.sendParticles(ParticleTypes.CRIT, point.x, point.y, point.z, 1, 0.02D, 0.02D, 0.02D, 0.0D);
            if (i % 2 == 0) {
                level.sendParticles(ParticleTypes.ENCHANT, point.x, point.y, point.z, 1, 0.02D, 0.02D, 0.02D, 0.0D);
            }
        }
    }

    private static AltarConfig readConfig() {
        try {
            JsonObject root = GSON.fromJson(ConfigManager.readRaw("altar_config.json"), JsonObject.class);
            return AltarConfig.from(root);
        } catch (IOException | RuntimeException e) {
            GuardianMod.LOGGER.warn("Failed to read altar_config.json, using defaults", e);
            return AltarConfig.defaults();
        }
    }

    private static ServerLevel findLevel(MinecraftServer server, String levelId) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().identifier().toString().equals(levelId)) {
                return level;
            }
        }
        return null;
    }

    private record BlockStateMatch(AltarUpgradeType type, AltarBlockEntity altar) {
    }

    private static final class ActiveRitual {
        private final String levelId;
        private final BlockPos corePos;
        private final UUID ownerUuid;
        private final List<BlockPos> altarPositions;
        private int ticks;

        private ActiveRitual(String levelId, BlockPos corePos, UUID ownerUuid, List<BlockPos> altarPositions, int ticks) {
            this.levelId = levelId;
            this.corePos = corePos;
            this.ownerUuid = ownerUuid;
            this.altarPositions = altarPositions;
            this.ticks = ticks;
        }
    }

    private record AltarConfig(int stageOneSpeed, int stageOneProtection, int stageOneDamage, int stageOneRecovery,
                               int stageTwoSpeed, int stageTwoProtection, int stageTwoDamage, int stageTwoRecovery) {
        static AltarConfig defaults() {
            return new AltarConfig(3, 3, 3, 3, 7, 7, 7, 7);
        }

        static AltarConfig from(JsonObject root) {
            AltarConfig defaults = defaults();
            if (root == null) {
                return defaults;
            }
            JsonObject stageOne = root.has("stage_1") ? root.getAsJsonObject("stage_1") : new JsonObject();
            JsonObject stageTwo = root.has("stage_2") ? root.getAsJsonObject("stage_2") : new JsonObject();
            return new AltarConfig(
                    readMax(stageOne, AltarUpgradeType.SPEED, defaults.stageOneSpeed),
                    readMax(stageOne, AltarUpgradeType.PROTECTION, defaults.stageOneProtection),
                    readMax(stageOne, AltarUpgradeType.DAMAGE, defaults.stageOneDamage),
                    readMax(stageOne, AltarUpgradeType.RECOVERY, defaults.stageOneRecovery),
                    readMax(stageTwo, AltarUpgradeType.SPEED, defaults.stageTwoSpeed),
                    readMax(stageTwo, AltarUpgradeType.PROTECTION, defaults.stageTwoProtection),
                    readMax(stageTwo, AltarUpgradeType.DAMAGE, defaults.stageTwoDamage),
                    readMax(stageTwo, AltarUpgradeType.RECOVERY, defaults.stageTwoRecovery)
            );
        }

        int maxFor(AltarUpgradeType type, boolean stageTwo) {
            return switch (type) {
                case SPEED -> stageTwo ? stageTwoSpeed : stageOneSpeed;
                case PROTECTION -> stageTwo ? stageTwoProtection : stageOneProtection;
                case DAMAGE -> stageTwo ? stageTwoDamage : stageOneDamage;
                case RECOVERY -> stageTwo ? stageTwoRecovery : stageOneRecovery;
            };
        }

        private static int readMax(JsonObject object, AltarUpgradeType type, int fallback) {
            return object.has(type.maxConfigKey()) ? object.get(type.maxConfigKey()).getAsInt() : fallback;
        }
    }
}
