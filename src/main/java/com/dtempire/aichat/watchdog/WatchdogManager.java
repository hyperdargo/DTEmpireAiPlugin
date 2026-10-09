package com.dtempire.aichat.watchdog;

import com.dtempire.aichat.DTEmpireAIChatPlugin;
import com.dtempire.aichat.SqliteStore;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Central Watchdog Anti-Cheat Manager. Manages violation levels, staff alerts, and ban executions. */
public class WatchdogManager {

    private final DTEmpireAIChatPlugin plugin;
    private final SqliteStore store;
    private final Random random = new Random();

    // Player UUID -> Check Name -> Violation Level
    private final Map<UUID, Map<String, Integer>> violations = new ConcurrentHashMap<>();
    // Player UUID -> click timestamps for CPS counter
    private final Map<UUID, Deque<Long>> clickHistory = new ConcurrentHashMap<>();
    // Consecutive air ticks for fly checks
    private final Map<UUID, Integer> airTicks = new ConcurrentHashMap<>();

    private static final int MAX_BAN_VL = 10;

    public WatchdogManager(DTEmpireAIChatPlugin plugin, SqliteStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    public void recordClick(Player player) {
        UUID id = player.getUniqueId();
        Deque<Long> clicks = clickHistory.computeIfAbsent(id, k -> new ArrayDeque<>());
        long now = System.currentTimeMillis();
        clicks.addLast(now);

        // Remove clicks older than 1 second
        while (!clicks.isEmpty() && now - clicks.peekFirst() > 1000L) {
            clicks.pollFirst();
        }

        if (clicks.size() > 20) {
            flag(player, "AUTOCLICKER", 1, clicks.size() + " CPS");
        }
    }

    public int getCPS(Player player) {
        Deque<Long> clicks = clickHistory.get(player.getUniqueId());
        if (clicks == null) return 0;
        long now = System.currentTimeMillis();
        while (!clicks.isEmpty() && now - clicks.peekFirst() > 1000L) {
            clicks.pollFirst();
        }
        return clicks.size();
    }

    public int getAirTicks(Player player) {
        return airTicks.getOrDefault(player.getUniqueId(), 0);
    }

    public void setAirTicks(Player player, int ticks) {
        airTicks.put(player.getUniqueId(), ticks);
    }

    public int getTotalVL(Player player) {
        Map<String, Integer> map = violations.get(player.getUniqueId());
        if (map == null) return 0;
        return map.values().stream().mapToInt(Integer::intValue).sum();
    }

    public void flag(Player player, String check, int vlAmount, String details) {
        Map<String, Integer> map = violations.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>());
        int current = map.merge(check, vlAmount, Integer::sum);
        int total = getTotalVL(player);

        // Persist violation log to SQLite
        store.logWatchdogViolation(player.getUniqueId(), player.getName(), check, current, details);

        // Alert online staff
        String alert = plugin.color("&c[WATCHDOG] &f" + player.getName() + " &7failed &c" + check +
                " &7(VL: &c" + current + "&7/Total: &c" + total + "&7) &8[" + details + "]");

        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("dtempire.watchdog.staff") || staff.isOp()) {
                staff.sendMessage(alert);
            }
        }
        plugin.getLogger().warning("[Watchdog] " + player.getName() + " flagged " + check + " (VL: " + current + ", details: " + details + ")");

        // If player hit the KillAura orbiting bot -> instant ban!
        // If total VL exceeds threshold -> Watchdog ban!
        if ("KILLAURA_BOT".equalsIgnoreCase(check) || total >= MAX_BAN_VL) {
            punishBan(player, check);
        }
    }

    /** Authentic Hypixel-style Watchdog Ban Execution. */
    public void punishBan(Player player, String reason) {
        String banId = "#WD-" + (10000000 + random.nextInt(90000000));
        store.recordWatchdogBan(player.getUniqueId(), player.getName(), reason, banId);

        // Hypixel-style server announcement
        Bukkit.broadcastMessage(plugin.color("&8&m──────────────────────────────────────────────────"));
        Bukkit.broadcastMessage(plugin.color("&c&l[WATCHDOG CHEAT DETECTION]"));
        Bukkit.broadcastMessage(plugin.color("&fA player has been removed from your game for hacking or behavioral violations."));
        Bukkit.broadcastMessage(plugin.color("&7Thanks for your report, please continue to help us keep DTEmpire fair!"));
        Bukkit.broadcastMessage(plugin.color("&8&m──────────────────────────────────────────────────"));

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5f, 1.0f);
        }

        // Kick the player with ban screen
        String discordUrl = plugin.getConfig().getString("watchdog.discord-appeal-url", "https://discord.gg/dtempire");
        String kickMsg = plugin.color(
                "&c&lYou are permanently banned from DTEmpire Network!\n\n" +
                "&7Reason: &fWATCHDOG CHEAT DETECTION (&c" + reason + "&7)\n" +
                "&7Ban ID: &e" + banId + "\n" +
                "&7Ban Status: &cPERMANENT\n\n" +
                "&eIf you believe this detection was false, join our Discord to appeal:\n" +
                "&b" + discordUrl + "\n\n" +
                "&7Sharing your account or using disallowed modifications is strictly prohibited."
        );

        player.kickPlayer(kickMsg);
    }

    public void onQuit(Player player) {
        clickHistory.remove(player.getUniqueId());
        airTicks.remove(player.getUniqueId());
    }
}
