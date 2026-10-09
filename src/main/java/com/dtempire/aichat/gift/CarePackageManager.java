package com.dtempire.aichat.gift;

import com.dtempire.aichat.DTEmpireAIChatPlugin;
import com.dtempire.aichat.SqliteStore;
import com.dtempire.aichat.telemetry.PlayerTelemetry;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/** Delivers adaptive sympathy gifts and care packages to struggling or milestone players. */
public class CarePackageManager {

    private final DTEmpireAIChatPlugin plugin;
    private final SqliteStore store;
    private static final long COOLDOWN_MS = 30 * 60 * 1000L; // 30 minutes cooldown

    public CarePackageManager(DTEmpireAIChatPlugin plugin, SqliteStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    /** Called on player death to assess if a sympathy package should be granted on respawn. */
    public void checkSympathyPackage(Player player, PlayerTelemetry telemetry, EntityDamageEvent.DamageCause cause) {
        boolean lavaDeath = (cause == EntityDamageEvent.DamageCause.LAVA || cause == EntityDamageEvent.DamageCause.FIRE_TICK);
        boolean consecutiveStruggle = telemetry.getConsecutiveDeaths() >= 3;

        if (!lavaDeath && !consecutiveStruggle) {
            return;
        }

        long lastGiven = store.getLastCarePackageTime(player.getUniqueId());
        long now = System.currentTimeMillis();
        if (now - lastGiven < COOLDOWN_MS) {
            return; // on cooldown
        }

        // Deliver package shortly after player respawns (wait 3 seconds)
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline() && !player.isDead()) {
                    deliverSympathyPackage(player, lavaDeath);
                }
            }
        }.runTaskLater(plugin, 60L);
    }

    public void deliverSympathyPackage(Player player, boolean lavaDeath) {
        store.recordCarePackageGiven(player.getUniqueId());

        // Golden Apple
        ItemStack gapple = new ItemStack(Material.GOLDEN_APPLE, 2);
        ItemMeta gMeta = gapple.getItemMeta();
        if (gMeta != null) {
            gMeta.setDisplayName(plugin.color("&6Hermes Sympathy Apple"));
            gMeta.setLore(Collections.singletonList(plugin.color("&7A blessing from the AI Game Master.")));
            gapple.setItemMeta(gMeta);
        }

        // Fire Res Potion if died to lava, otherwise Healing
        ItemStack potion = new ItemStack(Material.POTION, 1);
        PotionMeta pMeta = (PotionMeta) potion.getItemMeta();
        if (pMeta != null) {
            if (lavaDeath) {
                pMeta.addCustomEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 20 * 240, 0), true);
                pMeta.setDisplayName(plugin.color("&eElixir of Fire Warding"));
            } else {
                pMeta.addCustomEffect(new PotionEffect(PotionEffectType.REGENERATION, 20 * 45, 1), true);
                pMeta.setDisplayName(plugin.color("&dElixir of Renewal"));
            }
            potion.setItemMeta(pMeta);
        }

        // Emergency supplies
        ItemStack food = new ItemStack(Material.COOKED_BEEF, 16);
        ItemStack torches = new ItemStack(Material.TORCH, 32);
        ItemStack pick = new ItemStack(Material.IRON_PICKAXE, 1);

        giveOrDrop(player, gapple, potion, food, torches, pick);

        // Visual & Audio effects
        player.sendTitle(
                plugin.color("&6&lCARE PACKAGE"),
                plugin.color("&eHermes delivered emergency survival supplies!"),
                10, 70, 20
        );
        player.sendMessage(plugin.color("&8[&bAI Care&8] &fStay strong, &e" + player.getName() +
                "&f! The realm can be unforgiving, but Hermes has your back."));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
        player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.1);
    }

    public void deliverCustomGift(Player player, Material material, int amount, String name, String lore) {
        ItemStack item = new ItemStack(material, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (name != null) meta.setDisplayName(plugin.color(name));
            if (lore != null) meta.setLore(Collections.singletonList(plugin.color(lore)));
            item.setItemMeta(meta);
        }
        giveOrDrop(player, item);
        player.sendMessage(plugin.color("&8[&bAI Gift&8] &fYou received a special gift from Hermes: &e" + (name != null ? name : material.name()) + " &7x" + amount));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
    }

    private void giveOrDrop(Player player, ItemStack... items) {
        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(items);
        for (ItemStack drop : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), drop);
        }
    }
}
