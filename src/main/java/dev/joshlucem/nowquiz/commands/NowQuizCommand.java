package dev.joshlucem.nowquiz.commands;

import dev.joshlucem.nowquiz.core.MessageService;
import dev.joshlucem.nowquiz.core.NowQuizPlugin;
import dev.joshlucem.nowquiz.manager.QuestionPool;
import dev.joshlucem.nowquiz.quiz.LeaderboardEntry;
import dev.joshlucem.nowquiz.quiz.PlayerStats;
import dev.joshlucem.nowquiz.util.LeaderboardMetric;
import dev.joshlucem.nowquiz.util.TextUtil;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletionException;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * Handles the single root command and its subcommands.
 */
public final class NowQuizCommand implements CommandExecutor, TabCompleter {

    private final NowQuizPlugin plugin;

    public NowQuizCommand(NowQuizPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        MessageService messages = this.plugin.getMessageService();
        if (args.length == 0) {
            this.sendUsage(sender, "/nowquiz <start|stop|ask|reload|answer|stats|top>");
            return true;
        }

        String subcommand = args[0].toLowerCase(Locale.ROOT);
        return switch (subcommand) {
            case "start" -> this.handleStart(sender, args);
            case "stop" -> this.handleStop(sender);
            case "ask" -> this.handleAsk(sender, args);
            case "reload" -> this.handleReload(sender);
            case "answer" -> this.handleAnswer(sender, args);
            case "stats" -> this.handleStats(sender, args);
            case "top" -> this.handleTop(sender, args);
            default -> {
                messages.send(sender, "errors.usage", Map.of("usage", "/nowquiz <start|stop|ask|reload|answer|stats|top>"));
                yield true;
            }
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return this.filterSuggestions(
                List.of("start", "stop", "ask", "reload", "answer", "stats", "top"),
                args[0]
            );
        }

        if (args.length == 2) {
            String subcommand = args[0].toLowerCase(Locale.ROOT);
            return switch (subcommand) {
                case "start" -> this.filterSuggestions(this.plugin.getQuestionPool().categories(), args[1]);
                case "ask" -> this.filterSuggestions(this.plugin.getQuestionPool().questionIds(), args[1]);
                case "stats" -> this.filterSuggestions(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
                case "top" -> this.filterSuggestions(List.of("wins", "streak"), args[1]);
                default -> List.of();
            };
        }

        return List.of();
    }

    private boolean handleStart(CommandSender sender, String[] args) {
        if (!sender.hasPermission("nowquiz.start")) {
            this.plugin.getMessageService().send(sender, "errors.no-permission");
            return true;
        }

        String category = args.length >= 2 ? args[1] : null;
        if (!this.plugin.getQuizManager().startRandomRound(category, sender) && category != null) {
            QuestionPool pool = this.plugin.getQuestionPool();
            if (!pool.categories().contains(category.toLowerCase(Locale.ROOT))) {
                this.plugin.getMessageService().send(sender, "system.no-questions");
            }
        }
        return true;
    }

    private boolean handleStop(CommandSender sender) {
        if (!sender.hasPermission("nowquiz.stop")) {
            this.plugin.getMessageService().send(sender, "errors.no-permission");
            return true;
        }

        this.plugin.getQuizManager().stopRound(sender);
        return true;
    }

    private boolean handleAsk(CommandSender sender, String[] args) {
        if (!sender.hasPermission("nowquiz.start")) {
            this.plugin.getMessageService().send(sender, "errors.no-permission");
            return true;
        }

        if (args.length < 2) {
            this.sendUsage(sender, "/nowquiz ask <questionId>");
            return true;
        }

        this.plugin.getQuizManager().startSpecificQuestion(args[1], sender);
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("nowquiz.reload")) {
            this.plugin.getMessageService().send(sender, "errors.no-permission");
            return true;
        }

        this.plugin.reloadPluginState();
        this.plugin.getMessageService().send(sender, "system.reloaded");
        return true;
    }

    private boolean handleAnswer(CommandSender sender, String[] args) {
        if (!sender.hasPermission("nowquiz.use")) {
            this.plugin.getMessageService().send(sender, "errors.no-permission");
            return true;
        }

        if (args.length < 3) {
            this.sendUsage(sender, "/nowquiz answer <roundId> <option|text>");
            return true;
        }

        long roundId;
        try {
            roundId = Long.parseLong(args[1]);
        } catch (NumberFormatException exception) {
            this.plugin.getMessageService().send(sender, "errors.invalid-round");
            return true;
        }

        String answer = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        this.plugin.getAnswerService().submitCommandAnswer(sender, roundId, answer);
        return true;
    }

