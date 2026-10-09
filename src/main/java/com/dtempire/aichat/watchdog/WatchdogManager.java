package com.dtempire.aichat.watchdog;

import com.dtempire.aichat.DTEmpireAIChatPlugin;
import com.dtempire.aichat.SqliteStore;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central Watchdog Anti-Cheat Manager.
 * Manages violation levels, staff-only alerts, violation decay, and ban executions.
 */
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
    // Timestamp of last damage taken (damage knockback false-positive immunity)
    private final Map<UUID, Long> lastDamageTime = new ConcurrentHashMap<>();
    // Last verified ground location for rubberbanding/setbacks
    private final Map<UUID, Location> lastGroundLocation = new ConcurrentHashMap<>();

    private BukkitTask decayTask;

    public WatchdogManager(DTEmpireAIChatPlugin plugin, SqliteStore store) {
        this.plugin = plugin;
        this.store = store;
        startDecayTask();
    }

    /** Periodic task to decay old violations over time so clean play resets accumulated flags. */
    private void startDecayTask() {
        decayTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            try {
                // Copy keys first — never mutate the map while iterating its entrySet
                // (that was throwing ConcurrentModification inside removeIf and killing the task).
                for (UUID uuid : new java.util.ArrayList<>(violations.keySet())) {
                    Map<String, Integer> checkMap = violations.get(uuid);
                    if (checkMap == null) continue;
                    for (String check : new java.util.ArrayList<>(checkMap.keySet())) {
                        Integer val = checkMap.get(check);
                        if (val == null) continue;
                        int newVal = val - 1;
                        if (newVal <= 0) {
                            checkMap.remove(check);
                        } else {
                            checkMap.put(check, newVal);
                        }
                    }
                    if (checkMap.isEmpty()) {
                        violations.remove(uuid);
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[Watchdog] Decay task error: " + e.getMessage());
            }
        }, 1200L, 1200L); // Every 60 seconds, decay -1 (XRAY VL sticks long enough to ban)
    }

    public void recordDamage(Player player) {
        lastDamageTime.put(player.getUniqueId(), System.currentTimeMillis());
    }

    public boolean hasRecentDamage(Player player) {
        long last = lastDamageTime.getOrDefault(player.getUniqueId(), 0L);
        return (System.currentTimeMillis() - last) < 2000L; // 2 seconds knockback immunity
    }

    public void setLastGroundLocation(Player player, Location loc) {
        lastGroundLocation.put(player.getUniqueId(), loc);
    }

    public Location getLastGroundLocation(Player player) {
        return lastGroundLocation.get(player.getUniqueId());
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

    public void clearVL(UUID uuid) {
        violations.remove(uuid);
        airTicks.remove(uuid);
    }

    public boolean unban(String nameOrUuid) {
        boolean ok = store.unbanWatchdog(nameOrUuid);
        try {
            UUID id = UUID.fromString(nameOrUuid);
            clearVL(id);
        } catch (IllegalArgumentException ignored) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().equalsIgnoreCase(nameOrUuid)) {
                    clearVL(p.getUniqueId());
                    break;
                }
            }
        }
        return ok;
    }

    /**
     * Flags a player violation.
     * Alerts are sent ONLY to OP / Admin staff.
     * Auto-bans trigger ONLY for definitive cheat trap strikes (KILLAURA_BOT).
     * Movement flags (Speed/Fly/etc) are NEVER auto-permabanned.
     */
    public void flag(Player player, String check, int vlAmount, String details) {
        Map<String, Integer> map = violations.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>());
        int current = map.merge(check, vlAmount, Integer::sum);
        int total = getTotalVL(player);

        // Persist violation log to SQLite
        store.logWatchdogViolation(player.getUniqueId(), player.getName(), check, current, details);

        // Alert online staff/OPs ONLY — regular players NEVER see Watchdog messages!
        String alert = plugin.color("&8[&cWatchdog&8] &c" + player.getName() + " &7failed &e" + check +
                " &7(VL: &c" + current + "&7/Total: &c" + total + "&7) &8[" + details + "]");

        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (isStaff(staff)) {
                staff.sendMessage(alert);
            }
        }
        plugin.getLogger().warning("[Watchdog] " + player.getName() + " flagged " + check + " (VL: " + current + ", details: " + details + ")");

        // Auto-ban policy:
        // 1. KILLAURA_BOT (striking the invisible orbiting trap bot) -> 100% definitive cheat client -> Instant ban!
        // 2. Movement checks (SPEED, FLY, JESUS) -> NEVER auto-ban! (Prevents false bans from cave jumping / lag)
        boolean isAuraBot = "KILLAURA_BOT".equalsIgnoreCase(check);
        boolean allowMovementAutoban = plugin.getConfig().getBoolean("watchdog.autoban-movement", false);
        int maxMovementVL = plugin.getConfig().getInt("watchdog.max-ban-vl", 100);
        boolean allowXrayAutoban = plugin.getConfig().getBoolean("watchdog.autoban-xray", true);
        int maxXrayVL = plugin.getConfig().getInt("watchdog.max-xray-ban-vl", 15);

        if (isAuraBot) {
            punishBan(player, "KILLAURA_BOT");
        } else if (allowMovementAutoban && total >= maxMovementVL) {
            punishBan(player, check);
        } else if (allowXrayAutoban && "XRAY".equalsIgnoreCase(check) && total >= maxXrayVL) {
            punishBan(player, "XRAY");
        }
    }

    /**
     * Executes Watchdog Ban.
     * Notice is sent ONLY to OP / Admin staff (no public broadcast to regular players).
     */
    public void punishBan(Player player, String reason) {
        String banId = "#WD-" + (10000000 + random.nextInt(90000000));
        store.recordWatchdogBan(player.getUniqueId(), player.getName(), reason, banId);

        // Alert online staff / OPs ONLY — regular players do NOT see this announcement!
        String staffNotice = plugin.color("&8&m──────────────────────────────────────────────────\n" +
                "&8[&cWatchdog&8] &c&lSTAFF NOTICE: &e" + player.getName() + " &7has been banned by Watchdog!\n" +
                "&7Reason: &c" + reason + " &8| &7Ban ID: &e" + banId + "\n" +
                "&8&m──────────────────────────────────────────────────");

        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (isStaff(staff)) {
                staff.sendMessage(staffNotice);
                staff.playSound(staff.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5f, 1.0f);
            }
        }

        // Kick the player with ban screen and Discord appeal link
        String discordUrl = plugin.getConfig().getString("watchdog.discord-appeal-url", "http://dsc.gg/dtempire-server");
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

        // Send Discord Ban Log Embed via Webhook asynchronously
        sendDiscordBanLog(player, reason, banId);
    }

    private void sendDiscordBanLog(Player player, String reason, String banId) {
        String botApiUrl = plugin.getConfig().getString("watchdog.discord.bot-api-url", "http://127.0.0.1:25608/ban");
        String webhookUrl = plugin.getConfig().getString("watchdog.discord.bans-webhook-url", "");
        String appealUrl = plugin.getConfig().getString("watchdog.discord-appeal-url", "http://dsc.gg/dtempire-server");

        CompletableFuture.runAsync(() -> {
            boolean deliveredViaBridge = false;

            // 1. Try sending directly to Local Watchdog Discord Bot Bridge (Single card with button)
            if (botApiUrl != null && !botApiUrl.trim().isEmpty()) {
                try {
                    JsonObject bridgePayload = new JsonObject();
                    bridgePayload.addProperty("player", player.getName());
                    bridgePayload.addProperty("banId", banId);
                    bridgePayload.addProperty("reason", reason);
                    bridgePayload.addProperty("status", "Permanent Ban");
                    bridgePayload.addProperty("appealUrl", appealUrl);

                    HttpRequest bridgeRequest = HttpRequest.newBuilder()
                            .uri(URI.create(botApiUrl.trim()))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(bridgePayload.toString()))
                            .timeout(Duration.ofSeconds(2))
                            .build();

                    HttpResponse<String> resp = HttpClient.newHttpClient().send(bridgeRequest, HttpResponse.BodyHandlers.ofString());
                    if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                        deliveredViaBridge = true;
                        plugin.getLogger().info("[Watchdog] Ban card for " + player.getName() + " published via Discord Bot Bridge.");
                    }
                } catch (Exception ignored) {
                    // Bot bridge offline or unconfigured, fall back to webhook
                }
            }

            // 2. If not delivered via bridge, fall back to Discord Webhook
            if (!deliveredViaBridge && webhookUrl != null && !webhookUrl.trim().isEmpty()) {
                try {
                    String avatarUrl = "https://mc-heads.net/avatar/" + player.getName() + "/128";

                    JsonObject payload = new JsonObject();
                    payload.addProperty("username", "Watchdog Security");
                    payload.addProperty("avatar_url", "https://mc-heads.net/avatar/Watchdog/128");

                    JsonArray embeds = new JsonArray();
                    JsonObject embed = new JsonObject();
                    embed.addProperty("title", "🛡️ WATCHDOG BAN ENFORCED");
                    embed.addProperty("color", 15158332); // Red #E74C3C
                    embed.addProperty("description", "A player has been permanently banned by Watchdog Anti-Cheat.");

                    JsonObject thumbnail = new JsonObject();
                    thumbnail.addProperty("url", avatarUrl);
                    embed.add("thumbnail", thumbnail);

                    JsonArray fields = new JsonArray();

                    JsonObject f1 = new JsonObject();
                    f1.addProperty("name", "👤 Player");
                    f1.addProperty("value", "`" + player.getName() + "`");
                    f1.addProperty("inline", true);
                    fields.add(f1);

                    JsonObject f2 = new JsonObject();
                    f2.addProperty("name", "🆔 Ban ID");
                    f2.addProperty("value", "`" + banId + "`");
                    f2.addProperty("inline", true);
                    fields.add(f2);

                    JsonObject f3 = new JsonObject();
                    f3.addProperty("name", "⚖️ Reason");
                    f3.addProperty("value", "**" + reason + "**");
                    f3.addProperty("inline", true);
                    fields.add(f3);

                    JsonObject f4 = new JsonObject();
                    f4.addProperty("name", "📋 Status");
                    f4.addProperty("value", "🔴 Permanent Ban");
                    f4.addProperty("inline", true);
                    fields.add(f4);

                    JsonObject f5 = new JsonObject();
                    f5.addProperty("name", "📩 How to Appeal");
                    f5.addProperty("value", "Join [" + appealUrl + "](" + appealUrl + ") and submit an appeal with your Ban ID in the appeals channel.");
                    f5.addProperty("inline", false);
                    fields.add(f5);

                    embed.add("fields", fields);

                    JsonObject footer = new JsonObject();
                    footer.addProperty("text", "DTEmpire Watchdog Security Network • Player Ban Record");
                    embed.add("footer", footer);

                    embeds.add(embed);
                    payload.add("embeds", embeds);

                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(webhookUrl.trim()))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                            .timeout(Duration.ofSeconds(10))
                            .build();

                    HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding());
                } catch (Exception e) {
                    plugin.getLogger().warning("[Watchdog] Failed to post ban embed to Discord webhook: " + e.getMessage());
                }
            }
        });
    }

    /** Helper to check if a player is staff or OP. */
    public static boolean isStaff(Player player) {
        return player.isOp() || player.hasPermission("dtempire.watchdog.staff") || player.hasPermission("dtempire.admin");
    }

    public void onQuit(Player player) {
        clickHistory.remove(player.getUniqueId());
        airTicks.remove(player.getUniqueId());
        lastDamageTime.remove(player.getUniqueId());
        lastGroundLocation.remove(player.getUniqueId());
    }

    public void cleanup() {
        if (decayTask != null) {
            decayTask.cancel();
        }
        violations.clear();
        clickHistory.clear();
        airTicks.clear();
        lastDamageTime.clear();
        lastGroundLocation.clear();
    }
}
