package dev.joshlucem.nowquiz.quiz;

import java.util.UUID;

/**
 * Simple leaderboard row returned from storage.
 */
public record LeaderboardEntry(UUID playerId, String playerName, long value) {
}
