package dev.joshlucem.nowquiz.listeners;

import dev.joshlucem.nowquiz.core.NowQuizPlugin;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Handles optional chat-based answers without cancelling unrelated chat traffic.
 */
public final class QuizChatListener implements Listener {

    private final NowQuizPlugin plugin;

    public QuizChatListener(NowQuizPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onAsyncChat(AsyncChatEvent event) {
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        if (!this.plugin.getAnswerService().shouldCaptureChat(event.getPlayer(), message)) {
            return;
        }

        event.setCancelled(true);
        String answer = this.plugin.getAnswerService().extractChatAnswer(message);
        Bukkit.getScheduler().runTask(this.plugin, () -> this.plugin.getAnswerService().submitChatAnswer(event.getPlayer(), answer));
    }
}
