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

    public static final EntityType<OverworldGuardianEntity> OVERWORLD_GUARDIAN = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            OVERWORLD_GUARDIAN_KEY,
            EntityType.Builder.of(OverworldGuardianEntity::new, MobCategory.MONSTER)
                    .sized(2.0f, 4.0f)
                    .build(OVERWORLD_GUARDIAN_KEY)
    );

    public static void initialize() {
        FabricDefaultAttributeRegistry.register(OVERWORLD_GUARDIAN, OverworldGuardianEntity.createAttributes());
        GuardianMod.LOGGER.info("Registering entities for " + GuardianMod.MOD_ID);
    }
}
