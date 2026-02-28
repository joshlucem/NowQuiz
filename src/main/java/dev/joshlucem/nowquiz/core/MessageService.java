package dev.joshlucem.nowquiz.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Centralizes MiniMessage rendering for configurable text.
 */
public final class MessageService {

    private final MiniMessage miniMessage;
    private final YamlConfiguration messages;
    private final Component prefix;

    public MessageService(YamlConfiguration messages) {
        this.miniMessage = MiniMessage.miniMessage();
        this.messages = messages;
        this.prefix = this.miniMessage.deserialize(messages.getString("prefix", "<gray>NowQuiz</gray>"));
    }

    public Component render(String path) {
        return this.render(path, Map.of());
    }

    public Component render(String path, Map<String, String> placeholders) {
        String template = this.messages.getString(path, "<red>Missing message: " + path + "</red>");
        List<TagResolver> resolvers = new ArrayList<>();
        resolvers.add(Placeholder.component("prefix", this.prefix));
        placeholders.forEach((key, value) -> resolvers.add(Placeholder.unparsed(key, value == null ? "" : value)));
        return this.miniMessage.deserialize(template, TagResolver.resolver(resolvers));
    }

    public String string(String path, String fallback) {
        return this.messages.getString(path, fallback);
    }

    public void send(CommandSender sender, String path) {
        sender.sendMessage(this.render(path));
    }

    public void send(CommandSender sender, String path, Map<String, String> placeholders) {
        sender.sendMessage(this.render(path, placeholders));
    }
}
