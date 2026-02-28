package dev.joshlucem.nowquiz.manager;

import dev.joshlucem.nowquiz.core.MessageService;
import dev.joshlucem.nowquiz.core.NowQuizPlugin;
import dev.joshlucem.nowquiz.core.NowQuizSettings;
import dev.joshlucem.nowquiz.quiz.Question;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * Orchestrates global enable state, scheduling and admin-driven round control.
 */
public final class QuizManager {

    private final NowQuizPlugin plugin;
    private final NowQuizSettings settings;
    private final MessageService messageService;
    private final QuestionPool questionPool;
    private final RoundManager roundManager;
    private BukkitTask autoTask;

    public QuizManager(
        NowQuizPlugin plugin,
        NowQuizSettings settings,
        MessageService messageService,
        QuestionPool questionPool,
        RoundManager roundManager
    ) {
        this.plugin = plugin;
        this.settings = settings;
        this.messageService = messageService;
        this.questionPool = questionPool;
        this.roundManager = roundManager;
    }

    public boolean startRandomRound(String category, CommandSender initiator) {
        if (!this.settings.enabled()) {
            if (initiator != null) {
                this.messageService.send(initiator, "system.disabled");
            }
            return false;
        }

        if (this.roundManager.hasActiveRound()) {
            if (initiator != null) {
                this.messageService.send(initiator, "errors.round-running");
            }
            return false;
        }

        Optional<Question> question = this.questionPool.pickRandom(category);
        if (question.isEmpty()) {
            if (initiator != null) {
                this.messageService.send(initiator, "system.no-questions");
            }
            return false;
        }

        Player anchor = initiator instanceof Player player ? player : null;
        boolean started = this.roundManager.startRound(question.get(), anchor);
        if (started && initiator != null) {
            this.messageService.send(initiator, "round.manual-start");
        }
        return started;
    }

    public boolean startSpecificQuestion(String questionId, CommandSender initiator) {
        if (!this.settings.enabled()) {
            this.messageService.send(initiator, "system.disabled");
            return false;
        }

        if (this.roundManager.hasActiveRound()) {
            this.messageService.send(initiator, "errors.round-running");
            return false;
        }

        Optional<Question> question = this.questionPool.findById(questionId);
        if (question.isEmpty()) {
            this.messageService.send(initiator, "system.no-questions");
            return false;
        }

        Player anchor = initiator instanceof Player player ? player : null;
        boolean started = this.roundManager.startRound(question.get(), anchor);
        if (started) {
            this.messageService.send(initiator, "round.manual-start");
        }
        return started;
    }

    public boolean stopRound(CommandSender initiator) {
        if (!this.roundManager.hasActiveRound()) {
            if (initiator != null) {
                this.messageService.send(initiator, "errors.no-active-round");
            }
            return false;
        }

        return this.roundManager.finishActiveRound(true);
    }

    public void refreshScheduler() {
        if (this.autoTask != null) {
            this.autoTask.cancel();
            this.autoTask = null;
        }

        if (!this.settings.enabled() || !this.settings.autoEnabled() || this.questionPool.size() <= 0) {
            return;
        }

        long intervalTicks = this.settings.autoIntervalSeconds() * 20L;
        this.autoTask = Bukkit.getScheduler().runTaskTimer(
            this.plugin,
            () -> {
                if (this.roundManager.hasActiveRound()) {
                    return;
                }
                this.startRandomRound(null, null);
            },
            intervalTicks,
            intervalTicks
        );
    }

    public void shutdown() {
        if (this.autoTask != null) {
            this.autoTask.cancel();
            this.autoTask = null;
        }
        this.roundManager.abort();
    }
}
