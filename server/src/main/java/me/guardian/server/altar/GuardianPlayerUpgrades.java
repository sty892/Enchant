package me.guardian.server.altar;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import me.guardian.GuardianMod;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

public record GuardianPlayerUpgrades(int speed, int protection, int damage, int recovery) {
    public static final GuardianPlayerUpgrades EMPTY = new GuardianPlayerUpgrades(0, 0, 0, 0);
    public static final Codec<GuardianPlayerUpgrades> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("guardian_speed_level", 0).forGetter(GuardianPlayerUpgrades::speed),
            Codec.INT.optionalFieldOf("guardian_protection_level", 0).forGetter(GuardianPlayerUpgrades::protection),
            Codec.INT.optionalFieldOf("guardian_damage_level", 0).forGetter(GuardianPlayerUpgrades::damage),
            Codec.INT.optionalFieldOf("guardian_recovery_level", 0).forGetter(GuardianPlayerUpgrades::recovery)
    ).apply(instance, GuardianPlayerUpgrades::new));
    public static final AttachmentType<GuardianPlayerUpgrades> TYPE = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(GuardianMod.MOD_ID, "player_upgrades"),
            builder -> builder.persistent(CODEC).copyOnDeath().initializer(() -> EMPTY)
    );

    public int get(AltarUpgradeType type) {
        return switch (type) {
            case SPEED -> speed;
            case PROTECTION -> protection;
            case DAMAGE -> damage;
            case RECOVERY -> recovery;
        };
    }

    public GuardianPlayerUpgrades withLevel(AltarUpgradeType type, int level) {
        return switch (type) {
            case SPEED -> new GuardianPlayerUpgrades(level, protection, damage, recovery);
            case PROTECTION -> new GuardianPlayerUpgrades(speed, level, damage, recovery);
            case DAMAGE -> new GuardianPlayerUpgrades(speed, protection, level, recovery);
            case RECOVERY -> new GuardianPlayerUpgrades(speed, protection, damage, level);
        };
    }

    public static GuardianPlayerUpgrades get(ServerPlayer player) {
        return ((AttachmentTarget) player).getAttachedOrCreate(TYPE);
    }

    public static int getRecoveryLevel(ServerPlayer player) {
        return get(player).recovery();
    }

    public static boolean applyUpgrade(ServerPlayer player, AltarUpgradeType type, int level) {
        GuardianPlayerUpgrades upgrades = get(player).withLevel(type, level);
        ((AttachmentTarget) player).setAttached(TYPE, upgrades);
        applyAttributeModifier(player, type, level);
        return true;
    }

    public static void reapplyAll(ServerPlayer player) {
        GuardianPlayerUpgrades upgrades = get(player);
        applyAttributeModifier(player, AltarUpgradeType.SPEED, upgrades.speed());
        applyAttributeModifier(player, AltarUpgradeType.PROTECTION, upgrades.protection());
        applyAttributeModifier(player, AltarUpgradeType.DAMAGE, upgrades.damage());
    }

    private static void applyAttributeModifier(ServerPlayer player, AltarUpgradeType type, int level) {
        if (type.attribute() == null) {
            return;
        }

        AttributeInstance instance = player.getAttribute(type.attribute());
        if (instance == null) {
            return;
        }

        instance.removeModifier(type.modifierId());
        if (level > 0) {
            instance.addOrReplacePermanentModifier(new AttributeModifier(type.modifierId(), type.perLevel() * level, AttributeModifier.Operation.ADD_VALUE));
        }
    }
}
