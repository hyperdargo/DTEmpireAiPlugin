package com.dtempire.aichat;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class AIExitCommand implements CommandExecutor {

    private final DTEmpireAIChatPlugin plugin;
    private final AIChatManager manager;

    public AIExitCommand(DTEmpireAIChatPlugin plugin, AIChatManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (manager.hasSession(player)) {
            manager.endSession(player);
            player.sendMessage(plugin.color(plugin.getConfig().getString("messages.session-ended",
                    "Ended your AI chat session.")));
        } else {
            player.sendMessage(plugin.color(plugin.getConfig().getString("messages.no-active-session",
                    "No active AI chat session.")));
        }
        return true;
    }
}