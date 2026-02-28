package dev.joshlucem.nowquiz.manager;

import dev.joshlucem.nowquiz.core.MessageService;
import dev.joshlucem.nowquiz.core.NowQuizPlugin;
import dev.joshlucem.nowquiz.core.NowQuizSettings;
import dev.joshlucem.nowquiz.quiz.PlayerAnswer;
import dev.joshlucem.nowquiz.quiz.Question;
import dev.joshlucem.nowquiz.quiz.QuestionType;
import dev.joshlucem.nowquiz.quiz.QuizRound;
import dev.joshlucem.nowquiz.util.TextUtil;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Validates answers from both clickable commands and optional chat submissions.
 */
public final class AnswerService {

    private final NowQuizPlugin plugin;
    private final NowQuizSettings settings;
    private final MessageService messageService;
    private final RoundManager roundManager;
    private final Map<UUID, Long> cooldowns;

    public AnswerService(NowQuizPlugin plugin, NowQuizSettings settings, MessageService messageService, RoundManager roundManager) {
        this.plugin = plugin;
        this.settings = settings;
        this.messageService = messageService;
        this.roundManager = roundManager;
        this.cooldowns = new ConcurrentHashMap<>();
    }

    public void submitCommandAnswer(CommandSender sender, long roundId, String rawAnswer) {
        if (!(sender instanceof Player player)) {
            this.messageService.send(sender, "errors.player-only");
            return;
        }

        this.submit(player, roundId, rawAnswer);
    }

    public void submitChatAnswer(Player player, String rawAnswer) {
        QuizRound round = this.roundManager.getActiveRound();
        if (round == null) {
            return;
        }

        this.submit(player, round.roundId(), rawAnswer);
    }

    /**
     * Returns whether a chat message should be intercepted as a quiz answer.
     */
    public boolean shouldCaptureChat(Player player, String rawMessage) {
        if (!this.settings.allowChatAnswers()) {
            return false;
        }

        QuizRound round = this.roundManager.getActiveRound();
        if (round == null || !round.isOpen() || !round.isEligible(player.getUniqueId())) {
            return false;
        }

        String trimmed = rawMessage == null ? "" : rawMessage.trim();
        if (trimmed.isBlank()) {
            return false;
        }

        String prefix = this.settings.chatPrefix();
        if (prefix != null && !prefix.isBlank()) {
            return trimmed.length() > prefix.length() && trimmed.startsWith(prefix);
        }

        if (round.question().type() == QuestionType.OPEN) {
            return false;
        }

        return round.question().resolveOptionKey(trimmed).isPresent();
    }

    public String extractChatAnswer(String rawMessage) {
        String trimmed = rawMessage == null ? "" : rawMessage.trim();
        String prefix = this.settings.chatPrefix();
        if (prefix != null && !prefix.isBlank() && trimmed.startsWith(prefix)) {
            return trimmed.substring(prefix.length()).trim();
        }
        return trimmed;
    }

    private void submit(Player player, long roundId, String rawAnswer) {
        QuizRound round = this.roundManager.getActiveRound();
        if (round == null || !round.isOpen() || round.roundId() != roundId) {
            this.messageService.send(player, "errors.invalid-round");
            return;
        }

        if (!round.isEligible(player.getUniqueId())) {
            return;
        }

        long now = System.currentTimeMillis();
        long lastAttempt = this.cooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (this.settings.answerCooldownMs() > 0L && now - lastAttempt < this.settings.answerCooldownMs()) {
            long remaining = this.settings.answerCooldownMs() - (now - lastAttempt);
            this.messageService.send(player, "errors.cooldown", Map.of("time", TextUtil.formatMillis(remaining)));
            return;
        }

        if (round.hasAnswered(player.getUniqueId())) {
            this.messageService.send(player, "errors.already-answered");
            return;
        }

        if (this.settings.minHumanMs() > 0L && now - round.startedAtMillis() < this.settings.minHumanMs()) {
            this.cooldowns.put(player.getUniqueId(), now);
            this.messageService.send(player, "errors.too-fast");
            return;
        }

        Question question = round.question();
        String answerText = rawAnswer == null ? "" : rawAnswer.trim();
        if (answerText.isBlank()) {
            this.messageService.send(player, "errors.invalid-option");
            return;
        }

        if (question.type() != QuestionType.OPEN && question.resolveOptionKey(answerText).isEmpty()) {
            this.cooldowns.put(player.getUniqueId(), now);
            this.messageService.send(player, "errors.invalid-option");
            return;
        }

        boolean correct = question.isCorrect(answerText);
        boolean rewardEligible = !correct || this.plugin.isRewardEligible(player);
        if (correct && !rewardEligible) {
            this.messageService.send(player, "errors.ineligible");
        }

        PlayerAnswer answer = new PlayerAnswer(
            player.getUniqueId(),
            player.getName(),
            answerText,
            now,
            Math.max(0L, now - round.startedAtMillis()),
            correct,
            rewardEligible
        );

        if (!round.recordAnswer(answer)) {
            this.messageService.send(player, "errors.already-answered");
            return;
        }

        this.cooldowns.put(player.getUniqueId(), now);
        if (question.type() == QuestionType.OPEN) {
            this.messageService.send(player, "feedback.accepted-open");
            return;
        }

        this.messageService.send(
            player,
            correct ? "feedback.correct" : "feedback.incorrect",
            Map.of(
                "correct", question.correctAnswerDisplay(),
                "answer", question.displayFor(answerText)
            )
        );
    }
}
