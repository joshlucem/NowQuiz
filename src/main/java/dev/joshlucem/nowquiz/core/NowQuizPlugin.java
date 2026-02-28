package dev.joshlucem.nowquiz.core;

import dev.joshlucem.nowquiz.commands.NowQuizCommand;
import dev.joshlucem.nowquiz.listeners.PlayerSessionListener;
import dev.joshlucem.nowquiz.listeners.QuizChatListener;
import dev.joshlucem.nowquiz.manager.AnswerService;
import dev.joshlucem.nowquiz.manager.QuestionPool;
import dev.joshlucem.nowquiz.manager.QuizManager;
import dev.joshlucem.nowquiz.manager.RewardManager;
import dev.joshlucem.nowquiz.manager.RoundManager;
import dev.joshlucem.nowquiz.manager.StatsManager;
import dev.joshlucem.nowquiz.quiz.Question;
import dev.joshlucem.nowquiz.quiz.RewardDefinition;
import dev.joshlucem.nowquiz.storage.SQLiteStorage;
import dev.joshlucem.nowquiz.util.AsyncExecutor;
import java.io.File;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main plugin entrypoint.
 */
public final class NowQuizPlugin extends JavaPlugin {

    private final Map<UUID, Long> playerSessions = new ConcurrentHashMap<>();

    private AsyncExecutor asyncExecutor;
    private PluginLogger loggerBridge;
    private NowQuizSettings settings;
    private MessageService messageService;
    private SQLiteStorage storage;
    private StatsManager statsManager;
    private QuestionPool questionPool;
    private RewardManager rewardManager;
    private RoundManager roundManager;
    private AnswerService answerService;
    private QuizManager quizManager;

    @Override
    public void onEnable() {
        this.saveBundledResources();
        this.reloadConfig();
        this.settings = ConfigurationLoader.loadSettings(this.getConfig());
        this.loggerBridge = new PluginLogger(this.getLogger(), this.settings.debug());
        this.asyncExecutor = new AsyncExecutor("NowQuiz-Storage");
        this.storage = new SQLiteStorage(this.getDataFolder().toPath().resolve("nowquiz.db"), this.asyncExecutor, this.loggerBridge);
        this.storage.initialize();
        this.statsManager = new StatsManager(this.storage, this.loggerBridge);

        this.reloadPluginState();
        this.registerCommand();
        this.registerListeners();

        for (Player player : Bukkit.getOnlinePlayers()) {
            this.markPlayerJoin(player.getUniqueId());
        }

        this.loggerBridge.info("NowQuiz enabled with " + this.questionPool.size() + " loaded questions.");
    }

    @Override
    public void onDisable() {
        if (this.quizManager != null) {
            this.quizManager.shutdown();
        }
        if (this.statsManager != null) {
            this.statsManager.flushDirtyBlocking();
        }
        if (this.asyncExecutor != null) {
            this.asyncExecutor.shutdown(Duration.ofSeconds(5));
        }
    }

    /**
     * Reloads settings, messages and question caches without touching the SQL executor.
     */
    public void reloadPluginState() {
        this.reloadConfig();
        this.settings = ConfigurationLoader.loadSettings(this.getConfig());

        if (this.loggerBridge == null) {
            this.loggerBridge = new PluginLogger(this.getLogger(), this.settings.debug());
        } else {
            this.loggerBridge.setDebugEnabled(this.settings.debug());
        }

        if (this.quizManager != null) {
            this.quizManager.shutdown();
        }

        YamlConfiguration messagesConfig = LanguageResolver.resolve(this, this.settings.language(), this.loggerBridge);
        YamlConfiguration questionsConfig = ConfigurationLoader.loadYaml(this.resolveDataFile("questions.yml"));
        YamlConfiguration rewardsConfig = ConfigurationLoader.loadYaml(this.resolveDataFile("rewards.yml"));

        this.messageService = new MessageService(messagesConfig);

        Map<String, RewardDefinition> rewardProfiles = ConfigurationLoader.loadRewardProfiles(rewardsConfig, this.loggerBridge);
        List<Question> questions = ConfigurationLoader.loadQuestions(questionsConfig, this.loggerBridge);

        this.questionPool = new QuestionPool(questions, this.settings, this.loggerBridge);
        this.rewardManager = new RewardManager(this, rewardProfiles, this.settings, this.loggerBridge);
        this.roundManager = new RoundManager(this, this.settings, this.messageService, this.rewardManager, this.statsManager, this.loggerBridge);
        this.answerService = new AnswerService(this, this.settings, this.messageService, this.roundManager);
        this.quizManager = new QuizManager(this, this.settings, this.messageService, this.questionPool, this.roundManager);
        this.quizManager.refreshScheduler();

        if (questions.isEmpty()) {
            this.loggerBridge.warn("No quiz questions were loaded from questions.yml.");
        }
    }

    public void markPlayerJoin(UUID playerId) {
        this.playerSessions.put(playerId, System.currentTimeMillis());
    }

    public void clearPlayerSession(UUID playerId) {
        this.playerSessions.remove(playerId);
    }

    /**
     * Simple eligibility check to discourage instant join-and-claim abuse.
     */
    public boolean isRewardEligible(Player player) {
        if (this.settings == null || this.settings.minOnlineSeconds() <= 0L) {
            return true;
        }

        long joinedAt = this.playerSessions.getOrDefault(player.getUniqueId(), System.currentTimeMillis());
        return System.currentTimeMillis() - joinedAt >= this.settings.minOnlineSeconds() * 1000L;
    }

    public PluginLogger getLoggerBridge() {
        return this.loggerBridge;
    }

    public MessageService getMessageService() {
        return this.messageService;
    }

    public NowQuizSettings getSettings() {
        return this.settings;
    }

    public StatsManager getStatsManager() {
        return this.statsManager;
    }

    public QuestionPool getQuestionPool() {
        return this.questionPool;
    }

    public RewardManager getRewardManager() {
        return this.rewardManager;
    }

    public RoundManager getRoundManager() {
        return this.roundManager;
    }

    public AnswerService getAnswerService() {
        return this.answerService;
    }

    public QuizManager getQuizManager() {
        return this.quizManager;
    }

    private void registerCommand() {
        PluginCommand pluginCommand = Objects.requireNonNull(this.getCommand("nowquiz"), "The nowquiz command must be defined in plugin.yml");
        NowQuizCommand command = new NowQuizCommand(this);
        pluginCommand.setExecutor(command);
        pluginCommand.setTabCompleter(command);
    }

    private void registerListeners() {
        this.getServer().getPluginManager().registerEvents(new QuizChatListener(this), this);
        this.getServer().getPluginManager().registerEvents(new PlayerSessionListener(this), this);
    }

    private void saveBundledResources() {
        if (!this.getDataFolder().exists() && !this.getDataFolder().mkdirs()) {
            throw new IllegalStateException("Could not create the NowQuiz data folder.");
        }

        this.saveDefaultConfig();
        this.saveIfMissing("questions.yml");
        this.saveIfMissing("rewards.yml");
        this.saveIfMissing("lang/es.yml");
        this.saveIfMissing("lang/en.yml");
    }

    private void saveIfMissing(String resourcePath) {
        File file = this.resolveDataFile(resourcePath);
        if (!file.exists()) {
            this.saveResource(resourcePath, false);
        }
    }

    private File resolveDataFile(String fileName) {
        return new File(this.getDataFolder(), fileName);
    }
}
