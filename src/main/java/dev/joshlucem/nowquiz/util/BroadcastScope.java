package dev.joshlucem.nowquiz.util;

/**
 * Defines who receives a quiz round broadcast.
 */
public enum BroadcastScope {
    GLOBAL,
    WORLD,
    PERMISSION;

    public static BroadcastScope fromConfig(String raw) {
        if (raw == null || raw.isBlank()) {
            return GLOBAL;
        }

        try {
            return BroadcastScope.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return GLOBAL;
        }
    }
}
