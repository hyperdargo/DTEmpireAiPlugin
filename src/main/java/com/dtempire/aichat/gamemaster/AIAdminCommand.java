package com.dtempire.aichat.gamemaster;

import com.dtempire.aichat.DTEmpireAIChatPlugin;
import com.dtempire.aichat.telemetry.PlayerTelemetry;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Command executor for /aiadmin. Admin operations for the AI Game Master. */
public class AIAdminCommand implements CommandExecutor {

    private final DTEmpireAIChatPlugin plugin;
    private final AIGameMaster gameMaster;

    public AIAdminCommand(DTEmpireAIChatPlugin plugin, AIGameMaster gameMaster) {
        this.plugin = plugin;
        this.gameMaster = gameMaster;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("dtempire.admin")) {
            sender.sendMessage(plugin.color("&cYou do not have permission to use AI Admin commands."));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(plugin.color("&8[&bAI Admin&8] &r&lGame Master Controls:"));
            sender.sendMessage(plugin.color("&e/aiadmin event <meteor|bloodmoon|goldenhour>&7 - Trigger server event"));
            sender.sendMessage(plugin.color("&e/aiadmin gift <player> <material> [amount]&7 - Send gift to player"));
            sender.sendMessage(plugin.color("&e/aiadmin broadcast <message>&7 - Broadcast lore announcement"));
            sender.sendMessage(plugin.color("&e/aiadmin stats <player>&7 - View player telemetry archetype"));
            sender.sendMessage(plugin.color("&e/aiadmin carepackage <player>&7 - Force deliver care package"));
            sender.sendMessage(plugin.color("&e/aiadmin reload&7 - Reload AI configs and scheduler"));
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "event":
                if (args.length < 2) {
                    sender.sendMessage(plugin.color("&cUsage: /aiadmin event <meteor|bloodmoon|goldenhour>"));
                    return true;
                }
                String ev = args[1].toLowerCase();
                if (ev.equals("meteor")) {
                    gameMaster.triggerMeteorSupplyDrop(sender instanceof Player p ? p.getLocation() : null);
                    sender.sendMessage(plugin.color("&aCelestial meteor supply drop triggered!"));
                } else if (ev.equals("bloodmoon")) {
                    gameMaster.triggerBloodMoon();
                    sender.sendMessage(plugin.color("&aBlood Moon event initiated!"));
                } else if (ev.equals("goldenhour")) {
                    gameMaster.triggerGoldenHour();
                    sender.sendMessage(plugin.color("&aGolden Hour event begun!"));
                } else {
                    sender.sendMessage(plugin.color("&cUnknown event. Choose meteor, bloodmoon, or goldenhour."));
                }
                break;

            case "gift":
                if (args.length < 3) {
                    sender.sendMessage(plugin.color("&cUsage: /aiadmin gift <player> <material> [amount]"));
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(plugin.color("&cPlayer not found or offline."));
                    return true;
                }
                Material mat = Material.matchMaterial(args[2].toUpperCase());
                if (mat == null) {
                    sender.sendMessage(plugin.color("&cInvalid material name: " + args[2]));
                    return true;
                }
                int count = 1;
                if (args.length >= 4) {
                    try { count = Integer.parseInt(args[3]); } catch (NumberFormatException ignored) {}
                }
                plugin.getCarePackageManager().deliverCustomGift(target, mat, count, "&6Admin Blessing", "&7Gifted by " + sender.getName());
                sender.sendMessage(plugin.color("&aDelivered gift to " + target.getName()));
                break;

            case "broadcast":
                if (args.length < 2) {
                    sender.sendMessage(plugin.color("&cUsage: /aiadmin broadcast <message>"));
                    return true;
                }
                StringBuilder sb = new StringBuilder();
                for (int i = 1; i < args.length; i++) sb.append(args[i]).append(" ");
                gameMaster.broadcastLore(sb.toString().trim());
                break;

            case "stats":
                if (args.length < 2) {
                    sender.sendMessage(plugin.color("&cUsage: /aiadmin stats <player>"));
                    return true;
                }
                Player pTarget = Bukkit.getPlayer(args[1]);
                if (pTarget == null) {
                    sender.sendMessage(plugin.color("&cPlayer not found or offline."));
                    return true;
                }
                PlayerTelemetry t = plugin.getTelemetryManager().getTelemetry(pTarget);
                sender.sendMessage(plugin.color("&8&m────────────────────────────────────────"));
                sender.sendMessage(plugin.color("&8[&bAI Telemetry&8] &fProfile for: &e" + pTarget.getName()));
                sender.sendMessage(plugin.color("&7Learned Archetype: &a&l" + t.getArchetype()));
                sender.sendMessage(plugin.color("&7Blocks Mined: &f" + t.getBlocksMined() + " &8(Ores: " + t.getOresMined() + ")"));
                sender.sendMessage(plugin.color("&7Blocks Placed: &f" + t.getBlocksPlaced()));
                sender.sendMessage(plugin.color("&7Mobs Killed: &f" + t.getMobsKilled() + " &8(PvP: " + t.getPvpKills() + ")"));
                sender.sendMessage(plugin.color("&7Deaths: &f" + t.getDeaths() + " &8(Streak: " + t.getConsecutiveDeaths() + ")"));
                sender.sendMessage(plugin.color("&7Distance Explored: &f" + (int) t.getDistanceTraveled() + " blocks"));
                sender.sendMessage(plugin.color("&8&m────────────────────────────────────────"));
                break;

            case "carepackage":
                if (args.length < 2) {
                    sender.sendMessage(plugin.color("&cUsage: /aiadmin carepackage <player>"));
                    return true;
                }
                Player cTarget = Bukkit.getPlayer(args[1]);
                if (cTarget == null) {
                    sender.sendMessage(plugin.color("&cPlayer not found or offline."));
                    return true;
                }
                plugin.getCarePackageManager().deliverSympathyPackage(cTarget, false);
                sender.sendMessage(plugin.color("&aForce delivered sympathy package to " + cTarget.getName()));
                break;

            case "reload":
                plugin.reloadConfig();
                gameMaster.startEventScheduler();
                sender.sendMessage(plugin.color("&aAI Game Master reloaded successfully."));
                break;

            default:
                sender.sendMessage(plugin.color("&cUnknown subcommand. Type /aiadmin for help."));
                break;
        }
        return true;
    }
}
