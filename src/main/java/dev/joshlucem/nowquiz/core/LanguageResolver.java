package dev.joshlucem.nowquiz.core;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Loads built-in and optional custom language files from the plugin data folder.
 */
public final class LanguageResolver {

    private LanguageResolver() {
    }

    public static YamlConfiguration resolve(JavaPlugin plugin, String configuredLanguage, PluginLogger logger) {
        File dataFolder = plugin.getDataFolder();
        File langFolder = new File(dataFolder, "lang");
        String language = configuredLanguage == null ? "es" : configuredLanguage.trim().toLowerCase(Locale.ROOT);

        return switch (language) {
            case "en" -> loadBundled(plugin, "lang/en.yml");
            case "custom" -> loadCustom(plugin, langFolder, logger);
            case "es" -> loadBundled(plugin, "lang/es.yml");
            default -> {
                logger.warn("Unsupported language '" + configuredLanguage + "'. Falling back to Spanish (es).");
                yield loadBundled(plugin, "lang/es.yml");
            }
        };
    }

    private static YamlConfiguration loadCustom(JavaPlugin plugin, File langFolder, PluginLogger logger) {
        YamlConfiguration base = loadBundled(plugin, "lang/es.yml");
        File customFile = new File(langFolder, "custom.yml");
        if (!customFile.exists()) {
            logger.warn("lang: custom is configured, but lang/custom.yml does not exist. Falling back to Spanish (es).");
            return base;
        }

        YamlConfiguration custom = YamlConfiguration.loadConfiguration(customFile);
        mergeInto(base, custom);
        return base;
    }

    private static YamlConfiguration loadBundled(JavaPlugin plugin, String resourcePath) {
        InputStream resourceStream = plugin.getResource(resourcePath);
        if (resourceStream == null) {
            return new YamlConfiguration();
        }

        try (InputStream inputStream = resourceStream;
             InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (Exception exception) {
            return new YamlConfiguration();
        }
    }

    /**
     * Custom translations can override only the keys they care about.
     */
    private static void mergeInto(YamlConfiguration target, YamlConfiguration overlay) {
        copySection("", target, overlay);
    }

    private static void copySection(String path, YamlConfiguration target, ConfigurationSection source) {
        for (String key : source.getKeys(false)) {
            String fullPath = path.isEmpty() ? key : path + "." + key;
            Object value = source.get(key);
            if (value instanceof ConfigurationSection nestedSection) {
                copySection(fullPath, target, nestedSection);
                continue;
            }
            target.set(fullPath, value);
        }
    }
}
