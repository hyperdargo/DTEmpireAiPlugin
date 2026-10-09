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
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * Movement and combat event listener for Watchdog anti-cheat checks.
 * Specifically tuned to eliminate false positives from cave jumping, low ceilings, and damage knockback.
 */
public class WatchdogListener implements Listener {

    private final DTEmpireAIChatPlugin plugin;
    private final WatchdogManager watchdogManager;

    public WatchdogListener(DTEmpireAIChatPlugin plugin, WatchdogManager watchdogManager) {
        this.plugin = plugin;
        this.watchdogManager = watchdogManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            watchdogManager.recordDamage(player);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCombat(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        Entity victim = event.getEntity();

        // 1. KillAura Orbiting Bot trap check
        // Invisible decoy armor stand rotating at high speed around player.
        // Legit players cannot see or hit this; only cheats lock on.
        if (WatchdogBot.isWatchdogBot(victim)) {
            event.setCancelled(true);
            watchdogManager.flag(attacker, "KILLAURA_BOT", 10, "Attacked orbiting Watchdog bot decoy");
            return;
        }

        // Only enforce anti-cheat on Survival/Adventure mode players
        if (attacker.getGameMode() != GameMode.SURVIVAL && attacker.getGameMode() != GameMode.ADVENTURE) {
            return;
        }

        // Record click for CPS
        watchdogManager.recordClick(attacker);

        // 2. Reach check (lenient to account for ping & entity hitboxes)
        Location eyeLoc = attacker.getEyeLocation();
        Location victimLoc = victim.getLocation();
        double distance = eyeLoc.distance(victimLoc);

        if (distance > 4.35) {
            watchdogManager.flag(attacker, "REACH", 1, String.format("%.2f blocks", distance));
        }

        // 3. Angle / Field of View check
        Vector direction = eyeLoc.getDirection().normalize();
        Vector toVictim = victimLoc.toVector().subtract(eyeLoc.toVector()).normalize();
        double angleDeg = Math.toDegrees(direction.angle(toVictim));

        if (angleDeg > 115.0) {
            watchdogManager.flag(attacker, "ANGLE_KILLAURA", 1, String.format("%.1f deg", angleDeg));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK) {
            watchdogManager.recordClick(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.ADVENTURE) {
            return;
        }

        // Gliding with Elytra, vehicle, riptide, or climbing are legitimate vanilla aerial/vertical actions
        if (player.isGliding() || player.isInsideVehicle() || player.isRiptiding() || player.isClimbing()) {
            watchdogManager.setAirTicks(player, 0);
            return;
        }

        // Flight ability check:
        // In survival mode, flight is disallowed unless explicitly enabled by server config
        boolean allowSurvivalFly = plugin.getConfig().getBoolean("watchdog.allow-survival-flight", false);
        if (player.isFlying() || player.getAllowFlight()) {
            if (!allowSurvivalFly && player.getGameMode() == GameMode.SURVIVAL) {
                // Illegal flight enabled in survival (/fly, abilities packet, or cheat client flight mode)
                watchdogManager.flag(player, "FLY", 2, "Survival flight enabled (isFlying=" + player.isFlying() + ", allowFlight=" + player.getAllowFlight() + ")");
                event.setTo(event.getFrom());
                return;
            } else {
                watchdogManager.setAirTicks(player, 0);
                return;
            }
        }

        if (player.hasPotionEffect(PotionEffectType.LEVITATION) || player.hasPotionEffect(PotionEffectType.SLOW_FALLING)) {
            watchdogManager.setAirTicks(player, 0);
            return;
        }

        // Knockback immunity: if player took damage in the last 2 seconds, ignore movement flags
        if (watchdogManager.hasRecentDamage(player)) {
            return;
        }

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);

        // Check ground and surroundings
        boolean onGround = player.isOnGround() || isSolidBlockUnderneath(to);
        boolean inLiquid = player.isInWater() || to.getBlock().isLiquid();
        boolean isClimbable = isClimbableBlock(to.getBlock());

        // Low-ceiling / cave detection: check if there's solid rock 2 to 3 blocks above player's feet
        boolean hasLowCeiling = hasCeilingAbove(to);

        if (onGround || inLiquid || isClimbable || hasLowCeiling) {
            watchdogManager.setAirTicks(player, 0);
            watchdogManager.setLastGroundLocation(player, to.clone());
        } else {
            // Suspended in air with open sky/room
            int air = watchdogManager.getAirTicks(player) + 1;
            watchdogManager.setAirTicks(player, air);

            // Bouncy blocks check (slime, honey, bed)
            Material groundMat = from.clone().subtract(0, 0.5, 0).getBlock().getType();
            boolean isBouncy = groundMat == Material.SLIME_BLOCK || groundMat == Material.HONEY_BLOCK || groundMat.name().endsWith("_BED");
            boolean inSlowBlock = to.getBlock().getType() == Material.COBWEB || to.getBlock().getType() == Material.POWDER_SNOW;

            if (!hasLowCeiling && !isBouncy && !inSlowBlock) {
                // Check 1: Ascending into the air without ground (air > 12 and dy > 0.08)
                // In vanilla, peak of jump is ~tick 11. Ascending after tick 12 is impossible without cheats.
                if (air > 12 && dy > 0.08) {
                    watchdogManager.flag(player, "FLY", 2, "Ascending in air without ground (air=" + air + ", dy=" + String.format("%.3f", dy) + ")");
                    event.setTo(from);
                    return;
                }

                // Check 2: Hovering or gliding horizontally through air without falling
                // In vanilla, after 20 air ticks, gravity forces dy downwards (-0.2 to -0.6 blocks/tick).
                if (air > 20 && dy >= -0.05 && player.getFallDistance() == 0.0f) {
                    watchdogManager.flag(player, "FLY", 1, "Suspended in air without falling (air=" + air + ", dy=" + String.format("%.3f", dy) + ")");
                    Location safeLoc = watchdogManager.getLastGroundLocation(player);
                    event.setTo(safeLoc != null ? safeLoc : from);
                    return;
                }
            }
        }

        // Speed Check:
        // Vanilla sprint-jumping reaches ~0.8 blocks/tick.
        // Ceiling sprint-jumping in caves bounces player repeatedly, reaching ~0.95-1.05 blocks/tick.
        // Real speedhacks go 1.3 - 3.0+ blocks/tick.
        double speedLimit = hasLowCeiling ? 1.35 : 1.15;

        // Account for Speed potion effects (+0.35 per amplifier level)
        PotionEffect speedEffect = player.getPotionEffect(PotionEffectType.SPEED);
        if (speedEffect != null) {
            speedLimit += (speedEffect.getAmplifier() + 1) * 0.35;
        }

        // Ice and packed ice boost momentum naturally
        Material belowMat = to.clone().subtract(0, 0.5, 0).getBlock().getType();
        boolean onIce = belowMat == Material.ICE || belowMat == Material.PACKED_ICE || belowMat == Material.BLUE_ICE;

        if (!onIce && horizontalDist > speedLimit) {
            watchdogManager.flag(player, "SPEED", 1, String.format("%.2f blocks/tick (limit: %.2f)", horizontalDist, speedLimit));
            // Rubberband setback to prevent speedhacking
            event.setTo(from);
            return;
        }

        // Jesus / Water Walk Check
        if (inLiquid && !player.isSwimming()) {
            if (Math.abs(dy) < 0.001 && horizontalDist > 0.45) {
                watchdogManager.flag(player, "JESUS", 1, "Water-walking: " + String.format("%.2f", horizontalDist));
                event.setTo(from);
            }
        }
    }

    /** Checks if there is a ceiling (blocks overhead within 1.8 to 3.2 blocks). */
    private boolean hasCeilingAbove(Location loc) {
        for (double yOffset = 1.8; yOffset <= 3.2; yOffset += 0.5) {
            if (loc.clone().add(0, yOffset, 0).getBlock().getType().isSolid()) {
                return true;
            }
        }
        return false;
    }

    /** Checks if any solid block exists within a small box below the player's feet. */
    private boolean isSolidBlockUnderneath(Location loc) {
        for (double xOff = -0.3; xOff <= 0.3; xOff += 0.3) {
            for (double zOff = -0.3; zOff <= 0.3; zOff += 0.3) {
                Block b = loc.clone().add(xOff, -0.4, zOff).getBlock();
                if (b.getType().isSolid()) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isClimbableBlock(Block block) {
        Material type = block.getType();
        return type == Material.LADDER || type == Material.VINE ||
                type == Material.SCAFFOLDING || type == Material.WEEPING_VINES ||
                type == Material.TWISTING_VINES;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLogin(AsyncPlayerPreLoginEvent event) {
        com.dtempire.aichat.SqliteStore.WatchdogBanRecord ban = plugin.getStore().getWatchdogBanInfo(event.getUniqueId());
        if (ban != null) {
            String discordUrl = plugin.getConfig().getString("watchdog.discord-appeal-url", "http://dsc.gg/dtempire-server");
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
