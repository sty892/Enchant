package me.guardian.entity;

import net.minecraft.world.BossEvent;

public enum NetherGuardianPhase {
    ONE(1, BossEvent.BossBarColor.RED, 0.30D, 20.0D),
    TWO(2, BossEvent.BossBarColor.YELLOW, 0.33D, 23.0D),
    THREE(3, BossEvent.BossBarColor.BLUE, 0.38D, 27.0D);

    private final int id;
    private final BossEvent.BossBarColor bossBarColor;
    private final double movementSpeed;
    private final double attackDamage;

    NetherGuardianPhase(int id, BossEvent.BossBarColor bossBarColor, double movementSpeed, double attackDamage) {
        this.id = id;
        this.bossBarColor = bossBarColor;
        this.movementSpeed = movementSpeed;
        this.attackDamage = attackDamage;
    }

    public int id() {
        return id;
    }

    public BossEvent.BossBarColor bossBarColor() {
        return bossBarColor;
    }

    public double movementSpeed() {
        return movementSpeed;
    }

    public double attackDamage() {
        return attackDamage;
    }

    public static NetherGuardianPhase fromHealth(float healthRatio) {
        if (healthRatio <= 0.33F) {
            return THREE;
        }
        if (healthRatio <= 0.66F) {
            return TWO;
        }
        return ONE;
    }

    public static NetherGuardianPhase byId(int id) {
        return switch (id) {
            case 2 -> TWO;
            case 3 -> THREE;
            default -> ONE;
        };
    }
}
