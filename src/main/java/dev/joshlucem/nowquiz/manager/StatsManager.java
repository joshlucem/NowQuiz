package dev.joshlucem.nowquiz.manager;

import dev.joshlucem.nowquiz.core.PluginLogger;
import dev.joshlucem.nowquiz.quiz.LeaderboardEntry;
import dev.joshlucem.nowquiz.quiz.PlayerAnswer;
import dev.joshlucem.nowquiz.quiz.PlayerStats;
import dev.joshlucem.nowquiz.quiz.QuizRound;
import dev.joshlucem.nowquiz.storage.SQLiteStorage;
import dev.joshlucem.nowquiz.util.LeaderboardMetric;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletionException;

/**
 * Maintains a hot in-memory stats cache and persists dirty entries asynchronously.
 */
public final class StatsManager {

    private final SQLiteStorage storage;
    private final PluginLogger logger;
    private final Map<UUID, PlayerStats> cache;
    private final Set<UUID> dirty;

    public StatsManager(SQLiteStorage storage, PluginLogger logger) {
        this.storage = storage;
        this.logger = logger;
        this.cache = new ConcurrentHashMap<>();
        this.dirty = ConcurrentHashMap.newKeySet();
    }

    public Map<UUID, PlayerStats> recordRound(QuizRound round, Collection<UUID> winnerIds) {
        Map<UUID, PlayerAnswer> answers = round.answers();
        if (answers.isEmpty()) {
            return Map.of();
        }

        Map<UUID, PlayerStats> updated = new ConcurrentHashMap<>();
        for (PlayerAnswer answer : answers.values()) {
            PlayerStats stats = this.cache.computeIfAbsent(answer.playerId(), ignored -> new PlayerStats(answer.playerId(), answer.playerName()));
            boolean won = winnerIds.contains(answer.playerId());
            stats.recordResult(answer.playerName(), won, answer.responseTimeMillis());
            this.dirty.add(answer.playerId());
            updated.put(answer.playerId(), stats.copy());
        }

        this.flushDirty();
        return Map.copyOf(updated);
    }

    public CompletableFuture<PlayerStats> getOrLoad(UUID playerId, String fallbackName) {
        PlayerStats cached = this.cache.get(playerId);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached.copy());
        }

        return this.storage.loadStats(playerId, fallbackName)
            .thenApply(optional -> {
                PlayerStats stats = optional.orElseGet(() -> new PlayerStats(playerId, fallbackName));
                this.cache.putIfAbsent(playerId, stats);
                return this.cache.get(playerId).copy();
            });
    }

    public CompletableFuture<Optional<PlayerStats>> loadByName(String playerName) {
        for (PlayerStats stats : this.cache.values()) {
            if (stats.lastKnownName().equalsIgnoreCase(playerName)) {
                return CompletableFuture.completedFuture(Optional.of(stats.copy()));
            }
        }

        return this.storage.loadStatsByName(playerName)
            .thenApply(optional -> {
                optional.ifPresent(stats -> this.cache.putIfAbsent(stats.playerId(), stats));
                return optional.map(PlayerStats::copy);
            });
    }

    public CompletableFuture<List<LeaderboardEntry>> fetchTop(LeaderboardMetric metric, int limit) {
        return this.storage.fetchTop(metric, limit);
    }

    public CompletableFuture<Void> flushDirty() {
        List<PlayerStats> snapshot = this.drainDirtySnapshot();
        if (snapshot.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        return this.storage.saveAll(snapshot).handle((unused, throwable) -> {
            if (throwable != null) {
                Throwable cause = unwrap(throwable);
                this.logger.warn("Failed to persist NowQuiz stats asynchronously.", cause);
                for (PlayerStats stat : snapshot) {
                    this.dirty.add(stat.playerId());
                }
            }
            return null;
        });
    }

    public void flushDirtyBlocking() {
        try {
            this.flushDirty().join();
        } catch (CompletionException exception) {
            this.logger.warn("Failed to flush stats during shutdown.", unwrap(exception));
        }
    }

    private List<PlayerStats> drainDirtySnapshot() {
        List<PlayerStats> snapshot = new ArrayList<>();
        for (UUID playerId : new ArrayList<>(this.dirty)) {
            PlayerStats stats = this.cache.get(playerId);
            if (stats == null) {
                this.dirty.remove(playerId);
                continue;
            }

            if (this.dirty.remove(playerId)) {
                snapshot.add(stats.copy());
            }
        }
        return snapshot;
    }

    private static Throwable unwrap(Throwable throwable) {
        if (throwable instanceof CompletionException completionException && completionException.getCause() != null) {
            return completionException.getCause();
        }
        return throwable;
    }
}
