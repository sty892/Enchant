package me.guardian.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class OverworldGuardianThreatTable {
    private static final double THREAT_SCAN_RANGE = 32.0D;
    private static final double THREAT_SCAN_RANGE_SQR = THREAT_SCAN_RANGE * THREAT_SCAN_RANGE;
    private static final float MIN_AGGRO_THREAT = 8.0F;
    private static final int AGGRO_MEMORY_TICKS = 240;
    private static final int TARGET_SWITCH_MARGIN = 18;

    private final Map<UUID, ThreatEntry> entries = new HashMap<>();
    private UUID lastAttacker;
    private int lastAttackTick;

    public void recordDamage(ServerPlayer player, float amount, int gameTick) {
        ThreatEntry entry = entries.computeIfAbsent(player.getUUID(), ignored -> new ThreatEntry());
        entry.threat += 20.0F + amount * 8.0F;
        entry.lastSeenTick = gameTick;
        lastAttacker = player.getUUID();
        lastAttackTick = gameTick;
    }

    public void tick(OverworldGuardianEntity boss, ServerLevel level) {
        int gameTick = boss.tickCount;
        for (ServerPlayer player : level.players()) {
            if (!isValidPlayer(player) || player.distanceToSqr(boss) > THREAT_SCAN_RANGE_SQR) {
                continue;
            }
            double distance = Math.sqrt(player.distanceToSqr(boss));
            ThreatEntry entry = entries.computeIfAbsent(player.getUUID(), ignored -> new ThreatEntry());
            entry.threat += (float) Math.max(0.0D, (THREAT_SCAN_RANGE - distance) * 0.035D);
            entry.lastSeenTick = gameTick;
        }

        entries.entrySet().removeIf(entry -> {
            entry.getValue().threat *= 0.992F;
            return entry.getValue().threat < 1.0F && gameTick - entry.getValue().lastSeenTick > AGGRO_MEMORY_TICKS;
        });
    }

    public LivingEntity chooseTarget(OverworldGuardianEntity boss, ServerLevel level) {
        ServerPlayer current = boss.getTarget() instanceof ServerPlayer player ? player : null;
        ScoredPlayer best = scoredPlayers(boss, level, THREAT_SCAN_RANGE)
                .stream()
                .filter(scored -> isAggroedPlayer(boss, scored.player(), entries.get(scored.player().getUUID()), boss.tickCount))
                .max(Comparator.comparingDouble(ScoredPlayer::score))
                .orElse(null);
        if (best == null) {
            return null;
        }
        if (current == null || !isValidPlayer(current) || current.distanceToSqr(boss) > THREAT_SCAN_RANGE_SQR) {
            return best.player();
        }

        double currentScore = scorePlayer(boss, current, entries.get(current.getUUID()));
        return best.score() > currentScore + TARGET_SWITCH_MARGIN ? best.player() : current;
    }

    public List<ServerPlayer> topAggroedPlayers(OverworldGuardianEntity boss, ServerLevel level, double range, int maxTargets) {
        int gameTick = boss.tickCount;
        return scoredPlayers(boss, level, range)
                .stream()
                .filter(scored -> {
                    ThreatEntry entry = entries.get(scored.player().getUUID());
                    return isAggroedPlayer(boss, scored.player(), entry, gameTick);
                })
                .sorted(Comparator.comparingDouble(ScoredPlayer::score).reversed())
                .limit(maxTargets)
                .map(ScoredPlayer::player)
                .toList();
    }

    private List<ScoredPlayer> scoredPlayers(OverworldGuardianEntity boss, ServerLevel level, double range) {
        double rangeSqr = range * range;
        List<ScoredPlayer> players = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (!isValidPlayer(player) || player.distanceToSqr(boss) > rangeSqr || !boss.isNearHome(player, 8.0D)) {
                continue;
            }
            ThreatEntry entry = entries.get(player.getUUID());
            if (entry == null && player != boss.getTarget()) {
                continue;
            }
            players.add(new ScoredPlayer(player, scorePlayer(boss, player, entry)));
        }
        return players;
    }

    private double scorePlayer(OverworldGuardianEntity boss, ServerPlayer player, ThreatEntry entry) {
        double distance = Math.sqrt(player.distanceToSqr(boss));
        double score = entry == null ? 0.0D : entry.threat;
        score += Math.max(0.0D, 22.0D - distance) * 1.6D;
        if (player.getUUID().equals(lastAttacker) && boss.tickCount - lastAttackTick < 100) {
            score += 25.0D;
        }
        if (player == boss.getTarget()) {
            score += 10.0D;
        }
        return score;
    }

    private boolean isAggroedPlayer(OverworldGuardianEntity boss, ServerPlayer player, ThreatEntry entry, int gameTick) {
        if (entry == null) {
            return player == boss.getTarget();
        }
        return entry.threat >= MIN_AGGRO_THREAT
                || player == boss.getTarget()
                || player.getUUID().equals(lastAttacker) && gameTick - lastAttackTick < AGGRO_MEMORY_TICKS;
    }

    private static boolean isValidPlayer(ServerPlayer player) {
        return player.isAlive() && !player.isSpectator() && !player.isCreative();
    }

    private static final class ThreatEntry {
        private float threat;
        private int lastSeenTick;
    }

    private record ScoredPlayer(ServerPlayer player, double score) {
    }
}
