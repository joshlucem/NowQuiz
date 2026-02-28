package dev.joshlucem.nowquiz.quiz;

import java.util.UUID;

/**
 * Cached per-player statistics used for both commands and batch persistence.
 */
public final class PlayerStats {

    private final UUID playerId;
    private String lastKnownName;
    private long plays;
    private long wins;
    private long losses;
    private long bestStreak;
    private long currentStreak;
    private long totalResponseMs;
    private long totalAnswers;

    public PlayerStats(UUID playerId, String lastKnownName) {
        this(playerId, lastKnownName, 0L, 0L, 0L, 0L, 0L, 0L, 0L);
    }

    public PlayerStats(
        UUID playerId,
        String lastKnownName,
        long plays,
        long wins,
        long losses,
        long bestStreak,
        long currentStreak,
        long totalResponseMs,
        long totalAnswers
    ) {
        this.playerId = playerId;
        this.lastKnownName = lastKnownName;
        this.plays = plays;
        this.wins = wins;
        this.losses = losses;
        this.bestStreak = bestStreak;
        this.currentStreak = currentStreak;
        this.totalResponseMs = totalResponseMs;
        this.totalAnswers = totalAnswers;
    }

    public UUID playerId() {
        return this.playerId;
    }

    public String lastKnownName() {
        return this.lastKnownName;
    }

    public long plays() {
        return this.plays;
    }

    public long wins() {
        return this.wins;
    }

    public long losses() {
        return this.losses;
    }

    public long bestStreak() {
        return this.bestStreak;
    }

    public long currentStreak() {
        return this.currentStreak;
    }

    public long totalResponseMs() {
        return this.totalResponseMs;
    }

    public long totalAnswers() {
        return this.totalAnswers;
    }

    public double averageResponseMs() {
        if (this.totalAnswers <= 0L) {
            return 0.0D;
        }
        return (double) this.totalResponseMs / (double) this.totalAnswers;
    }

    public void recordResult(String playerName, boolean win, long responseMs) {
        this.lastKnownName = playerName;
        this.plays++;
        this.totalAnswers++;
        this.totalResponseMs += Math.max(0L, responseMs);

        if (win) {
            this.wins++;
            this.currentStreak++;
            this.bestStreak = Math.max(this.bestStreak, this.currentStreak);
            return;
        }

        this.losses++;
        this.currentStreak = 0L;
    }

    public PlayerStats copy() {
        return new PlayerStats(
            this.playerId,
            this.lastKnownName,
            this.plays,
            this.wins,
            this.losses,
            this.bestStreak,
            this.currentStreak,
            this.totalResponseMs,
            this.totalAnswers
        );
    }
}
