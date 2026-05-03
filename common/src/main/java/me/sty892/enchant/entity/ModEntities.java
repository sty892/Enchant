package me.sty892.enchant.entity;

import me.sty892.enchant.GuardianModCommon;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModEntities {
    public static final EntityType<OverworldGuardianEntity> OVERWORLD_GUARDIAN = Registry.register(
            Registries.ENTITY_TYPE,
            Identifier.of(GuardianModCommon.MOD_ID, "boss_overworld"),
            EntityType.Builder.create(OverworldGuardianEntity::new, SpawnGroup.MONSTER)
                    .dimensions(2f, 4f).build()
    );

    public static final EntityType<NetherGuardianEntity> NETHER_GUARDIAN = Registry.register(
            Registries.ENTITY_TYPE,
            Identifier.of(GuardianModCommon.MOD_ID, "boss_nether"),
            EntityType.Builder.create(NetherGuardianEntity::new, SpawnGroup.MONSTER)
                    .dimensions(2.5f, 5f).build()
    );

    public static final EntityType<GenericBossEntity> GENERIC_BOSS = Registry.register(
            Registries.ENTITY_TYPE,
            Identifier.of(GuardianModCommon.MOD_ID, "boss_generic"),
            EntityType.Builder.create(GenericBossEntity::new, SpawnGroup.MONSTER)
                    .dimensions(1.5f, 3f).build()
    );

    public static void registerModEntities() {
        FabricDefaultAttributeRegistry.register(OVERWORLD_GUARDIAN, OverworldGuardianEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(NETHER_GUARDIAN, NetherGuardianEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(GENERIC_BOSS, GenericBossEntity.createAttributes());
    }
}
