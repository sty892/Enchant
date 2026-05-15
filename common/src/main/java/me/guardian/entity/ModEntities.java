package me.guardian.entity;

import me.guardian.GuardianMod;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public class ModEntities {
    public static final ResourceKey<EntityType<?>> OVERWORLD_GUARDIAN_KEY = ResourceKey.create(
            Registries.ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(GuardianMod.MOD_ID, "boss_overworld")
    );
    public static final ResourceKey<EntityType<?>> NETHER_GUARDIAN_KEY = ResourceKey.create(
            Registries.ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(GuardianMod.MOD_ID, "boss_nether")
    );
    public static final ResourceKey<EntityType<?>> GENERIC_BOSS_KEY = ResourceKey.create(
            Registries.ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(GuardianMod.MOD_ID, "boss_generic")
    );
    public static final ResourceKey<EntityType<?>> ALTAR_PLACEMENT_KEY = ResourceKey.create(
            Registries.ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(GuardianMod.MOD_ID, "altar_placement")
    );

    public static final EntityType<OverworldGuardianEntity> OVERWORLD_GUARDIAN = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            OVERWORLD_GUARDIAN_KEY,
            EntityType.Builder.of(OverworldGuardianEntity::new, MobCategory.MONSTER)
                    .sized(2.0f, 4.0f)
                    .build(OVERWORLD_GUARDIAN_KEY)
    );
    public static final EntityType<NetherGuardianEntity> NETHER_GUARDIAN = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            NETHER_GUARDIAN_KEY,
            EntityType.Builder.of(NetherGuardianEntity::new, MobCategory.MONSTER)
                    .sized(2.0f, 4.0f)
                    .fireImmune()
                    .build(NETHER_GUARDIAN_KEY)
    );
    public static final EntityType<GenericBossEntity> GENERIC_BOSS = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            GENERIC_BOSS_KEY,
            EntityType.Builder.of(GenericBossEntity::new, MobCategory.MONSTER)
                    .sized(1.4f, 2.8f)
                    .build(GENERIC_BOSS_KEY)
    );
    public static final EntityType<AltarPlacementEntity> ALTAR_PLACEMENT = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            ALTAR_PLACEMENT_KEY,
            EntityType.Builder.of(AltarPlacementEntity::new, MobCategory.MISC)
                    .sized(3.0f, 3.0f)
                    .build(ALTAR_PLACEMENT_KEY)
    );

    public static void initialize() {
        FabricDefaultAttributeRegistry.register(OVERWORLD_GUARDIAN, OverworldGuardianEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(NETHER_GUARDIAN, NetherGuardianEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(GENERIC_BOSS, GenericBossEntity.createAttributes());
        GuardianMod.LOGGER.info("Registering entities for " + GuardianMod.MOD_ID);
    }
}
