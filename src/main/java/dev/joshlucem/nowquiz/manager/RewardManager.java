package dev.joshlucem.nowquiz.manager;

import dev.joshlucem.nowquiz.core.MessageService;
import dev.joshlucem.nowquiz.core.NowQuizPlugin;
import dev.joshlucem.nowquiz.core.NowQuizSettings;
import dev.joshlucem.nowquiz.core.PluginLogger;
import dev.joshlucem.nowquiz.quiz.Question;
import dev.joshlucem.nowquiz.quiz.RewardDefinition;
import dev.joshlucem.nowquiz.quiz.RewardItem;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Applies configured rewards on the main thread.
 */
public final class RewardManager {

    private final NowQuizPlugin plugin;
    private final MessageService messageService;
    private final PluginLogger logger;
    private final Map<String, RewardDefinition> profiles;
    private final MiniMessage miniMessage;
    private final boolean hookVault;
    private Object economyProvider;
    private boolean warnedAboutVault;

    public RewardManager(NowQuizPlugin plugin, Map<String, RewardDefinition> profiles, NowQuizSettings settings, PluginLogger logger) {
        this.plugin = plugin;
        this.messageService = plugin.getMessageService();
        this.logger = logger;
        this.profiles = new LinkedHashMap<>(profiles);
        this.miniMessage = MiniMessage.miniMessage();
        this.hookVault = settings.hookVault();
        this.economyProvider = this.resolveEconomyProvider();
        this.warnedAboutVault = false;
    }

    public RewardDefinition resolveRewards(Question question) {
        RewardDefinition base = this.profiles.getOrDefault(
            question.rewardProfile().toLowerCase(Locale.ROOT),
            this.profiles.getOrDefault("default", RewardDefinition.empty())
        );
        return base.merge(question.rewardOverrides());
    }

    /**
     * Grants all rewards for the provided question to a player.
     */
    public void grant(Player player, Question question, long streak) {
        RewardDefinition rewards = this.resolveRewards(question);
        if (rewards.equals(RewardDefinition.empty())) {
            return;
        }

        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("player", player.getName());
        placeholders.put("streak", Long.toString(streak));
        placeholders.put("correct", question.correctAnswerDisplay());
        placeholders.put("amount", rewards.money() == null ? "0" : Long.toString(Math.round(rewards.money())));

        if (rewards.money() != null && rewards.money() > 0) {
            if (this.depositMoney(player, rewards.money())) {
                this.messageService.send(player, "rewards.money", Map.of("amount", Long.toString(Math.round(rewards.money()))));
            }
        }

        if (rewards.xp() != null && rewards.xp() > 0) {
            player.giveExp(rewards.xp());
            this.messageService.send(player, "rewards.xp", Map.of("amount", Integer.toString(rewards.xp())));
        }

        for (RewardItem rewardItem : rewards.items()) {
            ItemStack itemStack = new ItemStack(rewardItem.material(), rewardItem.amount());
            if (!rewardItem.name().isBlank() || !rewardItem.lore().isEmpty()) {
                ItemMeta itemMeta = itemStack.getItemMeta();
                if (!rewardItem.name().isBlank()) {
                    itemMeta.displayName(this.miniMessage.deserialize(applyPlaceholders(rewardItem.name(), placeholders)));
                }
                if (!rewardItem.lore().isEmpty()) {
                    List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
                    for (String line : rewardItem.lore()) {
                        lore.add(this.miniMessage.deserialize(applyPlaceholders(line, placeholders)));
                    }
                    itemMeta.lore(lore);
                }
                itemStack.setItemMeta(itemMeta);
            }

            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(itemStack);
            leftovers.values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
            this.messageService.send(player, "rewards.item", Map.of(
                "amount", Integer.toString(rewardItem.amount()),
                "item", this.describeRewardItem(rewardItem)
            ));
        }

        int commandRewards = 0;
        for (String command : rewards.legacyCommands()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), applyPlaceholders(command, placeholders));
            commandRewards++;
        }

        for (String command : rewards.consoleCommands()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), applyPlaceholders(command, placeholders));
            commandRewards++;
        }

        for (String command : rewards.playerCommands()) {
            player.performCommand(applyPlaceholders(command, placeholders));
            commandRewards++;
        }

        if (commandRewards > 0) {
            this.messageService.send(player, "rewards.commands", Map.of("amount", Integer.toString(commandRewards)));
        }
    }

    private boolean depositMoney(Player player, double amount) {
        if (this.economyProvider == null) {
            if (!this.warnedAboutVault) {
                this.logger.warn("Vault economy rewards are configured, but no Vault economy provider is available. Money rewards will be skipped.");
                this.warnedAboutVault = true;
            }
            return false;
        }

        try {
            Method method = this.economyProvider.getClass().getMethod("depositPlayer", OfflinePlayer.class, double.class);
            method.invoke(this.economyProvider, player, amount);
            return true;
        } catch (ReflectiveOperationException exception) {
            this.logger.warn("Failed to deposit a Vault reward for " + player.getName() + ".", exception);
            return false;
        }
    }

    private Object resolveEconomyProvider() {
        if (!this.hookVault) {
            this.logger.debug("Vault hook disabled in config.");
            return null;
        }

        try {
            Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
            @SuppressWarnings({"rawtypes", "unchecked"})
            RegisteredServiceProvider<?> registration = Bukkit.getServicesManager().getRegistration((Class) economyClass);
            if (registration == null) {
                this.logger.debug("Vault was not found or no economy provider is registered.");
                return null;
            }

            this.logger.debug("Vault economy provider detected.");
            return registration.getProvider();
        } catch (ClassNotFoundException exception) {
            this.logger.debug("Vault classes are not present.");
            return null;
        }
    }

    private static String applyPlaceholders(String raw, Map<String, String> placeholders) {
        String rendered = raw;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            rendered = rendered.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return rendered;
    }

    private String describeRewardItem(RewardItem rewardItem) {
        if (!rewardItem.name().isBlank()) {
            return PlainTextComponentSerializer.plainText().serialize(this.miniMessage.deserialize(rewardItem.name()));
        }

        String[] parts = rewardItem.material().name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
    }
}
