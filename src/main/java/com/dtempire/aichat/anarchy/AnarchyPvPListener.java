package com.dtempire.aichat.anarchy;

import com.dtempire.aichat.DTEmpireAIChatPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles Combat Tagging, Combat Logging elimination, Player Head decapitations,
 * Killstreak tracking & buffs, and AI Death Roasts for SMP/Anarchy gameplay.
 */
public class AnarchyPvPListener implements Listener {

    private final DTEmpireAIChatPlugin plugin;
    private final CombatTagManager combatTagManager;
    private final BountyManager bountyManager;

    private final Map<UUID, Integer> killstreaks = new ConcurrentHashMap<>();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
    private final Random random = new Random();
    private long lastRoastTime = 0L;

    private final Set<String> blockedCombatCommands = new HashSet<>(Arrays.asList(
            "/spawn", "/tp", "/tpa", "/tpaccept", "/tpahere", "/home", "/sethome",
            "/warp", "/back", "/aichat", "/aidaily", "/hub", "/lobby", "/suicide"
    ));

    public AnarchyPvPListener(DTEmpireAIChatPlugin plugin, CombatTagManager combatTagManager, BountyManager bountyManager) {
        this.plugin = plugin;
        this.combatTagManager = combatTagManager;
        this.bountyManager = bountyManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPvPDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        Player attacker = null;
        if (event.getDamager() instanceof Player p) {
            attacker = p;
        } else if (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player shooter) {
            attacker = shooter;
        }

        if (attacker != null && !attacker.equals(victim)) {
            combatTagManager.tag(victim, attacker);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (player.isOp()) return;

        if (combatTagManager.isTagged(player)) {
            String msg = event.getMessage().toLowerCase();
            String root = msg.split(" ")[0];

            if (blockedCombatCommands.contains(root)) {
                event.setCancelled(true);
                int remaining = combatTagManager.getRemainingSeconds(player);
                player.sendMessage(plugin.color("&c⚔ You cannot escape combat! &7(" + remaining + "s combat tag remaining)"));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (combatTagManager.isTagged(player)) {
            UUID attackerId = combatTagManager.getLastAttacker(player.getUniqueId());
            combatTagManager.clear(player.getUniqueId());

            // Kill player for combat-logging
            player.setHealth(0.0);

            Bukkit.broadcastMessage(plugin.color("&8&m────────────────────────────────────────"));
            Bukkit.broadcastMessage(plugin.color("&c&l[COMBAT LOG] &e" + player.getName() + " &7disconnected while in combat and was eliminated!"));
            Bukkit.broadcastMessage(plugin.color("&8&m────────────────────────────────────────"));

            if (attackerId != null) {
                Player attacker = Bukkit.getPlayer(attackerId);
                if (attacker != null && attacker.isOnline()) {
                    attacker.sendMessage(plugin.color("&8[&bCombat Tag&8] &aYour opponent &e" + player.getName() + " &acombat-logged and was punished."));
                    bountyManager.claimBounty(attacker, player.getUniqueId(), player.getName());
                    dropPlayerHead(player, attacker);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        combatTagManager.clear(victim.getUniqueId());

        Player killer = victim.getKiller();
        if (killer != null && !killer.equals(victim)) {
            // 1. Decapitate player head trophy
            dropPlayerHead(victim, killer);

            // 2. Claim Diamond bounty if any
            bountyManager.claimBounty(killer, victim.getUniqueId(), victim.getName());

            // 3. Killstreak tracking
            int prevVictimStreak = killstreaks.remove(victim.getUniqueId()) != null ? killstreaks.getOrDefault(victim.getUniqueId(), 0) : 0;
            if (prevVictimStreak >= 3) {
                Bukkit.broadcastMessage(plugin.color("&e" + killer.getName() + " &7ended &c" + victim.getName() + "'s &7killstreak of &c" + prevVictimStreak + " kills&7!"));
            }

            int streak = killstreaks.merge(killer.getUniqueId(), 1, Integer::sum);
            if (streak == 3) {
                Bukkit.broadcastMessage(plugin.color("&6&l[KILLSTREAK] &e" + killer.getName() + " &7is on a &6Killing Spree &7(3 kills)!"));
            } else if (streak == 5) {
                Bukkit.broadcastMessage(plugin.color("&c&l[KILLSTREAK] &e" + killer.getName() + " &7is on a &cRampage &7(5 kills)!"));
                killer.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 30, 0));
                // Autonomous AI Bounty trigger!
                bountyManager.placeAutonomousAIBounty(killer, 5, "5-kill rampage in the wilderness");
            } else if (streak == 10) {
                Bukkit.broadcastMessage(plugin.color("&4&l[KILLSTREAK] &e" + killer.getName() + " &7is &4&lUNSTOPPABLE &7(10 kills)!"));
                killer.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 20 * 45, 0));
                bountyManager.placeAutonomousAIBounty(killer, 10, "Unstoppable 10-kill terror");
            }
        } else {
            // Accidental/PvE death
            killstreaks.remove(victim.getUniqueId());
            checkAIDeathRoast(victim);
        }
    }

    /** Drops customized player head trophy with decapitation lore. */
    private void dropPlayerHead(Player victim, Player killer) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (head.getItemMeta() instanceof SkullMeta meta) {
            meta.setOwningPlayer(victim);
            meta.setDisplayName(plugin.color("&c" + victim.getName() + "'s Decapitated Head"));

            List<String> lore = new ArrayList<>();
            lore.add(plugin.color("&7Slain by: &e" + killer.getName()));
            lore.add(plugin.color("&7Date: &8" + dateFormat.format(new Date())));
            lore.add(plugin.color("&8A grim trophy of Anarchy conquest."));
            meta.setLore(lore);

            head.setItemMeta(meta);
        }
        victim.getWorld().dropItemNaturally(victim.getLocation(), head);
    }

    /** AI Game Master delivers hilarious/snarky death roasts on embarrassing deaths. */
    private void checkAIDeathRoast(Player victim) {
        long now = System.currentTimeMillis();
        if (now - lastRoastTime < 45000L) { // 45s cooldown so chat is never spammed
            return;
        }

        EntityDamageEvent lastDamage = victim.getLastDamageCause();
        if (lastDamage == null) return;

        EntityDamageEvent.DamageCause cause = lastDamage.getCause();
        String roast = null;

        switch (cause) {
            case LAVA:
            case FIRE:
            case FIRE_TICK:
                roast = victim.getName() + " took an unscheduled lava bath. Extra crispy.";
                break;
            case FALL:
                roast = victim.getName() + " tested if fall damage was enabled. Verified: it is.";
                break;
            case SUFFOCATION:
                roast = victim.getName() + " attempted to merge with a solid stone wall.";
                break;
            case DROWNING:
                roast = victim.getName() + " forgot that air is a survival requirement.";
                break;
            case VOID:
                roast = victim.getName() + " stared too deeply into the void. The void stared back.";
                break;
            case STARVATION:
                roast = victim.getName() + " succumbed to hunger. Rest in carrots.";
                break;
            case CONTACT:
                roast = victim.getName() + " lost a duel against a stationary cactus.";
                break;
            case ENTITY_ATTACK:
                roast = victim.getName() + " was humbled by a hostile mob. Better luck next respawn.";
                break;
            default:
                break;
        }

        if (roast != null) {
            lastRoastTime = now;
            String finalRoast = roast;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Bukkit.broadcastMessage(plugin.color("&8[&bHermes AI&8] &7" + finalRoast));
            }, 20L); // 1-second delay for dramatic comedic timing
        }
    }
}
