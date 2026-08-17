package com.dtempire.aichat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Posts server status embeds to a Discord channel.
 * First post creates ONE message; every later update EDITS that same message in place
 * (webhook PATCH), so the channel never spams. Falls back to bot token + channel id.
 */
public class DiscordReporter {

    private final DTEmpireAIChatPlugin plugin;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final File stateFile;
    private String lastMessageId;

    public DiscordReporter(DTEmpireAIChatPlugin plugin) {
        this.plugin = plugin;
        this.stateFile = new File(plugin.getDataFolder(), "discord_message_id.txt");
        loadState();
    }

    private void loadState() {
        try (FileReader r = new FileReader(stateFile)) {
            StringBuilder sb = new StringBuilder();
            int c;
            while ((c = r.read()) != -1) sb.append((char) c);
            String id = sb.toString().trim();
            if (!id.isEmpty()) lastMessageId = id;
        } catch (Exception ignored) {
        }
    }

    private void saveState() {
        try {
            stateFile.getParentFile().mkdirs();
            try (FileWriter w = new FileWriter(stateFile)) {
                if (lastMessageId != null) w.write(lastMessageId);
            }
        } catch (Exception ignored) {
        }
    }

    public boolean isConfigured() {
        return !plugin.getConfig().getString("tracking.discord.webhook-url", "").isEmpty()
                || (!plugin.getConfig().getString("tracking.discord.bot-token", "").isEmpty()
                && !plugin.getConfig().getString("tracking.discord.channel-id", "").isEmpty());
    }

    /** Sends a status embed now; edits the existing message if we have one. Returns true on 2xx. */
    public CompletableFuture<Boolean> postNow() {
        return CompletableFuture.supplyAsync(() -> {
            synchronized (this) {
                try {
                    JsonObject payload = buildPayload();
                    return send(payload);
                } catch (Exception e) {
                    plugin.getLogger().warning("Discord post failed: " + e.getMessage());
                    return false;
                }
            }
        });
    }

