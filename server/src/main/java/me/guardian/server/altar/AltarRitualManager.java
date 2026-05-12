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
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class AltarRitualManager {
    private static final Gson GSON = new Gson();
    private static final Map<UUID, SelectedAspect> SELECTED_ASPECTS = new HashMap<>();
    private static final Map<UUID, ActiveRitual> ACTIVE_RITUALS = new HashMap<>();

    private AltarRitualManager() {
    }

    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(AltarRitualManager::tick);
    }

    public static InteractionResult selectAltarAspect(ServerLevel level, BlockPos altarPos, ServerPlayer player, ItemStack heldStack) {
        AltarUpgradeType type = AltarUpgradeType.fromBlock(level.getBlockState(altarPos).getBlock());
        if (type == null) {
            return InteractionResult.PASS;
        }

        AltarConfig config = readConfig();
        BlockPos corePos = findNearestCore(level, altarPos, config.radius());
        if (corePos == null) {
            player.displayClientMessage(Component.translatable("message.guardian_mod.altar.no_core"), true);
            return InteractionResult.SUCCESS;
        }

        int current = GuardianPlayerUpgrades.get(player).get(type);
        int max = config.maxFor(type, GuardianWorldState.get(level).netherBossDefeated);
        if (current >= max) {
            player.displayClientMessage(Component.translatable("message.guardian_mod.altar.stage_max"), true);
            return InteractionResult.SUCCESS;
        }

        AltarBlockEntity altar = altar(level, altarPos);
        if (altar == null) {
            return InteractionResult.PASS;
        }

        if (!altar.getFragment().isEmpty() && !player.getUUID().equals(altar.getOwnerUuid())) {
            player.displayClientMessage(Component.translatable("message.guardian_mod.altar.occupied"), true);
            return InteractionResult.SUCCESS;
        }

        if (altar.getFragment().isEmpty()) {
            if (!AltarBlock.isGuardianFragment(heldStack)) {
                player.displayClientMessage(Component.translatable("message.guardian_mod.altar.hold_fragment"), true);
                return InteractionResult.SUCCESS;
            }
            altar.setFragment(heldStack.copyWithCount(1));
            altar.setOwnerUuid(player.getUUID());
            if (!player.getAbilities().instabuild) {
                heldStack.shrink(1);
            }
        }

        altar.setActive(false);
        altar.setRitualTicks(0);
        level.sendBlockUpdated(altarPos, level.getBlockState(altarPos), level.getBlockState(altarPos), 3);
        SELECTED_ASPECTS.put(player.getUUID(), new SelectedAspect(level.dimension().identifier().toString(), corePos.immutable(), altarPos.immutable(), type));
        player.displayClientMessage(Component.translatable("message.guardian_mod.altar.aspect_selected", Component.translatable(type.translationKey())), true);
        player.sendSystemMessage(Component.translatable("message.guardian_mod.altar.stand_on_core"));
        return InteractionResult.SUCCESS;
    }

    public static InteractionResult tryActivateRitual(ServerLevel level, BlockPos corePos, ServerPlayer player, ItemStack heldStack) {
        SelectedAspect selected = SELECTED_ASPECTS.get(player.getUUID());
        if (selected == null || !selected.levelId.equals(level.dimension().identifier().toString()) || !selected.corePos.equals(corePos)) {
            player.displayClientMessage(Component.translatable("message.guardian_mod.altar.select_aspect_first"), true);
            return InteractionResult.SUCCESS;
        }
        startRitualIfPossible(level, player, selected);
        return InteractionResult.SUCCESS;
    }

    private static void tick(MinecraftServer server) {
        AltarConfig config = readConfig();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!ACTIVE_RITUALS.containsKey(player.getUUID())) {
                SelectedAspect selected = SELECTED_ASPECTS.get(player.getUUID());
                ServerLevel level = selected == null ? null : findLevel(server, selected.levelId);
                if (selected != null && level != null && isStandingOnCore(level, player, selected.corePos)) {
                    startRitualIfPossible(level, player, selected);
                }
            }
        }

        Iterator<Map.Entry<UUID, ActiveRitual>> iterator = ACTIVE_RITUALS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, ActiveRitual> entry = iterator.next();
            ActiveRitual ritual = entry.getValue();
            ServerLevel level = findLevel(server, ritual.levelId);
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (level == null || player == null) {
                clearRitual(level, ritual);
                iterator.remove();
                continue;
            }

            if (!isStandingOnCore(level, player, ritual.corePos)
                    || player.distanceToSqr(Vec3.atCenterOf(ritual.altarPos)) > config.radiusSqr()) {
                clearRitual(level, ritual);
                player.displayClientMessage(Component.translatable("message.guardian_mod.altar.interrupted"), true);
                iterator.remove();
                continue;
            }

            ritual.ticks++;
            AltarBlockEntity altar = altar(level, ritual.altarPos);
            if (altar != null) {
                altar.setRitualTicks(ritual.ticks);
                spawnBeam(level, ritual.altarPos, ritual.corePos, player, ritual.type);
            }

            if (ritual.ticks >= config.ritualTicks()) {
                finishRitual(level, player, ritual, config);
                iterator.remove();
            }
        }
    }

    private static void startRitualIfPossible(ServerLevel level, ServerPlayer player, SelectedAspect selected) {
        if (ACTIVE_RITUALS.containsKey(player.getUUID())) {
            return;
        }

        AltarBlockEntity altar = altar(level, selected.altarPos);
        if (altar == null || altar.getFragment().isEmpty()) {
            player.displayClientMessage(Component.translatable("message.guardian_mod.altar.hold_fragment"), true);
            SELECTED_ASPECTS.remove(player.getUUID());
            return;
        }

        AltarConfig config = readConfig();
        int current = GuardianPlayerUpgrades.get(player).get(selected.type);
        int max = config.maxFor(selected.type, GuardianWorldState.get(level).netherBossDefeated);
        if (current >= max) {
            player.displayClientMessage(Component.translatable("message.guardian_mod.altar.stage_max"), true);
            return;
        }

        altar.setActive(true);
        altar.setRitualTicks(0);
        level.sendBlockUpdated(selected.altarPos, level.getBlockState(selected.altarPos), level.getBlockState(selected.altarPos), 3);
        ACTIVE_RITUALS.put(player.getUUID(), new ActiveRitual(selected.levelId, selected.corePos, selected.altarPos, selected.type, 0));
        player.displayClientMessage(Component.translatable("message.guardian_mod.altar.started"), true);
    }

    private static void finishRitual(ServerLevel level, ServerPlayer player, ActiveRitual ritual, AltarConfig config) {
        AltarBlockEntity altar = altar(level, ritual.altarPos);
        if (altar == null || altar.getFragment().isEmpty()) {
            SELECTED_ASPECTS.remove(player.getUUID());
            return;
        }

        int current = GuardianPlayerUpgrades.get(player).get(ritual.type);
        int max = config.maxFor(ritual.type, GuardianWorldState.get(level).netherBossDefeated);
        if (current >= max) {
            altar.setActive(false);
            altar.setRitualTicks(0);
            player.displayClientMessage(Component.translatable("message.guardian_mod.altar.stage_max"), true);
            return;
        }

        GuardianPlayerUpgrades.applyUpgrade(player, ritual.type, current + 1);
        altar.setFragment(ItemStack.EMPTY);
        altar.setOwnerUuid(null);
        altar.setActive(false);
        altar.setRitualTicks(0);
        SELECTED_ASPECTS.remove(player.getUUID());
        level.sendBlockUpdated(ritual.altarPos, level.getBlockState(ritual.altarPos), level.getBlockState(ritual.altarPos), 3);
        sendStats(player, ritual.type, config);
    }

    private static void clearRitual(ServerLevel level, ActiveRitual ritual) {
        if (level == null) {
            return;
        }
        AltarBlockEntity altar = altar(level, ritual.altarPos);
        if (altar != null) {
            altar.setActive(false);
            altar.setRitualTicks(0);
            level.sendBlockUpdated(ritual.altarPos, level.getBlockState(ritual.altarPos), level.getBlockState(ritual.altarPos), 3);
        }
    }

    public static void sendStats(ServerPlayer player) {
        sendStats(player, null, readConfig());
    }

    private static void sendStats(ServerPlayer player, AltarUpgradeType improved, AltarConfig config) {
        boolean stageTwo = player.level() instanceof ServerLevel level && GuardianWorldState.get(level).netherBossDefeated;
        GuardianPlayerUpgrades upgrades = GuardianPlayerUpgrades.get(player);
        player.sendSystemMessage(Component.translatable("message.guardian_mod.altar.stats_title"));
        sendStatLine(player, AltarUpgradeType.SPEED, upgrades.speed(), config.maxFor(AltarUpgradeType.SPEED, stageTwo), improved);
        sendStatLine(player, AltarUpgradeType.PROTECTION, upgrades.protection(), config.maxFor(AltarUpgradeType.PROTECTION, stageTwo), improved);
        sendStatLine(player, AltarUpgradeType.DAMAGE, upgrades.damage(), config.maxFor(AltarUpgradeType.DAMAGE, stageTwo), improved);
        sendStatLine(player, AltarUpgradeType.RECOVERY, upgrades.recovery(), config.maxFor(AltarUpgradeType.RECOVERY, stageTwo), improved);
    }

    private static void sendStatLine(ServerPlayer player, AltarUpgradeType type, int current, int max, AltarUpgradeType improved) {
        boolean changed = type == improved;
        Component line = Component.empty()
                .append(Component.translatable(type.translationKey()))
                .append(": " + current + "/" + max + (changed ? " (+1)" : ""))
                .withStyle(changed ? ChatFormatting.GREEN : ChatFormatting.GRAY);
        player.sendSystemMessage(line);
    }

    public static void resetPlayerUpgrades(ServerPlayer player) {
        for (AltarUpgradeType type : AltarUpgradeType.values()) {
            GuardianPlayerUpgrades.applyUpgrade(player, type, 0);
        }
        SELECTED_ASPECTS.remove(player.getUUID());
        ACTIVE_RITUALS.remove(player.getUUID());
    }

    private static BlockPos findNearestCore(ServerLevel level, BlockPos altarPos, int radius) {
        BlockPos nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(altarPos.offset(-radius, -radius, -radius), altarPos.offset(radius, radius, radius))) {
            if (!level.getBlockState(pos).is(ModBlocks.ALTAR_CORE)) {
                continue;
            }
            double distance = pos.distSqr(altarPos);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = pos.immutable();
            }
        }
        return nearest;
    }

    private static boolean isStandingOnCore(ServerLevel level, ServerPlayer player, BlockPos corePos) {
        BlockPos feet = player.blockPosition();
        return feet.equals(corePos) && level.getBlockState(feet).is(ModBlocks.ALTAR_CORE)
                || feet.below().equals(corePos) && level.getBlockState(feet.below()).is(ModBlocks.ALTAR_CORE);
    }

    private static AltarBlockEntity altar(ServerLevel level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntity instanceof AltarBlockEntity altar ? altar : null;
    }

    private static void spawnBeam(ServerLevel level, BlockPos altarPos, BlockPos corePos, ServerPlayer player, AltarUpgradeType type) {
        Vec3 start = Vec3.atCenterOf(altarPos).add(0.0D, 0.7D, 0.0D);
        Vec3 end = Vec3.atCenterOf(corePos).add(0.0D, 1.2D, 0.0D);
        Vec3 playerEnd = player.position().add(0.0D, 1.0D, 0.0D);
        spawnLine(level, start, end, type);
        spawnLine(level, end, playerEnd, type);
    }

    private static void spawnLine(ServerLevel level, Vec3 start, Vec3 end, AltarUpgradeType type) {
        Vec3 step = end.subtract(start).scale(1.0D / 12.0D);
        for (int i = 0; i <= 12; i++) {
            Vec3 point = start.add(step.scale(i));
            ParticleOptions particle = particleFor(type, i);
            level.sendParticles(particle, point.x, point.y, point.z, 1, 0.02D, 0.02D, 0.02D, 0.0D);
        }
    }

    private static ParticleOptions particleFor(AltarUpgradeType type, int index) {
        return switch (type) {
            case SPEED -> index % 2 == 0 ? ParticleTypes.END_ROD : ParticleTypes.CLOUD;
            case PROTECTION -> index % 2 == 0 ? ParticleTypes.ENCHANT : ParticleTypes.CRIT;
            case DAMAGE -> index % 2 == 0 ? ParticleTypes.FLAME : ParticleTypes.LAVA;
            case RECOVERY -> index % 2 == 0 ? ParticleTypes.HEART : ParticleTypes.HAPPY_VILLAGER;
        };
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

    private record SelectedAspect(String levelId, BlockPos corePos, BlockPos altarPos, AltarUpgradeType type) {
    }

    private static final class ActiveRitual {
        private final String levelId;
        private final BlockPos corePos;
        private final BlockPos altarPos;
        private final AltarUpgradeType type;
        private int ticks;

        private ActiveRitual(String levelId, BlockPos corePos, BlockPos altarPos, AltarUpgradeType type, int ticks) {
            this.levelId = levelId;
            this.corePos = corePos;
            this.altarPos = altarPos;
            this.type = type;
            this.ticks = ticks;
        }
    }

    private record AltarConfig(int radius, int ritualTicks,
                               int stageOneSpeed, int stageOneProtection, int stageOneDamage, int stageOneRecovery,
                               int stageTwoSpeed, int stageTwoProtection, int stageTwoDamage, int stageTwoRecovery) {
        static AltarConfig defaults() {
            return new AltarConfig(5, 100, 3, 3, 3, 3, 7, 7, 7, 7);
        }

        static AltarConfig from(JsonObject root) {
            AltarConfig defaults = defaults();
            if (root == null) {
                return defaults;
            }
            JsonObject stageOne = root.has("stage_1") ? root.getAsJsonObject("stage_1") : new JsonObject();
            JsonObject stageTwo = root.has("stage_2") ? root.getAsJsonObject("stage_2") : new JsonObject();
            return new AltarConfig(
                    root.has("radius") ? root.get("radius").getAsInt() : defaults.radius,
                    root.has("ritual_ticks") ? root.get("ritual_ticks").getAsInt() : defaults.ritualTicks,
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

        double radiusSqr() {
            return radius * radius;
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
