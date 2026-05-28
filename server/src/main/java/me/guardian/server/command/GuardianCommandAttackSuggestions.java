package me.guardian.server.command;

import me.guardian.GuardianMod;

import java.lang.reflect.Field;
import java.util.Arrays;

public final class GuardianCommandAttackSuggestions {
    private static final String[] OVERWORLD_ATTACK_IDS = {
            "right_hand_wave",
            "left_hand_wave",
            "two_hand_wave",
            "hands_slam_line",
            "stomp_players",
            "bomb_traps",
            "statue_revival",
            "healing_shield"
    };

    private GuardianCommandAttackSuggestions() {
    }

    public static void apply() {
        try {
            Field field = GuardianCommand.class.getDeclaredField("ATTACK_ID_SUGGESTIONS");
            field.setAccessible(true);
            String[] suggestions = (String[]) field.get(null);
            Arrays.fill(suggestions, OVERWORLD_ATTACK_IDS[OVERWORLD_ATTACK_IDS.length - 1]);
            System.arraycopy(OVERWORLD_ATTACK_IDS, 0, suggestions, 0, Math.min(OVERWORLD_ATTACK_IDS.length, suggestions.length));
        } catch (ReflectiveOperationException | RuntimeException e) {
            GuardianMod.LOGGER.warn("Failed to apply guardian boss attack command suggestions", e);
        }
    }
}
