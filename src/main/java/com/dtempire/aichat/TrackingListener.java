package com.dtempire.aichat;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

/** Listens for player join/leave to track metrics and send welcome. */
public class TrackingListener implements Listener {

    private final DTEmpireAIChatPlugin plugin;

    public TrackingListener(DTEmpireAIChatPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getManager().onJoin(event.getPlayer());

        // Welcome message with /aihelp hint
        if (plugin.getConfig().getBoolean("welcome.enabled", true)) {
            String msg = plugin.getConfig().getString("welcome.message",
                    "&8[&bDTEmpire&8] &rWelcome! Type &e/aihelp&r for AI features.");
            int delay = plugin.getConfig().getInt("welcome.delay-ticks", 40);
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (event.getPlayer().isOnline()) {
                        event.getPlayer().sendMessage(plugin.color(msg));
                    }
                }
            }.runTaskLater(plugin, delay);
        }

        // Instant Discord update on join (if tracking enabled)
        if (plugin.isTrackingEnabled()) {
            plugin.getTrackingReporter().postNow();
        }
    }

    @EventHandler
    public void onLeave(PlayerQuitEvent event) {
        plugin.getManager().onLeave(event.getPlayer());

        // Instant Discord update on leave
        if (plugin.isTrackingEnabled()) {
            plugin.getTrackingReporter().postNow();
        }
    }
}