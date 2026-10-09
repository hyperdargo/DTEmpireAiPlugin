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
        sender.sendMessage(plugin.color("&8&m────────────────────────────────────────"));
        sender.sendMessage(plugin.color("&8[&bHermes AI Game Master&8] &r&lCommands"));
        sender.sendMessage(plugin.color("&e/ai <question>&r - Ask Hermes to answer/explain in public global chat"));
        sender.sendMessage(plugin.color("&7  (Or in chat: &ehey aichat <msg>&7, &emessage ai <msg>&7, &ehey ai tell him...&7)"));
        sender.sendMessage(plugin.color("&e/aichat <msg>&r - Talk privately with the server AI assistant"));
        sender.sendMessage(plugin.color("&e/aiexit&r - End your private AI chat session"));
        sender.sendMessage(plugin.color("&e/aidaily&r - View & claim tailored daily quests & rewards"));
        sender.sendMessage(plugin.color("&e/bounty <place|list>&r - Place Diamond bounties on wanted outlaws"));
        sender.sendMessage(plugin.color("&e/watchdog report <player>&r - Report suspicious players to Watchdog"));
        if (sender.hasPermission("dtempire.admin") || sender.isOp()) {
            sender.sendMessage(plugin.color("&c/aiadmin &7- Game Master event controls & player gifts"));
            sender.sendMessage(plugin.color("&c/watchdog &7- Anti-cheat inspections, aura bot tests & bans"));
        }
        sender.sendMessage(plugin.color("&8&m────────────────────────────────────────"));
        return true;
    }
}
