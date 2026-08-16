package com.dtempire.aichat;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class AIChatCommand implements CommandExecutor {

    private final DTEmpireAIChatPlugin plugin;
    private final AIChatManager manager;

    public AIChatCommand(DTEmpireAIChatPlugin plugin, AIChatManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        String joined = String.join(" ", args).trim();

        if (!manager.hasSession(player)) {
            // start a fresh session
            manager.startSession(player, null);
            if (joined.isEmpty()) {
                // /aichat alone → just enter AI mode
                player.sendMessage(plugin.color(plugin.getConfig().getString("messages.session-started",
                        "&8[&bAI&8] &aYou are now in AI chat mode. Just type normally to talk to the AI. Use &e/aiexit &ato leave.")));
            } else {
                // /aichat <message> → start session AND send first message
                AIChatSession session = manager.getSession(player);
                session.addMessage("user", joined);
                player.sendMessage(plugin.color(plugin.getConfig().getString("messages.player-prefix",
                        "&8[&aYou&8] &r") + joined));

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
        } else {
            // Already in AI session
            if (joined.isEmpty()) {
                player.sendMessage(plugin.color(plugin.getConfig().getString("messages.usage-aichat",
                        "&8[&bAI&8] &rAlready in AI chat mode. Just type in chat to talk. Use &e/aiexit &rto leave.")));
            } else {
                AIChatSession session = manager.getSession(player);
                session.addMessage("user", joined);
                player.sendMessage(plugin.color(plugin.getConfig().getString("messages.player-prefix",
                        "&8[&aYou&8] &r") + joined));

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
        }

        return true;
    }
}