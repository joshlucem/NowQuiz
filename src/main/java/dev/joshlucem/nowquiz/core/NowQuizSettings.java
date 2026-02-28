package dev.joshlucem.nowquiz.core;

import dev.joshlucem.nowquiz.util.BroadcastScope;

/**
 * Immutable runtime view of the main configuration.
 */
public record NowQuizSettings(
    String language,
    boolean enabled,
    boolean autoEnabled,
    int autoIntervalSeconds,
    int roundTimeLimitSeconds,
    boolean allowMultipleWinners,
    boolean allowClickAnswers,
    boolean allowChatAnswers,
    String chatPrefix,
    long answerCooldownMs,
    long minHumanMs,
    boolean avoidRepeats,
    int repeatCooldown,
    boolean hookVault,
    long minOnlineSeconds,
    boolean debug,
    BroadcastScope broadcastScope,
    String broadcastPermission,
    String defaultWorldName
) {
}
