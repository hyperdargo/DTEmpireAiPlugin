package com.dtempire.aichat;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class AIHelpCommand implements CommandExecutor {

    private final DTEmpireAIChatPlugin plugin;

    public AIHelpCommand(DTEmpireAIChatPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String help = plugin.getConfig().getString("messages.aihelp",
                "&8[&bDTEmpire AI&8] &r&lCommands\n" +
                "&e/aichat <msg>&r - Talk privately with our AI assistant\n" +
                "&e/aiexit&r - End your private AI chat\n" +
                "&e/aihelp&r - Show this help\n" +
                "&7The AI answers Minecraft & DTEmpire topics only.");
        for (String line : help.split("\\\\n")) {
            sender.sendMessage(plugin.color(line));
        }
        return true;
    }
}