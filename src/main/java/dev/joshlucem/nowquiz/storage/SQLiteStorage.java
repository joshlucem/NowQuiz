package dev.joshlucem.nowquiz.storage;

import dev.joshlucem.nowquiz.core.PluginLogger;
import dev.joshlucem.nowquiz.quiz.LeaderboardEntry;
import dev.joshlucem.nowquiz.quiz.PlayerStats;
import dev.joshlucem.nowquiz.util.AsyncExecutor;
import dev.joshlucem.nowquiz.util.LeaderboardMetric;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * SQLite-backed storage layer for player stats.
 */
public final class SQLiteStorage {

    private final Path databasePath;
    private final AsyncExecutor executor;
    private final PluginLogger logger;
    private CompletableFuture<Void> readyFuture;

    public SQLiteStorage(Path databasePath, AsyncExecutor executor, PluginLogger logger) {
        this.databasePath = databasePath;
        this.executor = executor;
        this.logger = logger;
        this.readyFuture = CompletableFuture.completedFuture(null);
    }

    public void initialize() {
        this.readyFuture = this.executor.run(() -> {
            try {
                Class.forName("org.sqlite.JDBC");
                Files.createDirectories(this.databasePath.getParent());
            } catch (Exception exception) {
                throw new CompletionException(exception);
            }

            try (Connection connection = this.openConnection();
                 Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS nowquiz_player_stats (
                        player_id TEXT PRIMARY KEY,
                        last_name TEXT NOT NULL,
                        plays INTEGER NOT NULL DEFAULT 0,
                        wins INTEGER NOT NULL DEFAULT 0,
                        losses INTEGER NOT NULL DEFAULT 0,
                        best_streak INTEGER NOT NULL DEFAULT 0,
                        current_streak INTEGER NOT NULL DEFAULT 0,
                        total_response_ms INTEGER NOT NULL DEFAULT 0,
                        total_answers INTEGER NOT NULL DEFAULT 0
                    )
                    """);
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_nowquiz_wins ON nowquiz_player_stats(wins DESC)");
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_nowquiz_streak ON nowquiz_player_stats(best_streak DESC)");
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_nowquiz_name ON nowquiz_player_stats(last_name)");
            } catch (SQLException exception) {
                throw new CompletionException(exception);
            }
        }).whenComplete((unused, throwable) -> {
            if (throwable != null) {
                this.logger.error("Failed to initialize the SQLite database.", unwrap(throwable));
            } else {
                this.logger.debug("SQLite storage initialized at " + this.databasePath + ".");
            }
        });
    }

    public CompletableFuture<Optional<PlayerStats>> loadStats(UUID playerId, String fallbackName) {
        return this.afterReady(() -> {
            try (Connection connection = this.openConnection();
                 PreparedStatement statement = connection.prepareStatement("""
                     SELECT last_name, plays, wins, losses, best_streak, current_streak, total_response_ms, total_answers
                     FROM nowquiz_player_stats
                     WHERE player_id = ?
                     """)) {
                statement.setString(1, playerId.toString());
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        return Optional.of(readStats(playerId, resultSet));
                    }
                    return Optional.of(new PlayerStats(playerId, fallbackName == null || fallbackName.isBlank() ? "Unknown" : fallbackName));
                }
            } catch (SQLException exception) {
                throw new CompletionException(exception);
            }
        });
    }

    public CompletableFuture<Optional<PlayerStats>> loadStatsByName(String playerName) {
        return this.afterReady(() -> {
            try (Connection connection = this.openConnection();
                 PreparedStatement statement = connection.prepareStatement("""
                     SELECT player_id, last_name, plays, wins, losses, best_streak, current_streak, total_response_ms, total_answers
                     FROM nowquiz_player_stats
                     WHERE lower(last_name) = lower(?)
                     LIMIT 1
                     """)) {
                statement.setString(1, playerName);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        return Optional.empty();
                    }

                    UUID playerId = UUID.fromString(resultSet.getString("player_id"));
                    return Optional.of(readStats(playerId, resultSet));
                }
            } catch (SQLException exception) {
                throw new CompletionException(exception);
            }
        });
    }

    public CompletableFuture<Void> saveAll(Collection<PlayerStats> stats) {
        if (stats.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        return this.afterReady(() -> {
            try (Connection connection = this.openConnection();
                 PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO nowquiz_player_stats (
                         player_id, last_name, plays, wins, losses, best_streak, current_streak, total_response_ms, total_answers
                     ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                     ON CONFLICT(player_id) DO UPDATE SET
                         last_name = excluded.last_name,
                         plays = excluded.plays,
                         wins = excluded.wins,
                         losses = excluded.losses,
                         best_streak = excluded.best_streak,
                         current_streak = excluded.current_streak,
                         total_response_ms = excluded.total_response_ms,
                         total_answers = excluded.total_answers
                     """)) {
                for (PlayerStats stat : stats) {
                    statement.setString(1, stat.playerId().toString());
                    statement.setString(2, stat.lastKnownName());
                    statement.setLong(3, stat.plays());
                    statement.setLong(4, stat.wins());
                    statement.setLong(5, stat.losses());
                    statement.setLong(6, stat.bestStreak());
                    statement.setLong(7, stat.currentStreak());
                    statement.setLong(8, stat.totalResponseMs());
                    statement.setLong(9, stat.totalAnswers());
                    statement.addBatch();
                }

                statement.executeBatch();
                return null;
            } catch (SQLException exception) {
                throw new CompletionException(exception);
            }
        });
    }

    public CompletableFuture<List<LeaderboardEntry>> fetchTop(LeaderboardMetric metric, int limit) {
        return this.afterReady(() -> {
            List<LeaderboardEntry> entries = new ArrayList<>();
            String sql = """
                SELECT player_id, last_name, %s
                FROM nowquiz_player_stats
                ORDER BY %s DESC, wins DESC, plays DESC, last_name ASC
                LIMIT ?
                """.formatted(metric.column(), metric.column());

            try (Connection connection = this.openConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, Math.max(1, limit));
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        entries.add(new LeaderboardEntry(
                            UUID.fromString(resultSet.getString("player_id")),
                            resultSet.getString("last_name"),
                            resultSet.getLong(metric.column())
                        ));
                    }
                }
            } catch (SQLException exception) {
                throw new CompletionException(exception);
            }

            return entries;
        });
    }

    private <T> CompletableFuture<T> afterReady(ThrowingSupplier<T> supplier) {
        return this.readyFuture.thenCompose(unused -> this.executor.supply(() -> {
            try {
                return supplier.get();
            } catch (Exception exception) {
                throw new CompletionException(exception);
            }
        }));
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + this.databasePath.toAbsolutePath());
    }

    private static PlayerStats readStats(UUID playerId, ResultSet resultSet) throws SQLException {
        return new PlayerStats(
            playerId,
            resultSet.getString("last_name"),
            resultSet.getLong("plays"),
            resultSet.getLong("wins"),
            resultSet.getLong("losses"),
            resultSet.getLong("best_streak"),
            resultSet.getLong("current_streak"),
            resultSet.getLong("total_response_ms"),
            resultSet.getLong("total_answers")
        );
    }

    private static Throwable unwrap(Throwable throwable) {
        if (throwable instanceof CompletionException completionException && completionException.getCause() != null) {
            return completionException.getCause();
        }
        return throwable;
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
