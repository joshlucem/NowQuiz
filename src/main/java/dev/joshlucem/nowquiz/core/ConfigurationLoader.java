package dev.joshlucem.nowquiz.core;

import dev.joshlucem.nowquiz.quiz.AnswerOption;
import dev.joshlucem.nowquiz.quiz.Question;
import dev.joshlucem.nowquiz.quiz.QuestionType;
import dev.joshlucem.nowquiz.quiz.RewardDefinition;
import dev.joshlucem.nowquiz.quiz.RewardItem;
import dev.joshlucem.nowquiz.util.BroadcastScope;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Loads user-editable YAML files into typed runtime objects.
 */
public final class ConfigurationLoader {

    private ConfigurationLoader() {
    }

    public static YamlConfiguration loadYaml(File file) {
        return YamlConfiguration.loadConfiguration(file);
    }

    public static NowQuizSettings loadSettings(FileConfiguration config) {
        return new NowQuizSettings(
            config.getString("lang", "es"),
            config.getBoolean("enabled", true),
            config.getBoolean("auto.enabled", true),
            Math.max(15, config.getInt("auto.interval-seconds", 300)),
            Math.max(5, config.getInt("round.time-limit-seconds", 30)),
            config.getBoolean("round.allow-multiple-winners", false),
            config.getBoolean("answer.allow-click", true),
            config.getBoolean("answer.allow-chat", true),
            config.getString("answer.chat-prefix", "!"),
            Math.max(0L, config.getLong("answer.cooldown-ms", 750L)),
            Math.max(0L, config.getLong("answer.min-human-ms", 250L)),
            config.getBoolean("question.avoid-repeats", true),
            Math.max(0, config.getInt("question.repeat-cooldown", 5)),
            config.getBoolean("hooks.vault", true),
            Math.max(0L, config.getLong("eligibility.min-online-seconds", 0L)),
            config.getBoolean("debug", false),
            BroadcastScope.fromConfig(config.getString("broadcast.scope", "GLOBAL")),
            config.getString("broadcast.permission", "nowquiz.use"),
            config.getString("broadcast.default-world", "")
        );
    }

    public static Map<String, RewardDefinition> loadRewardProfiles(YamlConfiguration config, PluginLogger logger) {
        Map<String, RewardDefinition> profiles = new LinkedHashMap<>();
        ConfigurationSection section = config.getConfigurationSection("profiles");
        if (section == null) {
            return profiles;
        }

        for (String key : section.getKeys(false)) {
            ConfigurationSection profileSection = section.getConfigurationSection(key);
            if (profileSection == null) {
                continue;
            }
            profiles.put(key.toLowerCase(Locale.ROOT), rewardFromSection(profileSection, logger));
        }

        return profiles;
    }

    public static List<Question> loadQuestions(YamlConfiguration config, PluginLogger logger) {
        List<Question> questions = new ArrayList<>();

        for (String category : config.getKeys(false)) {
            List<Map<?, ?>> questionMaps = config.getMapList(category);
            for (Map<?, ?> rawQuestion : questionMaps) {
                Question question = questionFromMap(category, rawQuestion, logger);
                if (question != null) {
                    questions.add(question);
                }
            }
        }

        return questions;
    }

