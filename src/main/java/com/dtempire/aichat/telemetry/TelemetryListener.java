package com.dtempire.aichat.telemetry;

import com.dtempire.aichat.DTEmpireAIChatPlugin;
import com.dtempire.aichat.daily.DailyTaskManager;
import com.dtempire.aichat.gift.CarePackageManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Listens to gameplay events to feed telemetry, daily tasks, and sympathy gifts. */
public class TelemetryListener implements Listener {

    private final DTEmpireAIChatPlugin plugin;
    private final TelemetryManager telemetryManager;
    private final DailyTaskManager dailyTaskManager;
    private final CarePackageManager carePackageManager;

    public TelemetryListener(DTEmpireAIChatPlugin plugin,
                             TelemetryManager telemetryManager,
                             DailyTaskManager dailyTaskManager,
                             CarePackageManager carePackageManager) {
        this.plugin = plugin;
        this.telemetryManager = telemetryManager;
        this.dailyTaskManager = dailyTaskManager;
        this.carePackageManager = carePackageManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        PlayerTelemetry telemetry = telemetryManager.getTelemetry(player);
        Material type = event.getBlock().getType();

        boolean isOre = type.name().endsWith("_ORE") || type == Material.ANCIENT_DEBRIS;
        if (isOre) {
            telemetry.addOresMined(1);
        } else {
            telemetry.addBlocksMined(1);
        }

        dailyTaskManager.onBlockBreak(player, type);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        PlayerTelemetry telemetry = telemetryManager.getTelemetry(player);
        telemetry.addBlocksPlaced(1);

        dailyTaskManager.onBlockPlace(player, event.getBlock().getType());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;

        PlayerTelemetry telemetry = telemetryManager.getTelemetry(killer);
        if (event.getEntity() instanceof Player) {
            telemetry.addPvpKills(1);
        } else if (event.getEntity() instanceof Monster) {
            telemetry.addMobsKilled(1);
        }

        dailyTaskManager.onEntityKill(killer, event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        PlayerTelemetry telemetry = telemetryManager.getTelemetry(player);
        telemetry.recordDeath();

        EntityDamageEvent lastDamage = player.getLastDamageCause();
        EntityDamageEvent.DamageCause cause = lastDamage != null ? lastDamage.getCause() : EntityDamageEvent.DamageCause.CUSTOM;
        carePackageManager.checkSympathyPackage(player, telemetry, cause);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double distSq = dx * dx + dz * dz;

        // Ignore micro-rotations or huge teleports
        if (distSq > 0.0025 && distSq < 100.0) {
            double dist = Math.sqrt(distSq);
            Player player = event.getPlayer();
            PlayerTelemetry telemetry = telemetryManager.getTelemetry(player);
            telemetry.addDistance(dist);
            dailyTaskManager.onMoveDistance(player, dist);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        telemetryManager.onJoin(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        telemetryManager.onQuit(event.getPlayer());
    }
}
