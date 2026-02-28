package dev.joshlucem.nowquiz.listeners;

import dev.joshlucem.nowquiz.core.NowQuizPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Tracks simple session timing for eligibility checks.
 */
public final class PlayerSessionListener implements Listener {

    private final NowQuizPlugin plugin;

    public PlayerSessionListener(NowQuizPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        this.plugin.markPlayerJoin(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        this.plugin.clearPlayerSession(event.getPlayer().getUniqueId());
    }
}