    private static Question questionFromMap(String category, Map<?, ?> rawQuestion, PluginLogger logger) {
        String id = stringValue(rawQuestion, "id", "").trim();
        String rawType = stringValue(rawQuestion, "type", "MULTIPLE");
        QuestionType type;

        if (id.isBlank()) {
            logger.warn("Skipped a question without a stable id in category " + category + ".");
            return null;
        }

        try {
            type = QuestionType.valueOf(rawType.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            logger.warn("Skipped question " + id + " because the type is invalid: " + rawType + ".");
            return null;
        }

        String prompt = stringValue(rawQuestion, "question", "").trim();
        if (prompt.isBlank()) {
            logger.warn("Skipped question " + id + " because the prompt is empty.");
            return null;
        }

        LinkedHashMap<String, AnswerOption> options = parseOptions(type, rawQuestion, logger, id);
        String correct = stringValue(rawQuestion, "correct", "").trim();
        if (correct.isBlank()) {
            logger.warn("Skipped question " + id + " because the correct answer is empty.");
            return null;
        }

        Set<String> acceptedAnswers = new LinkedHashSet<>();
        acceptedAnswers.add(correct);
        for (String alias : stringList(rawQuestion.get("aliases"))) {
            acceptedAnswers.add(alias);
        }

        String difficulty = stringValue(rawQuestion, "difficulty", "default").toLowerCase(Locale.ROOT);
        RewardDefinition overrides = rewardFromMap(mapValue(rawQuestion.get("rewards")), logger);

        if ((type == QuestionType.MULTIPLE || type == QuestionType.TRUE_FALSE)
            && !options.containsKey(correct.toUpperCase(Locale.ROOT))) {
            logger.warn("Skipped question " + id + " because the correct option does not exist.");
            return null;
        }

        return new Question(category, id, type, prompt, options, correct, acceptedAnswers, difficulty, overrides);
    }

    private static LinkedHashMap<String, AnswerOption> parseOptions(
        QuestionType type,
        Map<?, ?> rawQuestion,
        PluginLogger logger,
        String questionId
    ) {
        LinkedHashMap<String, AnswerOption> options = new LinkedHashMap<>();
        Map<String, Object> rawOptions = mapValue(rawQuestion.get("options"));

        if (rawOptions.isEmpty() && type == QuestionType.TRUE_FALSE) {
            rawOptions.put("A", "True");
            rawOptions.put("B", "False");
        }

        for (Map.Entry<String, Object> entry : rawOptions.entrySet()) {
            String key = entry.getKey().toUpperCase(Locale.ROOT);
            String text = Objects.toString(entry.getValue(), "").trim();
            if (key.isBlank() || text.isBlank()) {
                continue;
            }
            options.put(key, new AnswerOption(key, text));
        }

        if ((type == QuestionType.MULTIPLE || type == QuestionType.TRUE_FALSE) && options.isEmpty()) {
            logger.warn("Question " + questionId + " has no options and was skipped.");
        }

        return options;
    }

    private static RewardDefinition rewardFromSection(ConfigurationSection section, PluginLogger logger) {
        return new RewardDefinition(
            section.contains("money") ? section.getDouble("money") : null,
            section.contains("xp") ? section.getInt("xp") : null,
            parseItems(section.getMapList("items"), logger),
            stringList(section.getList("commands")),
            stringList(section.getStringList("commands.console")),
            stringList(section.getStringList("commands.player"))
        );
    }

    private static RewardDefinition rewardFromMap(Map<String, Object> map, PluginLogger logger) {
        if (map.isEmpty()) {
            return RewardDefinition.empty();
        }

        Double money = map.containsKey("money") ? doubleValue(map.get("money")) : null;
        Integer xp = map.containsKey("xp") ? intValue(map.get("xp")) : null;
        List<Map<?, ?>> rawItems = listOfMaps(map.get("items"));
        List<String> commands = stringList(map.get("commands"));
        Map<String, Object> commandsSection = mapValue(map.get("commands"));
        return new RewardDefinition(
            money,
            xp,
            parseItems(rawItems, logger),
            commands,
            stringList(commandsSection.get("console")),
            stringList(commandsSection.get("player"))
        );
    }

    private static List<RewardItem> parseItems(List<Map<?, ?>> rawItems, PluginLogger logger) {
        List<RewardItem> items = new ArrayList<>();
        for (Map<?, ?> rawItem : rawItems) {
            String materialName = stringValue(rawItem, "material", "");
            Material material = Material.matchMaterial(materialName);
            if (material == null || material.isAir()) {
                logger.warn("Skipped a reward item with invalid material: " + materialName + ".");
                continue;
            }

            items.add(new RewardItem(
                material,
                Math.max(1, intValue(rawItem.get("amount"), 1)),
                stringValue(rawItem, "name", ""),
                stringList(rawItem.get("lore"))
            ));
        }
        return items;
    }

    private static String stringValue(Map<?, ?> map, String key, String fallback) {
        Object value = map.get(key);
        return value == null ? fallback : Objects.toString(value, fallback);
    }

    private static Map<String, Object> mapValue(Object raw) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (raw instanceof ConfigurationSection section) {
            for (String key : section.getKeys(false)) {
                map.put(key, section.get(key));
            }
            return map;
        }

        if (raw instanceof Map<?, ?> rawMap) {
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                if (entry.getKey() != null) {
                    map.put(Objects.toString(entry.getKey(), ""), entry.getValue());
                }
            }
        }
        return map;
    }

    private static List<String> stringList(Object raw) {
        List<String> values = new ArrayList<>();
        if (raw instanceof Collection<?> collection) {
            for (Object entry : collection) {
                String value = Objects.toString(entry, "").trim();
                if (!value.isBlank()) {
                    values.add(value);
                }
            }
        } else if (raw instanceof String string && !string.isBlank()) {
            values.add(string.trim());
        }
        return values;
    }

    private static List<Map<?, ?>> listOfMaps(Object raw) {
        List<Map<?, ?>> values = new ArrayList<>();
        if (raw instanceof Collection<?> collection) {
            for (Object entry : collection) {
                if (entry instanceof Map<?, ?> map) {
                    values.add(map);
                }
            }
        }
        return values;
    }

    private static Double doubleValue(Object raw) {
        if (raw instanceof Number number) {
            return number.doubleValue();
        }

        try {
            return raw == null ? null : Double.parseDouble(raw.toString());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static Integer intValue(Object raw) {
        return intValue(raw, 0);
    }

    private static Integer intValue(Object raw, int fallback) {
        if (raw instanceof Number number) {
            return number.intValue();
        }

        try {
            return raw == null ? fallback : Integer.parseInt(raw.toString());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }
}
