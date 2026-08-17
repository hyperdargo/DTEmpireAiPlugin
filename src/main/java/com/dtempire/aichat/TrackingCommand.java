package com.dtempire.aichat;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/** Command handler for /dtstatus, /dttracking, /dtempireai. */
public class TrackingCommand implements CommandExecutor {

    private final DTEmpireAIChatPlugin plugin;

    public TrackingCommand(DTEmpireAIChatPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase();

        if (cmd.equals("dtstatus")) {
            if (!sender.hasPermission("dtempire.tracking.status")) {
                sender.sendMessage(plugin.color("&8[&bDTEmpire&8] &cNo permission."));
                return true;
            }
            sender.sendMessage(plugin.color("&8[&bDTEmpire&8] &aPosting server status to Discord..."));
            plugin.getTrackingReporter().postNow().thenAccept(success -> {
                if (success) {
                    sender.sendMessage(plugin.color("&8[&bDTEmpire&8] &aStatus posted."));
                } else {
                    sender.sendMessage(plugin.color("&8[&bDTEmpire&8] &cFailed to post (webhook/bot token or channel not configured)."));
                }
            });
            return true;
        }

        if (cmd.equals("dttracking")) {
            if (!sender.hasPermission("dtempire.tracking.toggle")) {
                sender.sendMessage(plugin.color("&8[&bDTEmpire&8] &cNo permission."));
                return true;
            }
            if (args.length != 1) {
                sender.sendMessage(plugin.color("&8[&bDTEmpire&8] &eUsage: /dttracking <on|off>"));
                return true;
            }
            boolean enabled = args[0].equalsIgnoreCase("on") || args[0].equalsIgnoreCase("true");
            plugin.getConfig().set("tracking.enabled", enabled);
            plugin.saveConfig();
            if (enabled) {
                plugin.startTracking();
                sender.sendMessage(plugin.color("&8[&bDTEmpire&8] &aServer tracking enabled."));
            } else {
                plugin.stopTracking();
                sender.sendMessage(plugin.color("&8[&bDTEmpire&8] &cServer tracking disabled."));
            }
            return true;
        }

        if (cmd.equals("dtempireai")) {
            if (!sender.hasPermission("dtempire.tracking.admin")) {
                sender.sendMessage(plugin.color("&8[&bDTEmpire&8] &cNo permission."));
                return true;
            }
            if (args.length == 0) {
                sender.sendMessage(plugin.color("&8[&bDTEmpire&8] &eUsage: /dtempireai <restart|reload|status>"));
                return true;
            }
            String sub = args[0].toLowerCase();
            if (sub.equals("restart") || sub.equals("reload")) {
                sender.sendMessage(plugin.color("&8[&bDTEmpire&8] &aReloading config and restarting tracking..."));
                plugin.reloadAndRestartTracking();
                sender.sendMessage(plugin.color("&8[&bDTEmpire&8] &aDone."));
                return true;
            }
            if (sub.equals("status")) {
                sender.sendMessage(plugin.color("&8[&bDTEmpire&8] &eTracking: " + (plugin.isTrackingEnabled() ? "&aENABLED" : "&cDISABLED")));
                sender.sendMessage(plugin.color("&8[&bDTEmpire&8] &eConfigured: " + (plugin.getTrackingReporter().isConfigured() ? "&aYES" : "&cNO")));
                return true;
            }
            sender.sendMessage(plugin.color("&8[&bDTEmpire&8] &eUnknown subcommand. Use: restart, reload, status"));
            return true;
        }
        return false;
    }
}