package dev.joshlucem.nowquiz.util;

/**
 * Supported leaderboard metrics exposed by the top command.
 */
public enum LeaderboardMetric {
    WINS("wins", "wins"),
    STREAK("best_streak", "streak");

    private final String column;
    private final String displayName;

    LeaderboardMetric(String column, String displayName) {
        this.column = column;
        this.displayName = displayName;
    }

    public String column() {
        return this.column;
    }

    public String displayName() {
        return this.displayName;
    }

    public static LeaderboardMetric fromInput(String raw) {
        if (raw == null || raw.isBlank()) {
            return WINS;
        }

        String normalized = raw.trim().toLowerCase();
        for (LeaderboardMetric metric : values()) {
            if (metric.displayName.equals(normalized)) {
                return metric;
            }
        }
        return WINS;
    }
}