    private boolean handleStats(CommandSender sender, String[] args) {
        if (!sender.hasPermission("nowquiz.stats")) {
            this.plugin.getMessageService().send(sender, "errors.no-permission");
            return true;
        }

        if (args.length < 2) {
            if (!(sender instanceof Player player)) {
                this.sendUsage(sender, "/nowquiz stats <player>");
                return true;
            }

            this.loadStatsForUuid(sender, player.getUniqueId(), player.getName());
            return true;
        }

        Player onlinePlayer = Bukkit.getPlayerExact(args[1]);
        if (onlinePlayer != null) {
            this.loadStatsForUuid(sender, onlinePlayer.getUniqueId(), onlinePlayer.getName());
            return true;
        }

        this.plugin.getStatsManager().loadByName(args[1]).whenComplete((optionalStats, throwable) ->
            Bukkit.getScheduler().runTask(this.plugin, () -> {
                if (throwable != null) {
                    this.handleAsyncFailure(sender, throwable);
                    return;
                }

                if (optionalStats.isEmpty()) {
                    this.plugin.getMessageService().send(sender, "errors.player-not-found");
                    return;
                }

                this.sendStats(sender, optionalStats.get());
            })
        );
        return true;
    }

    private boolean handleTop(CommandSender sender, String[] args) {
        if (!sender.hasPermission("nowquiz.top")) {
            this.plugin.getMessageService().send(sender, "errors.no-permission");
            return true;
        }

        LeaderboardMetric metric = args.length >= 2 ? LeaderboardMetric.fromInput(args[1]) : LeaderboardMetric.WINS;
        this.plugin.getStatsManager().fetchTop(metric, 10).whenComplete((entries, throwable) ->
            Bukkit.getScheduler().runTask(this.plugin, () -> {
                if (throwable != null) {
                    this.handleAsyncFailure(sender, throwable);
                    return;
                }

                MessageService messages = this.plugin.getMessageService();
                messages.send(sender, "top.header", Map.of("metric", metric.displayName()));
                int position = 1;
                for (LeaderboardEntry entry : entries) {
                    messages.send(sender, "top.entry", Map.of(
                        "position", Integer.toString(position),
                        "player", entry.playerName(),
                        "value", Long.toString(entry.value())
                    ));
                    position++;
                }
            })
        );
        return true;
    }

    private void loadStatsForUuid(CommandSender sender, java.util.UUID playerId, String playerName) {
        this.plugin.getStatsManager().getOrLoad(playerId, playerName).whenComplete((stats, throwable) ->
            Bukkit.getScheduler().runTask(this.plugin, () -> {
                if (throwable != null) {
                    this.handleAsyncFailure(sender, throwable);
                    return;
                }

                this.sendStats(sender, stats);
            })
        );
    }

    private void sendStats(CommandSender sender, PlayerStats stats) {
        MessageService messages = this.plugin.getMessageService();
        messages.send(sender, "stats.header", Map.of("player", stats.lastKnownName()));
        messages.send(sender, "stats.line", Map.of(
            "plays", Long.toString(stats.plays()),
            "wins", Long.toString(stats.wins()),
            "losses", Long.toString(stats.losses())
        ));
        messages.send(sender, "stats.streak", Map.of(
            "best_streak", Long.toString(stats.bestStreak()),
            "current_streak", Long.toString(stats.currentStreak())
        ));
        messages.send(sender, "stats.average", Map.of("avg_ms", TextUtil.formatDouble(stats.averageResponseMs())));
    }

    private void sendUsage(CommandSender sender, String usage) {
        this.plugin.getMessageService().send(sender, "errors.usage", Map.of("usage", usage));
    }

    private void handleAsyncFailure(CommandSender sender, Throwable throwable) {
        this.plugin.getLoggerBridge().warn("An async command task failed.", unwrap(throwable));
        this.plugin.getMessageService().send(sender, "system.database-error");
    }

    private List<String> filterSuggestions(Iterable<String> options, String input) {
        String filter = input == null ? "" : input.toLowerCase(Locale.ROOT);
        List<String> results = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(filter)) {
                results.add(option);
            }
        }
        return results;
    }

    private static Throwable unwrap(Throwable throwable) {
        if (throwable instanceof CompletionException completionException && completionException.getCause() != null) {
            return completionException.getCause();
        }
        return throwable;
    }
}
