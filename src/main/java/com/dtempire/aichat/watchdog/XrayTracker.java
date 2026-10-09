package com.dtempire.aichat.watchdog;

import com.dtempire.aichat.DTEmpireAIChatPlugin;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Heuristic Anti-Xray detector for Watchdog.
 * Tracks ore discovery velocity, distinct vein clustering, and ore-to-stone mining ratios.
 */
public class XrayTracker implements Listener {

    private final DTEmpireAIChatPlugin plugin;
    private final WatchdogManager watchdogManager;

    private static class VeinLocation {
        final double x, y, z;
        final long time;
        final Material type;

        VeinLocation(Location loc, Material type, long time) {
            this.x = loc.getX();
            this.y = loc.getY();
            this.z = loc.getZ();
            this.time = time;
            this.type = type;
        }

        double distanceSq(Location loc) {
            double dx = this.x - loc.getX();
            double dy = this.y - loc.getY();
            double dz = this.z - loc.getZ();
            return dx * dx + dy * dy + dz * dz;
        }
    }

    private static class MiningStats {
        int stoneBlocks = 0;
        int rareOres = 0;
        final List<VeinLocation> distinctVeins = new ArrayList<>();
        long lastAlertTime = 0L;
        long sessionStart = System.currentTimeMillis();

        void resetWindow() {
            stoneBlocks = 0;
            rareOres = 0;
            distinctVeins.clear();
            sessionStart = System.currentTimeMillis();
        }
    }

    private final Map<UUID, MiningStats> playerStats = new ConcurrentHashMap<>();

    private final Set<Material> RARE_ORES = EnumSet.of(
            Material.DIAMOND_ORE,
            Material.DEEPSLATE_DIAMOND_ORE,
            Material.ANCIENT_DEBRIS,
            Material.EMERALD_ORE,
            Material.DEEPSLATE_EMERALD_ORE
    );

    private final Set<Material> STONE_TYPES = EnumSet.of(
            Material.STONE,
            Material.DEEPSLATE,
            Material.TUFF,
            Material.GRANITE,
            Material.DIORITE,
            Material.ANDESITE,
            Material.CALCITE,
            Material.NETHERRACK,
            Material.BLACKSTONE,
            Material.BASALT
    );

    public XrayTracker(DTEmpireAIChatPlugin plugin, WatchdogManager watchdogManager) {
        this.plugin = plugin;
        this.watchdogManager = watchdogManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() != GameMode.SURVIVAL) return;

        Material mat = event.getBlock().getType();
        boolean isRare = RARE_ORES.contains(mat);
        boolean isStone = STONE_TYPES.contains(mat);

        if (!isRare && !isStone) return;

        MiningStats stats = playerStats.computeIfAbsent(player.getUniqueId(), k -> new MiningStats());
        long now = System.currentTimeMillis();

        // 5-minute rolling window reset
        if (now - stats.sessionStart > 300000L) {
            stats.resetWindow();
        }

        if (isStone) {
            stats.stoneBlocks++;
            return;
        }

        // Rare ore mined
        stats.rareOres++;
        Location loc = event.getBlock().getLocation();

        // Check if this ore is part of an already discovered vein (within 3.5 blocks distance)
        boolean isNewVein = true;
        for (VeinLocation prev : stats.distinctVeins) {
            if (prev.type == mat && prev.distanceSq(loc) <= 12.25) { // 3.5^2
                isNewVein = false;
                break;
            }
        }

        if (isNewVein) {
            stats.distinctVeins.add(new VeinLocation(loc, mat, now));
        }

        // Clean veins older than 3 minutes
        stats.distinctVeins.removeIf(v -> (now - v.time) > 180000L);

        // Evaluation Heuristics:
        // 1. Vein velocity: 3+ distinct diamond/debris veins in under 3 minutes
        int recentVeins = stats.distinctVeins.size();
        int totalMined = stats.stoneBlocks + stats.rareOres;
        double oreRatio = totalMined > 0 ? ((double) stats.rareOres / totalMined) * 100.0 : 0.0;

        boolean suspiciousVelocity = recentVeins >= 3;
        // 2. Suspicious ore ratio: 5+ rare ores with > 12% ratio (vanilla diamond mining is < 1.5%)
        boolean suspiciousRatio = stats.rareOres >= 5 && oreRatio > 12.0 && totalMined >= 15;

        if ((suspiciousVelocity || suspiciousRatio) && (now - stats.lastAlertTime > 25000L)) {
            stats.lastAlertTime = now;

            String details = String.format("%d veins / %d ores in 3m (%.1f%% ratio) at X:%d Y:%d Z:%d",
                    recentVeins, stats.rareOres, oreRatio, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());

            watchdogManager.flag(player, "XRAY", 5, details);
        }
    }

    /**
     * Returns mining statistics formatted for staff/console display.
     */
    public String getStatsSummary(UUID uuid) {
        MiningStats stats = playerStats.get(uuid);
        if (stats == null) return "No mining activity in current session.";
        int recentVeins = stats.distinctVeins.size();
        int totalMined = stats.stoneBlocks + stats.rareOres;
        double oreRatio = totalMined > 0 ? ((double) stats.rareOres / totalMined) * 100.0 : 0.0;
        return String.format("&e%d &7veins (3m) | &b%d &7rare ores | &7%d stone | &c%.1f%% &7ratio",
                recentVeins, stats.rareOres, stats.stoneBlocks, oreRatio);
    }
}
