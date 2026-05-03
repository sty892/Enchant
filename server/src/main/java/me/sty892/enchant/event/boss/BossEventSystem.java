package me.sty892.enchant.event.boss;

import me.sty892.enchant.state.GuardianWorldState;
import me.sty892.enchant.util.StructureSpawner;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.border.WorldBorder;
import java.util.Set;
import java.util.UUID;

public class BossEventSystem {
    public static void triggerOnDeath(LivingEntity boss, ServerWorld world, Set<UUID> contributors) {
        String id = Registries.ENTITY_TYPE.getId(boss.getType()).toString();
        BossEventConfig config = BossConfigManager.getConfigForBoss(id);
        if (config == null || config.on_death == null) return;

        BossEventConfig.BossEvent event = config.on_death;
        executeEvent(event, boss, world, contributors);
    }

    public static void triggerOnSpawn(LivingEntity boss, ServerWorld world) {
        String id = Registries.ENTITY_TYPE.getId(boss.getType()).toString();
        BossEventConfig config = BossConfigManager.getConfigForBoss(id);
        if (config == null || config.on_spawn == null) return;

        BossEventConfig.BossEvent event = config.on_spawn;
        executeEvent(event, boss, world, null);
    }

    private static void executeEvent(BossEventConfig.BossEvent event, LivingEntity boss, ServerWorld world, Set<UUID> contributors) {
        if (event.broadcast_title != null) {
            world.getServer().getPlayerManager().broadcast(Text.literal(event.broadcast_title), false);
        }

        if (event.world_border_expand != null) {
            WorldBorder border = world.getWorldBorder();
            border.interpolateSize(border.getSize(), event.world_border_expand.to, event.world_border_expand.duration_seconds * 1000L);
        }

        if (event.set_flag != null) {
            GuardianWorldState state = GuardianWorldState.getServerState(world.getServer());
            if (event.set_flag.equals("overworldBossDefeated")) state.overworldBossDefeated = true;
            if (event.set_flag.equals("netherBossDefeated")) state.netherBossDefeated = true;
            state.markDirty();
        }

        if (event.allow_diamonds) {
            GuardianWorldState state = GuardianWorldState.getServerState(world.getServer());
            state.overworldBossDefeated = true;
            state.markDirty();
        }

        if (event.spawn_structure != null) {
            BlockPos pos = boss.getBlockPos();
            if (event.spawn_structure_offset != null) {
                pos = pos.add(event.spawn_structure_offset.x, event.spawn_structure_offset.y, event.spawn_structure_offset.z);
            }
            StructureSpawner.place(world, pos, event.spawn_structure);
        }

        if (event.give_fragment != null && contributors != null) {
            Identifier fragmentId = Identifier.of(event.give_fragment);
            for (UUID uuid : contributors) {
                ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(uuid);
                if (player != null) {
                    player.getInventory().offerOrDrop(new ItemStack(Registries.ITEM.get(fragmentId)));
                }
            }
        }
    }
}
