package dev.joshlucem.nowquiz.manager;

import dev.joshlucem.nowquiz.core.MessageService;
import dev.joshlucem.nowquiz.core.NowQuizPlugin;
import dev.joshlucem.nowquiz.core.NowQuizSettings;
import dev.joshlucem.nowquiz.core.PluginLogger;
import dev.joshlucem.nowquiz.quiz.AnswerOption;
import dev.joshlucem.nowquiz.quiz.PlayerAnswer;
import dev.joshlucem.nowquiz.quiz.PlayerStats;
import dev.joshlucem.nowquiz.quiz.Question;
import dev.joshlucem.nowquiz.quiz.QuestionType;
import dev.joshlucem.nowquiz.quiz.QuizRound;
import dev.joshlucem.nowquiz.util.BroadcastScope;
import dev.joshlucem.nowquiz.util.TextUtil;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * Owns the active round lifecycle and all public round broadcasts.
 */
public final class RoundManager {

    private final NowQuizPlugin plugin;
    private final NowQuizSettings settings;
    private final MessageService messageService;
    private final RewardManager rewardManager;
    private final StatsManager statsManager;
    private final PluginLogger logger;
    private final AtomicLong roundSequence;
    private final MiniMessage miniMessage;
    private volatile QuizRound activeRound;
    private BukkitTask timeoutTask;

    public RoundManager(
        NowQuizPlugin plugin,
        NowQuizSettings settings,
        MessageService messageService,
        RewardManager rewardManager,
        StatsManager statsManager,
        PluginLogger logger
    ) {
        this.plugin = plugin;
        this.settings = settings;
        this.messageService = messageService;
        this.rewardManager = rewardManager;
        this.statsManager = statsManager;
        this.logger = logger;
        this.roundSequence = new AtomicLong(0L);
        this.miniMessage = MiniMessage.miniMessage();
    }

    public QuizRound getActiveRound() {
        return this.activeRound;
    }

    public boolean hasActiveRound() {
        return this.activeRound != null;
    }

    /**
     * Starts a new round if none is active.
     */
    public boolean startRound(Question question, Player anchorPlayer) {
        if (this.activeRound != null) {
            return false;
        }

        AudienceSelection selection = this.selectAudience(anchorPlayer);
        if (selection.recipients().isEmpty()) {
            this.logger.debug("Skipped round start because no eligible recipients were online.");
            return false;
        }

        long startedAt = System.currentTimeMillis();
        QuizRound round = new QuizRound(
            this.roundSequence.incrementAndGet(),
            question,
            startedAt,
            startedAt + (this.settings.roundTimeLimitSeconds() * 1000L),
            this.settings.allowMultipleWinners(),
            selection.playerIds(),
            selection.anchorWorldName()
        );
        this.activeRound = round;
        this.broadcastQuestion(round, selection.recipients());

        this.timeoutTask = Bukkit.getScheduler().runTaskLater(
            this.plugin,
            () -> this.finishActiveRound(false),
            this.settings.roundTimeLimitSeconds() * 20L
        );
        return true;
    }

    /**
     * Finishes the active round, optionally marking it as a manual stop.
     */
    public boolean finishActiveRound(boolean manualStop) {
        QuizRound round = this.activeRound;
        if (round == null) {
            return false;
        }

        this.activeRound = null;
        if (this.timeoutTask != null) {
            this.timeoutTask.cancel();
            this.timeoutTask = null;
        }

        round.close();
        Collection<Player> recipients = this.resolveLiveRecipients(round);

        if (manualStop) {
            this.broadcast(recipients, this.messageService.render("round.stopped"));
        }

        List<PlayerAnswer> winners = this.pickWinners(round);
        Set<UUID> winnerIds = winners.stream()
            .map(PlayerAnswer::playerId)
            .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<UUID, PlayerStats> updatedStats = this.statsManager.recordRound(round, winnerIds);
        for (PlayerAnswer winner : winners) {
            Player player = Bukkit.getPlayer(winner.playerId());
            if (player == null || !player.isOnline()) {
                this.logger.debug("Winner " + winner.playerName() + " left before rewards were applied.");
                continue;
            }

            PlayerStats playerStats = updatedStats.get(winner.playerId());
            long streak = playerStats == null ? 0L : playerStats.currentStreak();
            this.rewardManager.grant(player, round.question(), streak);
        }

        this.broadcastRoundSummary(round, winners, recipients);
        return true;
    }

    public void abort() {
        if (this.timeoutTask != null) {
            this.timeoutTask.cancel();
            this.timeoutTask = null;
        }

        if (this.activeRound != null) {
            this.activeRound.close();
            this.activeRound = null;
        }
    }

