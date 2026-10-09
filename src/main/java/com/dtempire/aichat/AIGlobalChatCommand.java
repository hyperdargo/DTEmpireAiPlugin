package com.dtempire.aichat;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Command executor for /ai <message>, /messageai <message>, /heyai <message>.
 * Allows players to prompt the AI directly to explain, translate, or answer another player
 * in public global chat.
 */
public class AIGlobalChatCommand implements CommandExecutor {

    private final DTEmpireAIChatPlugin plugin;
    private final PublicChatAIHandler publicChatAIHandler;

    public AIGlobalChatCommand(DTEmpireAIChatPlugin plugin, PublicChatAIHandler publicChatAIHandler) {
        this.plugin = plugin;
        this.publicChatAIHandler = publicChatAIHandler;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(plugin.color("&8&m────────────────────────────────────────"));
            sender.sendMessage(plugin.color("&b&lHermes Global AI Chat Assistant"));
            sender.sendMessage(plugin.color("&e/ai <message/question>&7 - Formulates an answer directly in global chat"));
            sender.sendMessage(plugin.color("&7Tip: You can also just type naturally in public chat:"));
            sender.sendMessage(plugin.color("  &ehey aichat <message>"));
            sender.sendMessage(plugin.color("  &emessage ai <message>"));
            sender.sendMessage(plugin.color("  &ehey ai tell him <explanation>"));
            sender.sendMessage(plugin.color("&8&m────────────────────────────────────────"));
            return true;
        }

        String message = String.join(" ", args).trim();

        if (sender instanceof Player player) {
            publicChatAIHandler.processExplicitCommand(player, message);
        } else {
            // Console execution
            publicChatAIHandler.processConsoleCommand(message);
        }

        return true;
    }
}
