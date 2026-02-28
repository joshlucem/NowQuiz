package dev.joshlucem.nowquiz.quiz;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Mutable runtime state for the active quiz round.
 *
 * <p>Only the active round mutates, while the immutable question data remains shared and cached.</p>
 */
public final class QuizRound {

    private final long roundId;
    private final Question question;
    private final long startedAtMillis;
    private final long closesAtMillis;
    private final boolean allowMultipleWinners;
    private final Set<UUID> eligiblePlayers;
    private final String anchorWorldName;
    private final LinkedHashMap<UUID, PlayerAnswer> answers;
    private boolean open;

    public QuizRound(
        long roundId,
        Question question,
        long startedAtMillis,
        long closesAtMillis,
        boolean allowMultipleWinners,
        Collection<UUID> eligiblePlayers,
        String anchorWorldName
    ) {
        this.roundId = roundId;
        this.question = question;
        this.startedAtMillis = startedAtMillis;
        this.closesAtMillis = closesAtMillis;
        this.allowMultipleWinners = allowMultipleWinners;
        this.eligiblePlayers = Collections.unmodifiableSet(new LinkedHashSet<>(eligiblePlayers));
        this.anchorWorldName = anchorWorldName;
        this.answers = new LinkedHashMap<>();
        this.open = true;
    }

    public long roundId() {
        return this.roundId;
    }

    public Question question() {
        return this.question;
    }

    public long startedAtMillis() {
        return this.startedAtMillis;
    }

    public long closesAtMillis() {
        return this.closesAtMillis;
    }

    public boolean allowMultipleWinners() {
        return this.allowMultipleWinners;
    }

    public Set<UUID> eligiblePlayers() {
        return this.eligiblePlayers;
    }

    public String anchorWorldName() {
        return this.anchorWorldName;
    }

    public boolean isOpen() {
        return this.open;
    }

    public boolean isEligible(UUID playerId) {
        return this.eligiblePlayers.contains(playerId);
    }

    public boolean hasAnswered(UUID playerId) {
        return this.answers.containsKey(playerId);
    }

    public boolean recordAnswer(PlayerAnswer answer) {
        if (!this.open || this.answers.containsKey(answer.playerId())) {
            return false;
        }

        this.answers.put(answer.playerId(), answer);
        return true;
    }

    public Map<UUID, PlayerAnswer> answers() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(this.answers));
    }

    public void close() {
        this.open = false;
    }
}
