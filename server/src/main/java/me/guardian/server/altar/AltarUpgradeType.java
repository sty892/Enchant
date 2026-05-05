package me.guardian.server.altar;

import me.guardian.GuardianMod;
import me.guardian.block.ModBlocks;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.Holder;

public enum AltarUpgradeType {
    SPEED("speed", "Скорость", "max_speed", ModBlocks.ALTAR_SPEED, Attributes.MOVEMENT_SPEED, 0.02D),
    PROTECTION("protection", "Защита", "max_protection", ModBlocks.ALTAR_PROTECTION, Attributes.ARMOR, 1.0D),
    DAMAGE("damage", "Урон", "max_damage", ModBlocks.ALTAR_DAMAGE, Attributes.ATTACK_DAMAGE, 1.0D),
    RECOVERY("recovery", "Восстановление", "max_recovery", ModBlocks.ALTAR_RECOVERY, null, 1.0D);

    private final String key;
    private final String displayName;
    private final String maxConfigKey;
    private final Block block;
    private final Holder<Attribute> attribute;
    private final double perLevel;
    private final Identifier modifierId;

    AltarUpgradeType(String key, String displayName, String maxConfigKey, Block block, Holder<Attribute> attribute, double perLevel) {
        this.key = key;
        this.displayName = displayName;
        this.maxConfigKey = maxConfigKey;
        this.block = block;
        this.attribute = attribute;
        this.perLevel = perLevel;
        this.modifierId = Identifier.fromNamespaceAndPath(GuardianMod.MOD_ID, "altar_" + key);
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    public String maxConfigKey() {
        return maxConfigKey;
    }

    public Block block() {
        return block;
    }

    public Holder<Attribute> attribute() {
        return attribute;
    }

    public double perLevel() {
        return perLevel;
    }

    public Identifier modifierId() {
        return modifierId;
    }

    public static AltarUpgradeType fromBlock(Block block) {
        for (AltarUpgradeType type : values()) {
            if (type.block == block) {
                return type;
            }
        }
        return null;
    }
}
