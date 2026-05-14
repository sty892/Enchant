package me.guardian.entity;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class SummonedBlockCleaner {
    private static final int CLEANUP_TICKS = 5 * 60 * 20;
    private static final Map<UUID, PendingBlock> PENDING_FALLING_BLOCKS = new HashMap<>();
    private static final Map<BlockKey, PlacedBlock> PLACED_BLOCKS = new HashMap<>();
    private static boolean initialized;

    private SummonedBlockCleaner() {
    }

    public static void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        ServerTickEvents.END_SERVER_TICK.register(SummonedBlockCleaner::tick);
    }

    public static void track(FallingBlockEntity fallingBlock, ServerLevel level) {
        fallingBlock.dropItem = false;
        PENDING_FALLING_BLOCKS.put(fallingBlock.getUUID(), new PendingBlock(
                level.dimension().identifier().toString(),
                fallingBlock.blockPosition().immutable(),
                fallingBlock.getBlockState()
        ));
    }

    private static void tick(MinecraftServer server) {
        tickPendingBlocks(server);
        tickPlacedBlocks(server);
    }

    private static void tickPendingBlocks(MinecraftServer server) {
        Iterator<Map.Entry<UUID, PendingBlock>> iterator = PENDING_FALLING_BLOCKS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, PendingBlock> entry = iterator.next();
            PendingBlock pending = entry.getValue();
            ServerLevel level = findLevel(server, pending.levelId);
            if (level == null) {
                iterator.remove();
                continue;
            }

            Entity entity = level.getEntity(entry.getKey());
            if (entity instanceof FallingBlockEntity fallingBlock && !fallingBlock.isRemoved()) {
                entry.setValue(pending.withLastPos(fallingBlock.blockPosition().immutable()));
                continue;
            }

            BlockPos placedPos = findPlacedBlock(level, pending);
            if (placedPos != null) {
                PLACED_BLOCKS.put(new BlockKey(pending.levelId, placedPos.immutable()), new PlacedBlock(
                        pending.state,
                        server.getTickCount(),
                        breakAnimationId(placedPos)
                ));
            }
            iterator.remove();
        }
    }

    private static void tickPlacedBlocks(MinecraftServer server) {
        Iterator<Map.Entry<BlockKey, PlacedBlock>> iterator = PLACED_BLOCKS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockKey, PlacedBlock> entry = iterator.next();
            BlockKey key = entry.getKey();
            PlacedBlock placed = entry.getValue();
            ServerLevel level = findLevel(server, key.levelId);
            if (level == null || !level.getBlockState(key.pos).is(placed.state.getBlock())) {
                clearBreakProgress(level, placed, key.pos);
                iterator.remove();
                continue;
            }

            int age = server.getTickCount() - placed.placedTick;
            if (age >= CLEANUP_TICKS) {
                clearBreakProgress(level, placed, key.pos);
                level.setBlock(key.pos, Blocks.AIR.defaultBlockState(), 3);
                iterator.remove();
                continue;
            }

            int progress = Math.min(9, Math.max(0, age * 10 / CLEANUP_TICKS));
            level.destroyBlockProgress(placed.breakAnimationId, key.pos, progress);
        }
    }

    private static BlockPos findPlacedBlock(ServerLevel level, PendingBlock pending) {
        for (BlockPos candidate : new BlockPos[]{pending.lastPos, pending.lastPos.below(), pending.lastPos.above()}) {
            if (level.getBlockState(candidate).is(pending.state.getBlock())) {
                return candidate;
            }
        }
        return null;
    }

    private static void clearBreakProgress(ServerLevel level, PlacedBlock placed, BlockPos pos) {
        if (level != null) {
            level.destroyBlockProgress(placed.breakAnimationId, pos, -1);
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

    private static int breakAnimationId(BlockPos pos) {
        return 0x4A000000 ^ pos.hashCode();
    }

    private record PendingBlock(String levelId, BlockPos lastPos, BlockState state) {
        private PendingBlock withLastPos(BlockPos pos) {
            return new PendingBlock(levelId, pos, state);
        }
    }

    private record BlockKey(String levelId, BlockPos pos) {
    }

    private record PlacedBlock(BlockState state, int placedTick, int breakAnimationId) {
    }
}
