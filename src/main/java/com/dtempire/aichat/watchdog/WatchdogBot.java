package com.dtempire.aichat.watchdog;

import com.dtempire.aichat.DTEmpireAIChatPlugin;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spawns an invisible orbiting armor stand around suspected players.
 * Legitimate human players never attack it. KillAura / TriggerBot hacks
 * automatically lock on and strike it, causing an instant ban.
 */
public class WatchdogBot {

    private static final Map<UUID, ArmorStand> ACTIVE_BOTS = new ConcurrentHashMap<>();

    public static boolean isWatchdogBot(org.bukkit.entity.Entity entity) {
        if (!(entity instanceof ArmorStand)) return false;
        return ACTIVE_BOTS.containsValue(entity);
    }

    public static void summonAuraTrap(DTEmpireAIChatPlugin plugin, Player suspect, WatchdogManager watchdogManager) {
        if (!suspect.isOnline()) return;
        UUID uuid = suspect.getUniqueId();

        // If bot already active on player, don't duplicate
        if (ACTIVE_BOTS.containsKey(uuid)) return;

        World world = suspect.getWorld();
        Location spawnLoc = suspect.getLocation().add(0, 2, 0);

        ArmorStand bot = (ArmorStand) world.spawnEntity(spawnLoc, EntityType.ARMOR_STAND);
        bot.setVisible(false);
        bot.setGravity(false);
        bot.setMarker(false); // Keeps interaction hitbox for killaura clients to hit
        bot.setInvulnerable(true);
        bot.setCustomName(plugin.color("&c&k||&r Watchdog &c&k||"));
        bot.setCustomNameVisible(false);

        ACTIVE_BOTS.put(uuid, bot);

        new BukkitRunnable() {
            private double angle = 0.0;
            private int ticks = 0;

            @Override
            public void run() {
                if (!suspect.isOnline() || bot.isDead() || ticks >= 100) { // 5 seconds duration
                    cleanup();
                    cancel();
                    return;
                }

                // Rapidly orbit around player at eye level (radius 2.3 blocks)
                angle += Math.PI / 4.0; // fast rotation
                double x = suspect.getLocation().getX() + Math.cos(angle) * 2.3;
                double z = suspect.getLocation().getZ() + Math.sin(angle) * 2.3;
                double y = suspect.getEyeLocation().getY() + 0.3 + Math.sin(angle * 2) * 0.4;

                Location targetLoc = new Location(suspect.getWorld(), x, y, z);
                bot.teleport(targetLoc);
                ticks++;
            }

            private void cleanup() {
                ACTIVE_BOTS.remove(uuid);
                if (bot.isValid()) {
                    bot.remove();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }
}
