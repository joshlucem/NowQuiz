package dev.joshlucem.nowquiz.manager;

import dev.joshlucem.nowquiz.core.NowQuizSettings;
import dev.joshlucem.nowquiz.core.PluginLogger;
import dev.joshlucem.nowquiz.quiz.Question;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * In-memory question cache with simple repeat avoidance.
 */
public final class QuestionPool {

    private final NowQuizSettings settings;
    private final PluginLogger logger;
    private final List<Question> questions;
    private final Map<String, Question> byId;
    private final Map<String, List<Question>> byCategory;
    private final Deque<String> recentIds;

    public QuestionPool(Collection<Question> questions, NowQuizSettings settings, PluginLogger logger) {
        this.settings = settings;
        this.logger = logger;
        this.questions = List.copyOf(questions);
        this.byId = new LinkedHashMap<>();
        this.byCategory = new LinkedHashMap<>();
        this.recentIds = new ArrayDeque<>();

        for (Question question : this.questions) {
            this.byId.put(question.id().toLowerCase(Locale.ROOT), question);
            this.byCategory.computeIfAbsent(question.category().toLowerCase(Locale.ROOT), ignored -> new ArrayList<>())
                .add(question);
        }
    }

    public int size() {
        return this.questions.size();
    }

    public Optional<Question> findById(String questionId) {
        if (questionId == null || questionId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(this.byId.get(questionId.toLowerCase(Locale.ROOT)));
    }

    public Optional<Question> pickRandom(String category) {
        List<Question> candidates = category == null || category.isBlank()
            ? this.questions
            : this.byCategory.getOrDefault(category.toLowerCase(Locale.ROOT), List.of());

        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        List<Question> selectionPool = candidates;
        if (this.settings.avoidRepeats() && this.settings.repeatCooldown() > 0 && candidates.size() > 1) {
            selectionPool = candidates.stream()
                .filter(question -> !this.recentIds.contains(question.id()))
                .collect(Collectors.toCollection(ArrayList::new));

            if (selectionPool.isEmpty()) {
                selectionPool = candidates;
            }
        }

        Question selected = selectionPool.get(ThreadLocalRandom.current().nextInt(selectionPool.size()));
        this.remember(selected.id());
        return Optional.of(selected);
    }

    public Collection<String> categories() {
        return List.copyOf(this.byCategory.keySet());
    }

    public Collection<String> questionIds() {
        return List.copyOf(this.byId.keySet());
    }

    private void remember(String questionId) {
        if (!this.settings.avoidRepeats() || this.settings.repeatCooldown() <= 0) {
            return;
        }

        this.recentIds.remove(questionId);
        this.recentIds.addLast(questionId);

        while (this.recentIds.size() > this.settings.repeatCooldown()) {
            this.recentIds.removeFirst();
        }

        this.logger.debug("Question " + questionId + " selected. Recent cache size: " + this.recentIds.size() + ".");
    }
}