    private boolean send(JsonObject payload) throws Exception {
        String webhook = plugin.getConfig().getString("tracking.discord.webhook-url", "");
        String token = plugin.getConfig().getString("tracking.discord.bot-token", "");
        String channel = plugin.getConfig().getString("tracking.discord.channel-id", "");
        String auth = null;

        String base;
        if (!webhook.isEmpty()) {
            base = webhook;
        } else {
            base = "https://discord.com/api/v10/channels/" + channel + "/messages";
            auth = "Bot " + token;
        }

        // Try to edit the existing message in place (no spam)
        if (lastMessageId != null) {
            HttpRequest.Builder editBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(base + "/" + lastMessageId))
                    .header("Content-Type", "application/json")
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(payload.toString()))
                    .timeout(Duration.ofSeconds(10));
            if (auth != null) editBuilder.header("Authorization", auth);
            HttpResponse<String> editResp = client.send(editBuilder.build(), HttpResponse.BodyHandlers.ofString());
            if (editResp.statusCode() < 300) {
                return true;
            }
            if (editResp.statusCode() == 404) {
                // Message was deleted — clear saved id so next tick recreates
                lastMessageId = null;
                saveState();
            } else if (editResp.statusCode() == 403) {
                // Bot lost permission — don't fall back, avoid spam
                plugin.getLogger().warning("Discord edit forbidden (403): " + editResp.body());
                return false;
            } else {
                // Transient error (429/5xx) — skip this tick, do NOT recreate (would spam)
                plugin.getLogger().warning("Discord edit failed (" + editResp.statusCode() + "), keeping existing message: " + editResp.body());
                return false;
            }
        }

        // Create new message (only when we have no known message to edit)
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(base))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .timeout(Duration.ofSeconds(10));
        if (auth != null) builder.header("Authorization", auth);
        HttpResponse<String> resp = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 300) {
            plugin.getLogger().warning("Discord returned " + resp.statusCode() + ": " + resp.body());
            return false;
        }
        // Save the new message id so all future updates edit it
        try {
            JsonObject obj = JsonParser.parseString(resp.body()).getAsJsonObject();
            if (obj.has("id")) {
                lastMessageId = obj.get("id").getAsString();
                saveState();
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not parse Discord response for message id: " + e.getMessage());
        }
        return true;
    }

    private JsonObject buildPayload() {
        ServerMetrics metrics = new ServerMetrics(plugin);

        JsonObject embed = new JsonObject();
        String serverName = plugin.getConfig().getString("tracking.server.name", "DTEmpire");
        String motd = plugin.getConfig().getString("tracking.server.motd", "");
        String ip = plugin.getConfig().getString("tracking.server.ip", "");
        embed.addProperty("title", "\uD83D\uDD17 " + serverName + " Status");
        // Description: MOTD + copyable IP (monospace block)
        StringBuilder desc = new StringBuilder();
        if (!motd.isEmpty()) desc.append(motd);
        if (!ip.isEmpty() && plugin.getConfig().getBoolean("tracking.embed.show-ip", true)) {
            if (desc.length() > 0) desc.append("\n\n");
            desc.append("```\n").append(ip).append("\n```");
        }
        embed.addProperty("description", desc.toString());
        embed.addProperty("color", metrics.worstColor());

        JsonArray fields = new JsonArray();

        if (plugin.getConfig().getBoolean("tracking.embed.show-online", true)) {
            fields.add(field("Online", metrics.getOnline() + "/" + metrics.getMaxPlayers(), true));
        }
        if (plugin.getConfig().getBoolean("tracking.embed.show-ram", true)) {
            fields.add(field("RAM", metrics.getRamUsed() + " MB / " + metrics.getRamMax() + " MB (" + metrics.getRamPercent() + "%)", true));
        }
        if (plugin.getConfig().getBoolean("tracking.embed.show-cpu", true)) {
            fields.add(field("CPU", metrics.getCpuPercent() + "%", true));
        }
        if (plugin.getConfig().getBoolean("tracking.embed.show-storage", true)) {
            fields.add(field("Storage", metrics.getStorageUsed() + " GB / " + metrics.getStorageTotal() + " GB (" + metrics.getStoragePercent() + "%)", true));
        }
        if (plugin.getConfig().getBoolean("tracking.embed.show-ai-sessions", true)) {
            fields.add(field("Private AI Chat", String.valueOf(plugin.getManager().activeSessionCount()), true));
        }
        if (plugin.getConfig().getBoolean("tracking.embed.show-top-online", true)) {
            fields.add(field("Top Online", metrics.getTopOnline(), false));
        }
        if (plugin.getConfig().getBoolean("tracking.embed.show-recent-joins", true)) {
            fields.add(field("Recently Joined", metrics.getRecentJoins(), false));
        }
        if (plugin.getConfig().getBoolean("tracking.embed.show-recent-leaves", true)) {
            fields.add(field("Recently Left", metrics.getRecentLeaves(), false));
        }
        if (plugin.getConfig().getBoolean("tracking.embed.show-gamemode", true)) {
            String gm = plugin.getConfig().getString("tracking.server.gamemode", "survival");
            fields.add(field("Game Mode", gm, true));
        }
        if (plugin.getConfig().getBoolean("tracking.embed.show-server-version", true)) {
            String ver = plugin.getServer().getVersion();
            fields.add(field("Server Version", ver, true));
        }

        embed.add("fields", fields);
        embed.addProperty("timestamp", java.time.OffsetDateTime.now().toString());

        JsonObject payload = new JsonObject();
        payload.add("embeds", jsonArray(embed));
        return payload;
    }

    private JsonObject field(String name, String value, boolean inline) {
        JsonObject f = new JsonObject();
        f.addProperty("name", name);
        f.addProperty("value", value.isEmpty() ? "\u200b" : value);
        f.addProperty("inline", inline);
        return f;
    }

    private JsonArray jsonArray(JsonObject... objs) {
        JsonArray arr = new JsonArray();
        for (JsonObject o : objs) arr.add(o);
        return arr;
    }

    /** Metrics snapshot. */
    public static class ServerMetrics {
        private final DTEmpireAIChatPlugin plugin;
        private final int online;
        private final int maxPlayers;
        private final long ramUsedMb;
        private final long ramMaxMb;
        private final double cpuPercent;
        private final long storageUsedGb;
        private final long storageTotalGb;

        public ServerMetrics(DTEmpireAIChatPlugin plugin) {
            this.plugin = plugin;
            this.online = plugin.getServer().getOnlinePlayers().size();
            this.maxPlayers = plugin.getServer().getMaxPlayers();
            Runtime rt = Runtime.getRuntime();
            this.ramUsedMb = (rt.totalMemory() - rt.freeMemory()) / 1048576L;
            // Use the configured allocation (how much RAM the server was GIVEN), else JVM max
            long cfgRam = plugin.getConfig().getLong("tracking.resources.ram-max-mb", 0L);
            this.ramMaxMb = cfgRam > 0 ? cfgRam : rt.maxMemory() / 1048576L;
            this.cpuPercent = round1(osCpuLoad() * 100.0);
            java.io.File root = new java.io.File(".");
            long total = root.getTotalSpace() / 1073741824L;
            long free = root.getFreeSpace() / 1073741824L;
            // Use the configured storage allocation (how much disk the server was GIVEN), else full disk
            long cfgStorage = plugin.getConfig().getLong("tracking.resources.storage-max-gb", 0L);
            this.storageTotalGb = cfgStorage > 0 ? cfgStorage : total;
            this.storageUsedGb = cfgStorage > 0 ? Math.min(cfgStorage, total - free) : total - free;
        }

        private double osCpuLoad() {
            try {
                java.lang.management.OperatingSystemMXBean os =
                        java.lang.management.ManagementFactory.getOperatingSystemMXBean();
                if (os instanceof com.sun.management.OperatingSystemMXBean sunOs) {
                    return Math.max(0.0, sunOs.getCpuLoad());
                }
            } catch (Throwable ignored) {}
            return 0.0;
        }

        private static double round1(double d) {
            return Math.round(d * 10.0) / 10.0;
        }

        public int getOnline() { return online; }
        public int getMaxPlayers() { return maxPlayers; }
        public long getRamUsed() { return ramUsedMb; }
        public long getRamMax() { return ramMaxMb; }
        public int getRamPercent() {
            return ramMaxMb <= 0 ? 0 : (int) Math.round(ramUsedMb * 100.0 / ramMaxMb);
        }
        public double getCpuPercent() { return cpuPercent; }
        public long getStorageUsed() { return storageUsedGb; }
        public long getStorageTotal() { return storageTotalGb; }
        public int getStoragePercent() {
            return storageTotalGb <= 0 ? 0 : (int) Math.round(storageUsedGb * 100.0 / storageTotalGb);
        }

        /** Worst color across thresholds: red > yellow > green. */
        public int worstColor() {
            int ram = plugin.getConfig().getInt("tracking.thresholds.ram-warning-percent", 80);
            int cpu = plugin.getConfig().getInt("tracking.thresholds.cpu-warning-percent", 80);
            int st = plugin.getConfig().getInt("tracking.thresholds.storage-warning-percent", 90);
            int worst = 0; // 0 green, 1 yellow, 2 red
            if (getRamPercent() >= ram) worst = Math.max(worst, getRamPercent() >= ram + 15 ? 2 : 1);
            if (getCpuPercent() >= cpu) worst = Math.max(worst, getCpuPercent() >= cpu + 15 ? 2 : 1);
            if (getStoragePercent() >= st) worst = Math.max(worst, getStoragePercent() >= st + 10 ? 2 : 1);
            return switch (worst) {
                case 1 -> 0xFFDD00; // yellow
                case 2 -> 0xE74C3C; // red
                default -> 0x2ECC71; // green
            };
        }

        public String getTopOnline() {
            List<String> top = plugin.getTopPlayers(3);
            if (top.isEmpty()) return "No players yet.";
            StringBuilder sb = new StringBuilder();
            int i = 1;
            for (String name : top) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(rankMedal(i)).append(" ").append(name);
                i++;
            }
            return sb.toString();
        }

        private String rankMedal(int i) {
            return switch (i) {
                case 1 -> "\uD83E\uDD47"; // 🥇
                case 2 -> "\uD83E\uDD48"; // 🥈
                default -> "\uD83E\uDD49"; // 🥉
            };
        }

        public String getRecentJoins() {
            List<String> joins = plugin.getRecentJoins(3);
            return joins.isEmpty() ? "None" : String.join("\n", joins);
        }

        public String getRecentLeaves() {
            List<String> leaves = plugin.getRecentLeaves(3);
            return leaves.isEmpty() ? "None" : String.join("\n", leaves);
        }
    }
}