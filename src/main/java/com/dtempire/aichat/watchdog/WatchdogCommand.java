package com.dtempire.aichat.watchdog;

import com.dtempire.aichat.DTEmpireAIChatPlugin;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/** Command executor for /watchdog [report|test|inspect|ban|stats]. */
public class WatchdogCommand implements CommandExecutor {

    private final DTEmpireAIChatPlugin plugin;
    private final WatchdogManager watchdogManager;

    public WatchdogCommand(DTEmpireAIChatPlugin plugin, WatchdogManager watchdogManager) {
        this.plugin = plugin;
        this.watchdogManager = watchdogManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(plugin.color("&8[&cWatchdog&8] &c&lWatchdog Anti-Cheat"));
            sender.sendMessage(plugin.color("&e/watchdog report <player>&7 - Report a suspected hacker"));
            if (sender.hasPermission("dtempire.watchdog.staff")) {
                sender.sendMessage(plugin.color("&e/watchdog test <player>&7 - Dispatch KillAura detection bot"));
                sender.sendMessage(plugin.color("&e/watchdog inspect <player>&7 - View player violation levels & CPS"));
                sender.sendMessage(plugin.color("&e/watchdog ban <player>&7 - Force execute Watchdog ban"));
                sender.sendMessage(plugin.color("&e/watchdog stats&7 - View total Watchdog bans"));
            }
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "report":
                if (args.length < 2) {
                    sender.sendMessage(plugin.color("&cUsage: /watchdog report <player>"));
                    return true;
                }
                Player repTarget = Bukkit.getPlayer(args[1]);
                if (repTarget == null) {
                    sender.sendMessage(plugin.color("&cPlayer not found or offline."));
                    return true;
                }
                if (sender instanceof Player p && p.equals(repTarget)) {
                    sender.sendMessage(plugin.color("&cYou cannot report yourself."));
                    return true;
                }
                // Summon silent aura trap
                WatchdogBot.summonAuraTrap(plugin, repTarget, watchdogManager);
                sender.sendMessage(plugin.color("&8[&cWatchdog&8] &aThanks for your report! Watchdog is silently investigating &e" + repTarget.getName() + "&a."));
                break;

            case "test":
                if (!sender.hasPermission("dtempire.watchdog.staff")) {
                    sender.sendMessage(plugin.color("&cNo permission."));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(plugin.color("&cUsage: /watchdog test <player>"));
                    return true;
                }
                Player testTarget = Bukkit.getPlayer(args[1]);
                if (testTarget == null) {
                    sender.sendMessage(plugin.color("&cPlayer not found or offline."));
                    return true;
                }
                WatchdogBot.summonAuraTrap(plugin, testTarget, watchdogManager);
                sender.sendMessage(plugin.color("&8[&cWatchdog&8] &aSpawned orbiting KillAura bot on &e" + testTarget.getName()));
                break;

            case "inspect":
                if (!sender.hasPermission("dtempire.watchdog.staff")) {
                    sender.sendMessage(plugin.color("&cNo permission."));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(plugin.color("&cUsage: /watchdog inspect <player>"));
                    return true;
                }
                Player insTarget = Bukkit.getPlayer(args[1]);
                if (insTarget == null) {
                    sender.sendMessage(plugin.color("&cPlayer not found or offline."));
                    return true;
                }
                int totalVL = watchdogManager.getTotalVL(insTarget);
                int cps = watchdogManager.getCPS(insTarget);
                sender.sendMessage(plugin.color("&8&m────────────────────────────────────────"));
                sender.sendMessage(plugin.color("&8[&cWatchdog Inspect&8] &fPlayer: &e" + insTarget.getName()));
                sender.sendMessage(plugin.color("&7Total Violations (VL): &c" + totalVL));
                sender.sendMessage(plugin.color("&7Current CPS: &e" + cps));
                sender.sendMessage(plugin.color("&7Air Ticks: &f" + watchdogManager.getAirTicks(insTarget)));
                sender.sendMessage(plugin.color("&8&m────────────────────────────────────────"));
                break;

            case "ban":
                if (!sender.hasPermission("dtempire.watchdog.staff")) {
                    sender.sendMessage(plugin.color("&cNo permission."));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(plugin.color("&cUsage: /watchdog ban <player>"));
                    return true;
                }
                Player banTarget = Bukkit.getPlayer(args[1]);
                if (banTarget == null) {
                    sender.sendMessage(plugin.color("&cPlayer not found or offline."));
                    return true;
                }
                watchdogManager.punishBan(banTarget, "MANUAL_STAFF_BAN");
                sender.sendMessage(plugin.color("&aWatchdog ban executed on " + banTarget.getName()));
                break;

            case "stats":
                if (!sender.hasPermission("dtempire.watchdog.staff")) {
                    sender.sendMessage(plugin.color("&cNo permission."));
                    return true;
                }
                int count = plugin.getStore().getWatchdogBanCount();
                List<String> recent = plugin.getStore().getRecentWatchdogBans(5);
                sender.sendMessage(plugin.color("&8&m────────────────────────────────────────"));
                sender.sendMessage(plugin.color("&8[&cWatchdog Statistics&8]"));
                sender.sendMessage(plugin.color("&7Total Cheaters Banned: &c&l" + count));
                sender.sendMessage(plugin.color("&7Recent Bans:"));
                for (String b : recent) {
                    sender.sendMessage(plugin.color("&8- &7" + b));
                }
                sender.sendMessage(plugin.color("&8&m────────────────────────────────────────"));
                break;

            default:
                sender.sendMessage(plugin.color("&cUnknown subcommand. Type /watchdog for help."));
                break;
        }
        return true;
    }
}
