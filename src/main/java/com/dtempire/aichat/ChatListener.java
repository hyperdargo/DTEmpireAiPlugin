package com.dtempire.aichat;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class ChatListener implements Listener {

    private final DTEmpireAIChatPlugin plugin;
    private final AIChatManager manager;

    public ChatListener(DTEmpireAIChatPlugin plugin, AIChatManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!manager.hasSession(player)) return;

        // Private AI mode: nobody else sees this message
        event.setCancelled(true);

        String message = event.getMessage();
        AIChatSession session = manager.getSession(player);
        session.addMessage("user", message);
        player.sendMessage(plugin.color(plugin.getConfig().getString("messages.player-prefix",
                "&8[&aYou&8] &r") + message));

        session.sendToAI(plugin).thenAccept(reply -> {
            if (reply == null) {
                player.sendMessage(plugin.color(plugin.getConfig().getString("messages.ai-error",
                        "&8[&bAI&8] &cError contacting AI service. Please try again.")));
                return;
            }
            String prefix = plugin.color(plugin.getConfig().getString("messages.ai-prefix",
                    "&8[&bAI&8] &r"));
            player.sendMessage(prefix + reply);
            session.addMessage("assistant", reply);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Clean up session on player disconnect
        if (manager.hasSession(event.getPlayer())) {
            manager.endSession(event.getPlayer());
        }
    }
}