    private void broadcastQuestion(QuizRound round, Collection<Player> recipients) {
        this.broadcast(recipients, this.messageService.render("question.header", Map.of("round_id", Long.toString(round.roundId()))));
        this.broadcast(recipients, this.messageService.render("question.prompt", Map.of("question", round.question().prompt())));
        this.broadcast(recipients, this.messageService.render("question.footer", Map.of("seconds", Integer.toString(this.settings.roundTimeLimitSeconds()))));

        if (round.question().type() == QuestionType.OPEN) {
            this.broadcast(recipients, this.messageService.render(
                "question.no-click-open",
                Map.of(
                    "round_id", Long.toString(round.roundId()),
                    "chat_prefix", this.settings.chatPrefix()
                )
            ));
            return;
        }

        String layout = this.messageService.string("options.layout", "LIST").trim().toUpperCase();
        if ("INLINE".equals(layout)) {
            Component inline = Component.empty();
            boolean first = true;
            Component separator = this.miniMessage.deserialize(this.messageService.string("options.inline-separator", " | "));
            for (AnswerOption option : round.question().options().values()) {
                if (!first) {
                    inline = inline.append(separator);
                }
                inline = inline.append(this.buildOptionComponent(round, option, false));
                first = false;
            }
            this.broadcast(recipients, inline);
            return;
        }

        for (AnswerOption option : round.question().options().values()) {
            this.broadcast(recipients, this.buildOptionComponent(round, option, true));
        }
    }

    private Component buildOptionComponent(QuizRound round, AnswerOption option, boolean listLayout) {
        String messagePath = listLayout ? "options.list-format" : "options.inline-format";
        Component base = this.messageService.render(messagePath, Map.of("option", option.key(), "text", option.text()));
        if (!this.settings.allowClickAnswers()) {
            return base;
        }

        return base.hoverEvent(HoverEvent.showText(this.messageService.render("options.hover")))
            .clickEvent(ClickEvent.runCommand("/nowquiz answer " + round.roundId() + " " + option.key()));
    }

    private void broadcastRoundSummary(QuizRound round, List<PlayerAnswer> winners, Collection<Player> recipients) {
        if (winners.isEmpty()) {
            this.broadcast(recipients, this.messageService.render("round.no-winner"));
        } else if (winners.size() == 1) {
            PlayerAnswer winner = winners.get(0);
            this.broadcast(recipients, this.messageService.render(
                "round.winner-single",
                Map.of(
                    "player", winner.playerName(),
                    "time", TextUtil.formatMillis(winner.responseTimeMillis())
                )
            ));
        } else {
            String names = TextUtil.joinNames(winners.stream().map(PlayerAnswer::playerName).toList());
            this.broadcast(recipients, this.messageService.render("round.winner-multi", Map.of("players", names)));
        }

        this.broadcast(recipients, this.messageService.render("round.correct-answer", Map.of("answer", round.question().correctAnswerDisplay())));
    }

    private List<PlayerAnswer> pickWinners(QuizRound round) {
        List<PlayerAnswer> correctAnswers = new ArrayList<>();
        for (PlayerAnswer answer : round.answers().values()) {
            if (answer.correct() && answer.rewardEligible()) {
                correctAnswers.add(answer);
            }
        }

        if (correctAnswers.isEmpty()) {
            return List.of();
        }

        if (round.allowMultipleWinners()) {
            return List.copyOf(correctAnswers);
        }

        return List.of(correctAnswers.getFirst());
    }

    private AudienceSelection selectAudience(Player anchorPlayer) {
        String anchorWorldName = this.resolveAnchorWorld(anchorPlayer);
        Set<UUID> playerIds = new LinkedHashSet<>();
        List<Player> recipients = new ArrayList<>();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.hasPermission("nowquiz.use")) {
                continue;
            }

            if (this.settings.broadcastScope() == BroadcastScope.PERMISSION
                && !this.settings.broadcastPermission().isBlank()
                && !player.hasPermission(this.settings.broadcastPermission())) {
                continue;
            }

            if (this.settings.broadcastScope() == BroadcastScope.WORLD
                && !anchorWorldName.isBlank()
                && !player.getWorld().getName().equalsIgnoreCase(anchorWorldName)) {
                continue;
            }

            playerIds.add(player.getUniqueId());
            recipients.add(player);
        }

        return new AudienceSelection(playerIds, recipients, anchorWorldName);
    }

    private Collection<Player> resolveLiveRecipients(QuizRound round) {
        List<Player> recipients = new ArrayList<>();
        for (UUID playerId : round.eligiblePlayers()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                recipients.add(player);
            }
        }
        return recipients;
    }

    private String resolveAnchorWorld(Player anchorPlayer) {
        if (this.settings.broadcastScope() != BroadcastScope.WORLD) {
            return "";
        }

        if (anchorPlayer != null) {
            return anchorPlayer.getWorld().getName();
        }

        if (!this.settings.defaultWorldName().isBlank()) {
            World configured = Bukkit.getWorld(this.settings.defaultWorldName());
            if (configured != null) {
                return configured.getName();
            }
        }

        if (!Bukkit.getWorlds().isEmpty()) {
            return Bukkit.getWorlds().getFirst().getName();
        }

        return "";
    }

    private void broadcast(Collection<Player> recipients, Component component) {
        for (Player recipient : recipients) {
            recipient.sendMessage(component);
        }
    }

    private record AudienceSelection(Set<UUID> playerIds, List<Player> recipients, String anchorWorldName) {
    }
}
