package dev.joshlucem.nowquiz.quiz;

import java.util.UUID;

/**
 * Captures a player's single submission within a round.
 */
public record PlayerAnswer(
    UUID playerId,
    String playerName,
    String rawInput,
    long submittedAtMillis,
    long responseTimeMillis,
    boolean correct,
    boolean rewardEligible
) {
}
