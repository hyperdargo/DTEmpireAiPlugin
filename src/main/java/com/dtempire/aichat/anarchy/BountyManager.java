package com.dtempire.aichat.anarchy;

import com.dtempire.aichat.DTEmpireAIChatPlugin;
import com.dtempire.aichat.SqliteStore;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

/**
 * Anarchy / SMP Bounty System.
 * Players and the AI Game Master place Diamond bounties on wanted outlaws.
 */
public class BountyManager {

    private final DTEmpireAIChatPlugin plugin;
    private final SqliteStore store;

    public BountyManager(DTEmpireAIChatPlugin plugin, SqliteStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    /** Places a player-funded bounty using Diamonds in inventory. */
    public boolean placePlayerBounty(Player setter, OfflinePlayer target, int diamonds) {
        if (diamonds <= 0) {
            setter.sendMessage(plugin.color("&cThe bounty amount must be at least 1 Diamond!"));
            return false;
        }

        if (setter.getUniqueId().equals(target.getUniqueId())) {
            setter.sendMessage(plugin.color("&cYou cannot place a bounty on yourself!"));
            return false;
        }

        // Count diamonds in inventory
        int totalDiamonds = 0;
        for (ItemStack item : setter.getInventory().getContents()) {
            if (item != null && item.getType() == Material.DIAMOND) {
                totalDiamonds += item.getAmount();
            }
        }

        if (totalDiamonds < diamonds) {
            setter.sendMessage(plugin.color("&cYou do not have enough diamonds! Needed: &b" + diamonds + " &c| You have: &b" + totalDiamonds));
            return false;
        }

        // Deduct diamonds
        int remainingToDeduct = diamonds;
        ItemStack[] contents = setter.getInventory().getContents();
        for (int i = 0; i < contents.length && remainingToDeduct > 0; i++) {
            ItemStack item = contents[i];
            if (item != null && item.getType() == Material.DIAMOND) {
                int take = Math.min(item.getAmount(), remainingToDeduct);
                item.setAmount(item.getAmount() - take);
                remainingToDeduct -= take;
                if (item.getAmount() <= 0) {
                    contents[i] = null;
                }
            }
        }
        setter.getInventory().setContents(contents);
        setter.updateInventory();

        String targetName = target.getName() != null ? target.getName() : "Unknown";
        int newTotal = store.addOrIncreaseBounty(target.getUniqueId(), targetName, diamonds, setter.getName());

        // Global announcement
        Bukkit.broadcastMessage(plugin.color("&8&m────────────────────────────────────────"));
        Bukkit.broadcastMessage(plugin.color("&6&l[BOUNTY ISSUED] &e" + setter.getName() + " &7placed a &b" + diamonds + " Diamond &7bounty on &c" + targetName + "&7!"));
        Bukkit.broadcastMessage(plugin.color("&7Total Bounty on &c" + targetName + "&7: &b&l" + newTotal + " Diamonds&7!"));
        Bukkit.broadcastMessage(plugin.color("&7Hunt them down in the wilderness to claim the riches!"));
        Bukkit.broadcastMessage(plugin.color("&8&m────────────────────────────────────────"));

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, 0.7f, 1.2f);
        }

        return true;
    }

    /** AI Game Master places an autonomous server bounty on an outlaw. */
    public void placeAutonomousAIBounty(Player target, int diamonds, String reason) {
        int newTotal = store.addOrIncreaseBounty(target.getUniqueId(), target.getName(), diamonds, "Hermes AI");

        Bukkit.broadcastMessage(plugin.color("&8&m────────────────────────────────────────"));
        Bukkit.broadcastMessage(plugin.color("&4&l[AI AUTONOMOUS BOUNTY] &eHermes Game Master has marked &c" + target.getName() + "&e!"));
        Bukkit.broadcastMessage(plugin.color("&7Reason: &f" + reason));
        Bukkit.broadcastMessage(plugin.color("&7Added: &b+" + diamonds + " Diamonds &8| &7Total Pool: &b&l" + newTotal + " Diamonds"));
        Bukkit.broadcastMessage(plugin.color("&eEliminate this menace to claim the bounty!"));
        Bukkit.broadcastMessage(plugin.color("&8&m────────────────────────────────────────"));

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.5f, 1.5f);
        }
    }

    /** Claims any active bounty on the victim for the killer. */
    public void claimBounty(Player killer, UUID victimUuid, String victimName) {
        SqliteStore.BountyRecord bounty = store.getBounty(victimUuid);
        if (bounty == null || bounty.diamonds <= 0) {
            return;
        }

        int reward = store.removeBounty(victimUuid);
        if (reward <= 0) return;

        // Give diamonds to killer
        ItemStack rewardItem = new ItemStack(Material.DIAMOND, reward);
        HashMap<Integer, ItemStack> overflow = killer.getInventory().addItem(rewardItem);
        if (!overflow.isEmpty()) {
            for (ItemStack left : overflow.values()) {
                killer.getWorld().dropItemNaturally(killer.getLocation(), left);
            }
        }

        // Broadcast claim
        Bukkit.broadcastMessage(plugin.color("&8&m────────────────────────────────────────"));
        Bukkit.broadcastMessage(plugin.color("&a&l[BOUNTY CLAIMED!] &e" + killer.getName() + " &7has eliminated &c" + victimName + "&7!"));
        Bukkit.broadcastMessage(plugin.color("&7Reward Claimed: &b&l" + reward + " Diamonds&7!"));
        Bukkit.broadcastMessage(plugin.color("&8&m────────────────────────────────────────"));

        killer.playSound(killer.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        killer.sendTitle(plugin.color("&6&lBOUNTY CLAIMED!"), plugin.color("&b+" + reward + " Diamonds"), 10, 80, 20);
    }

    public SqliteStore.BountyRecord getBounty(UUID uuid) {
        return store.getBounty(uuid);
    }

    public List<SqliteStore.BountyRecord> getTopBounties(int limit) {
        return store.getTopBounties(limit);
    }
}
