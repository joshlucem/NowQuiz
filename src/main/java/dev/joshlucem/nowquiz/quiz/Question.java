package dev.joshlucem.nowquiz.quiz;

import dev.joshlucem.nowquiz.util.TextUtil;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable representation of a quiz question loaded from configuration.
 */
public final class Question {

    private final String category;
    private final String id;
    private final QuestionType type;
    private final String prompt;
    private final LinkedHashMap<String, AnswerOption> options;
    private final String correctKeyOrAnswer;
    private final Set<String> acceptedAnswers;
    private final String rewardProfile;
    private final RewardDefinition rewardOverrides;

    public Question(
        String category,
        String id,
        QuestionType type,
        String prompt,
        LinkedHashMap<String, AnswerOption> options,
        String correctKeyOrAnswer,
        Collection<String> acceptedAnswers,
        String rewardProfile,
        RewardDefinition rewardOverrides
    ) {
        this.category = category;
        this.id = id;
        this.type = type;
        this.prompt = prompt;
        this.options = new LinkedHashMap<>(options);
        this.correctKeyOrAnswer = correctKeyOrAnswer;
        this.acceptedAnswers = acceptedAnswers.stream()
            .map(TextUtil::normalizeAnswer)
            .collect(LinkedHashSet::new, Set::add, Set::addAll);
        this.rewardProfile = rewardProfile;
        this.rewardOverrides = rewardOverrides == null ? RewardDefinition.empty() : rewardOverrides;
    }

    public String category() {
        return this.category;
    }

    public String id() {
        return this.id;
    }

    public QuestionType type() {
        return this.type;
    }

    public String prompt() {
        return this.prompt;
    }

    public Map<String, AnswerOption> options() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(this.options));
    }

    public String rewardProfile() {
        return this.rewardProfile;
    }

    public RewardDefinition rewardOverrides() {
        return this.rewardOverrides;
    }

    public Optional<String> resolveOptionKey(String rawInput) {
        if (this.type == QuestionType.OPEN) {
            return Optional.empty();
        }

        String normalized = TextUtil.normalizeAnswer(rawInput);
        for (AnswerOption option : this.options.values()) {
            if (option.key().equalsIgnoreCase(normalized)
                || TextUtil.normalizeAnswer(option.text()).equals(normalized)) {
                return Optional.of(option.key());
            }
        }

        return Optional.empty();
    }

    public boolean isCorrect(String rawInput) {
        return switch (this.type) {
            case MULTIPLE, TRUE_FALSE -> this.resolveOptionKey(rawInput)
                .map(optionKey -> optionKey.equalsIgnoreCase(this.correctKeyOrAnswer))
                .orElse(false);
            case OPEN -> this.acceptedAnswers.contains(TextUtil.normalizeAnswer(rawInput));
        };
    }

    public String correctAnswerDisplay() {
        if (this.type == QuestionType.OPEN) {
            return this.correctKeyOrAnswer;
        }

        AnswerOption option = this.options.get(this.correctKeyOrAnswer.toUpperCase(Locale.ROOT));
        if (option == null) {
            return this.correctKeyOrAnswer;
        }

        return option.key() + " - " + option.text();
    }

    public String displayFor(String rawInput) {
        if (this.type == QuestionType.OPEN) {
            return rawInput;
        }

        return this.resolveOptionKey(rawInput)
            .map(optionKey -> {
                AnswerOption option = this.options.get(optionKey.toUpperCase(Locale.ROOT));
                return option == null ? optionKey : option.key() + " - " + option.text();
            })
            .orElse(rawInput);
    }
}
