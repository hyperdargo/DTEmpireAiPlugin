package com.dtempire.aichat.watchdog;

import com.dtempire.aichat.DTEmpireAIChatPlugin;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/** Movement and combat event listener for Watchdog anti-cheat checks. */
public class WatchdogListener implements Listener {

    private final DTEmpireAIChatPlugin plugin;
    private final WatchdogManager watchdogManager;

    public WatchdogListener(DTEmpireAIChatPlugin plugin, WatchdogManager watchdogManager) {
        this.plugin = plugin;
        this.watchdogManager = watchdogManager;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCombat(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        Entity victim = event.getEntity();

        // 1. KillAura Orbiting Bot trap check
        if (WatchdogBot.isWatchdogBot(victim)) {
            event.setCancelled(true);
            watchdogManager.flag(attacker, "KILLAURA_BOT", 10, "Attacked orbiting Watchdog bot entity");
            return;
        }

        // Only enforce anti-cheat on Survival/Adventure mode players
        if (attacker.getGameMode() != GameMode.SURVIVAL && attacker.getGameMode() != GameMode.ADVENTURE) {
            return;
        }

        // Record click for CPS
        watchdogManager.recordClick(attacker);

        // 2. Reach check
        Location eyeLoc = attacker.getEyeLocation();
        Location victimLoc = victim.getLocation();
        double distance = eyeLoc.distance(victimLoc);

        if (distance > 3.9) {
            watchdogManager.flag(attacker, "REACH", 1, String.format("%.2f blocks", distance));
        }

        // 3. Angle / Field of View check
        Vector direction = eyeLoc.getDirection().normalize();
        Vector toVictim = victimLoc.toVector().subtract(eyeLoc.toVector()).normalize();
        double angleDeg = Math.toDegrees(direction.angle(toVictim));

        if (angleDeg > 95.0) {
            watchdogManager.flag(attacker, "ANGLE_KILLAURA", 2, String.format("%.1f deg", angleDeg));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK) {
            watchdogManager.recordClick(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.ADVENTURE) {
            return;
        }
        if (player.isFlying() || player.isGliding() || player.isInsideVehicle()) {
            watchdogManager.setAirTicks(player, 0);
            return;
        }
        if (player.hasPotionEffect(PotionEffectType.LEVITATION) || player.hasPotionEffect(PotionEffectType.SLOW_FALLING)) {
            watchdogManager.setAirTicks(player, 0);
            return;
        }

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);

        // Check ground/climbable blocks
        Block feetBlock = to.getBlock();
        Block groundBlock = to.clone().subtract(0, 0.5, 0).getBlock();

        boolean inLiquid = player.isInWater() || feetBlock.isLiquid() || groundBlock.isLiquid();
        boolean isClimbable = feetBlock.getType() == Material.LADDER || feetBlock.getType() == Material.VINE ||
                feetBlock.getType() == Material.SCAFFOLDING;

        // 4. Fly / Glide Check
        if (feetBlock.isEmpty() && groundBlock.isEmpty() && !inLiquid && !isClimbable) {
            int air = watchdogManager.getAirTicks(player) + 1;
            watchdogManager.setAirTicks(player, air);

            if (air > 40 && dy >= -0.05) {
                watchdogManager.flag(player, "FLY", 1, "Suspended in air for " + air + " ticks (dy: " + String.format("%.3f", dy) + ")");
            }
        } else {
            watchdogManager.setAirTicks(player, 0);
        }

        // 5. Speed / Bhop Check
        if (!player.hasPotionEffect(PotionEffectType.SPEED)) {
            Material groundMat = groundBlock.getType();
            boolean onIce = groundMat == Material.ICE || groundMat == Material.PACKED_ICE || groundMat == Material.BLUE_ICE;

            if (!onIce && horizontalDist > 0.72) {
                watchdogManager.flag(player, "SPEED", 1, String.format("%.2f blocks/tick", horizontalDist));
            }
        }

        // 6. Jesus / Water Walk Check
        if (inLiquid && !player.isSwimming()) {
            if (Math.abs(dy) < 0.001 && horizontalDist > 0.25) {
                // If moving horizontally across water surface with zero vertical drop
                watchdogManager.flag(player, "JESUS", 1, "Water-walking horizontal: " + String.format("%.2f", horizontalDist));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLogin(AsyncPlayerPreLoginEvent event) {
        com.dtempire.aichat.SqliteStore.WatchdogBanRecord ban = plugin.getStore().getWatchdogBanInfo(event.getUniqueId());
        if (ban != null) {
            String discordUrl = plugin.getConfig().getString("watchdog.discord-appeal-url", "https://discord.gg/dtempire");
            String banReason = ban.reason != null ? ban.reason : "Cheating / Unfair Advantage";
            String banId = ban.banId != null ? ban.banId : "#WD-PERM";

            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, plugin.color(
                    "&c&lYou are permanently banned from DTEmpire Network!\n\n" +
                    "&7Reason: &fWATCHDOG CHEAT DETECTION (&c" + banReason + "&7)\n" +
                    "&7Ban ID: &e" + banId + "\n" +
                    "&7Ban Status: &cPERMANENT\n\n" +
                    "&eIf you believe this detection was false, join our Discord to appeal:\n" +
                    "&b" + discordUrl + "\n\n" +
                    "&7Sharing accounts or unfair modifications violate DTEmpire policy."
            ));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        watchdogManager.onQuit(event.getPlayer());
    }
}
