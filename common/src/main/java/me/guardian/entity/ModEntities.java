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
    public static final ResourceKey<EntityType<?>> CAMERA_MARKER_KEY = ResourceKey.create(
            Registries.ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(GuardianMod.MOD_ID, "camera_marker")
    );
    public static final ResourceKey<EntityType<?>> CEILING_FALLING_BLOCK_KEY = ResourceKey.create(
            Registries.ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(GuardianMod.MOD_ID, "ceiling_falling_block")
    );
    public static final ResourceKey<EntityType<?>> BOMB_TRAP_KEY = ResourceKey.create(
            Registries.ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(GuardianMod.MOD_ID, "bomb_trap")
    );
    public static final ResourceKey<EntityType<?>> TEMPLE_STATUE_KEY = ResourceKey.create(
            Registries.ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(GuardianMod.MOD_ID, "temple_statue")
    );
    public static final ResourceKey<EntityType<?>> HEALING_SHIELD_KEY = ResourceKey.create(
            Registries.ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(GuardianMod.MOD_ID, "healing_shield")
    );
    public static final ResourceKey<EntityType<?>> TEMPLE_WALL_SEGMENT_KEY = ResourceKey.create(
            Registries.ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(GuardianMod.MOD_ID, "temple_wall_segment")
    );
    public static final ResourceKey<EntityType<?>> VINE_LASH_KEY = ResourceKey.create(
            Registries.ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(GuardianMod.MOD_ID, "vine_lash")
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
    public static final EntityType<CameraMarkerEntity> CAMERA_MARKER = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            CAMERA_MARKER_KEY,
            EntityType.Builder.of(CameraMarkerEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .build(CAMERA_MARKER_KEY)
    );
    public static final EntityType<CeilingFallingBlockEntity> CEILING_FALLING_BLOCK = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            CEILING_FALLING_BLOCK_KEY,
            EntityType.Builder.<CeilingFallingBlockEntity>of(CeilingFallingBlockEntity::new, MobCategory.MISC)
                    .sized(0.98F, 0.98F)
                    .build(CEILING_FALLING_BLOCK_KEY)
    );
    public static final EntityType<BombTrapEntity> BOMB_TRAP = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            BOMB_TRAP_KEY,
            EntityType.Builder.<BombTrapEntity>of(BombTrapEntity::new, MobCategory.MISC)
                    .sized(0.4F, 0.4F)
                    .build(BOMB_TRAP_KEY)
    );
    public static final EntityType<TempleStatueEntity> TEMPLE_STATUE = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            TEMPLE_STATUE_KEY,
            EntityType.Builder.<TempleStatueEntity>of(TempleStatueEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F)
                    .build(TEMPLE_STATUE_KEY)
    );
    public static final EntityType<HealingShieldEntity> HEALING_SHIELD = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            HEALING_SHIELD_KEY,
            EntityType.Builder.<HealingShieldEntity>of(HealingShieldEntity::new, MobCategory.MISC)
                    .sized(2.8F, 4.2F)
                    .build(HEALING_SHIELD_KEY)
    );
    public static final EntityType<TempleWallSegmentEntity> TEMPLE_WALL_SEGMENT = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            TEMPLE_WALL_SEGMENT_KEY,
            EntityType.Builder.<TempleWallSegmentEntity>of(TempleWallSegmentEntity::new, MobCategory.MONSTER)
                    .sized(0.98F, 3.0F)
                    .build(TEMPLE_WALL_SEGMENT_KEY)
    );
    public static final EntityType<VineLashEntity> VINE_LASH = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            VINE_LASH_KEY,
            EntityType.Builder.<VineLashEntity>of(VineLashEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F)
                    .clientTrackingRange(64)
                    .updateInterval(1)
                    .build(VINE_LASH_KEY)
    );

    public static void initialize() {
        FabricDefaultAttributeRegistry.register(OVERWORLD_GUARDIAN, OverworldGuardianEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(NETHER_GUARDIAN, NetherGuardianEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(GENERIC_BOSS, GenericBossEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(TEMPLE_STATUE, TempleStatueEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(TEMPLE_WALL_SEGMENT, TempleWallSegmentEntity.createAttributes());
        GuardianMod.LOGGER.info("Registering entities for " + GuardianMod.MOD_ID);
    }
}
