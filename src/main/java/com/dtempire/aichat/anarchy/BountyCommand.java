package com.dtempire.aichat.anarchy;

import com.dtempire.aichat.DTEmpireAIChatPlugin;
import com.dtempire.aichat.SqliteStore;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Command handler for /bounty [place|list|check|top].
 */
public class BountyCommand implements CommandExecutor {

    private final DTEmpireAIChatPlugin plugin;
    private final BountyManager bountyManager;

    public BountyCommand(DTEmpireAIChatPlugin plugin, BountyManager bountyManager) {
        this.plugin = plugin;
        this.bountyManager = bountyManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "place":
            case "set":
            case "add":
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(plugin.color("&cOnly players can place bounties using in-game diamonds."));
                    return true;
                }
                if (args.length < 3) {
                    player.sendMessage(plugin.color("&cUsage: /bounty place <player> <diamonds>"));
                    return true;
                }
                String targetName = args[1];
                int amount;
                try {
                    amount = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    player.sendMessage(plugin.color("&cPlease enter a valid number of diamonds."));
                    return true;
                }

                @SuppressWarnings("deprecation")
                OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
                if (!target.hasPlayedBefore() && !target.isOnline()) {
                    player.sendMessage(plugin.color("&cPlayer &e" + targetName + " &chas never played on this server."));
                    return true;
                }

                bountyManager.placePlayerBounty(player, target, amount);
                break;

            case "list":
            case "top":
                List<SqliteStore.BountyRecord> top = bountyManager.getTopBounties(10);
                sender.sendMessage(plugin.color("&8&m────────────────────────────────────────"));
                sender.sendMessage(plugin.color("&6&l[WANTED OUTLAWS] &eActive Diamond Bounties:"));
                if (top.isEmpty()) {
                    sender.sendMessage(plugin.color("&7No active bounties currently placed in the realm."));
                    sender.sendMessage(plugin.color("&8Place one using &e/bounty place <player> <diamonds>&8!"));
                } else {
                    int rank = 1;
                    for (SqliteStore.BountyRecord b : top) {
                        sender.sendMessage(plugin.color("&e#" + rank + " &c" + b.playerName + " &7- &b&l" + b.diamonds + " Diamonds &8(by " + b.placedBy + ")"));
                        rank++;
                    }
                }
                sender.sendMessage(plugin.color("&8&m────────────────────────────────────────"));
                break;

            case "check":
                if (args.length < 2) {
                    if (sender instanceof Player p) {
                        SqliteStore.BountyRecord own = bountyManager.getBounty(p.getUniqueId());
                        if (own != null) {
                            sender.sendMessage(plugin.color("&8[&6Bounty&8] &7Your bounty is &b" + own.diamonds + " Diamonds &7(placed by " + own.placedBy + ")."));
                        } else {
                            sender.sendMessage(plugin.color("&8[&6Bounty&8] &aYou do not have any active bounty on your head."));
                        }
                    } else {
                        sender.sendMessage(plugin.color("&cUsage: /bounty check <player>"));
                    }
                    return true;
                }
                @SuppressWarnings("deprecation")
                OfflinePlayer chkTarget = Bukkit.getOfflinePlayer(args[1]);
                SqliteStore.BountyRecord rec = bountyManager.getBounty(chkTarget.getUniqueId());
                if (rec != null) {
                    sender.sendMessage(plugin.color("&8[&6Bounty&8] &c" + rec.playerName + " &7has a bounty of &b" + rec.diamonds + " Diamonds &7(placed by " + rec.placedBy + ")."));
                } else {
                    sender.sendMessage(plugin.color("&8[&6Bounty&8] &7No bounty found for &e" + args[1] + "&7."));
                }
                break;

            default:
                sendHelp(sender);
                break;
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(plugin.color("&8&m────────────────────────────────────────"));
        sender.sendMessage(plugin.color("&6&l[Anarchy Bounty System]"));
        sender.sendMessage(plugin.color("&e/bounty place <player> <diamonds>&7 - Place diamond bounty on player"));
        sender.sendMessage(plugin.color("&e/bounty list&7 - View top wanted players and bounties"));
        sender.sendMessage(plugin.color("&e/bounty check [player]&7 - View current bounty on a player"));
        sender.sendMessage(plugin.color("&8&m────────────────────────────────────────"));
    }
}
