package me.guardian.dimension;

import me.guardian.GuardianMod;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.Level;

public final class GuardianDimensions {
    public static final ResourceKey<Level> GUARDIAN_DIMENSION = ResourceKey.create(
            Registries.DIMENSION,
            Identifier.fromNamespaceAndPath(GuardianMod.MOD_ID, "guardian_dimension")
    );
    public static final ResourceKey<Level> DEBUG_DIMENSION = ResourceKey.create(
            Registries.DIMENSION,
            Identifier.fromNamespaceAndPath(GuardianMod.MOD_ID, "debug_dimension")
    );

    private GuardianDimensions() {
    }
}
