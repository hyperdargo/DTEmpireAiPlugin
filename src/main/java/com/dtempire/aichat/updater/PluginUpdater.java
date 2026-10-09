package com.dtempire.aichat.updater;

import com.dtempire.aichat.DTEmpireAIChatPlugin;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Checks GitHub repository releases for plugin updates and automatically downloads
 * the new jar into the Bukkit update folder (plugins/update/).
 */
public class PluginUpdater {

    private final DTEmpireAIChatPlugin plugin;
    private final String repoOwner;
    private final String repoName;
    private final HttpClient client;

    public PluginUpdater(DTEmpireAIChatPlugin plugin) {
        this.plugin = plugin;
        this.repoOwner = plugin.getConfig().getString("updater.repo-owner", "hyperdargo");
        this.repoName = plugin.getConfig().getString("updater.repo-name", "DTEmpireAiPlugin");
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public void startScheduledCheck() {
        if (!plugin.getConfig().getBoolean("updater.enabled", true)) {
            return;
        }

        long checkIntervalHours = plugin.getConfig().getLong("updater.check-interval-hours", 6);
        long checkIntervalTicks = checkIntervalHours * 60 * 60 * 20L;

        // Run first check 30 seconds after boot, then periodically
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            checkForUpdate(null, false);
        }, 600L, checkIntervalTicks);
    }

    public CompletableFuture<Boolean> checkForUpdate(CommandSender sender, boolean manual) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String apiUrl = "https://api.github.com/repos/" + repoOwner + "/" + repoName + "/releases/latest";
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(apiUrl))
                        .header("Accept", "application/vnd.github.v3+json")
                        .header("User-Agent", "DTEmpireAIChat-PluginUpdater")
                        .timeout(Duration.ofSeconds(20))
                        .GET()
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 404) {
                    if (manual && sender != null) {
                        sender.sendMessage(plugin.color("&8[&bUpdater&8] &7No published releases found on GitHub repo yet."));
                    }
                    return false;
                }
                if (response.statusCode() != 200) {
                    plugin.getLogger().warning("[Updater] GitHub API returned status code " + response.statusCode());
                    if (manual && sender != null) {
                        sender.sendMessage(plugin.color("&8[&bUpdater&8] &cError checking for updates (HTTP " + response.statusCode() + ")."));
                    }
                    return false;
                }

                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                String rawTag = json.has("tag_name") ? json.get("tag_name").getAsString() : "";
                String latestVersion = cleanVersion(rawTag);
                String currentVersion = cleanVersion(plugin.getDescription().getVersion());

                if (isNewer(latestVersion, currentVersion)) {
                    String downloadUrl = null;
                    if (json.has("assets")) {
                        JsonArray assets = json.getAsJsonArray("assets");
                        for (JsonElement el : assets) {
                            JsonObject asset = el.getAsJsonObject();
                            String name = asset.get("name").getAsString().toLowerCase();
                            if (name.endsWith(".jar")) {
                                downloadUrl = asset.get("browser_download_url").getAsString();
                                break;
                            }
                        }
                    }

                    String alert = plugin.color("&8[&bUpdater&8] &aA new version of DTEmpireAIChat is available: &e" +
                            latestVersion + " &7(Current: &c" + currentVersion + "&7)");
                    plugin.getLogger().info("[Updater] A new version is available: v" + latestVersion + " (Current: v" + currentVersion + ")");

                    if (sender != null) {
                        sender.sendMessage(alert);
                    }
                    notifyStaff(alert);

                    boolean autoDownload = plugin.getConfig().getBoolean("updater.auto-download", true);
                    if (autoDownload && downloadUrl != null && !downloadUrl.isEmpty()) {
                        downloadUpdate(downloadUrl, latestVersion, sender);
                    } else if (downloadUrl == null) {
                        String noAssetMsg = plugin.color("&8[&bUpdater&8] &cNew release found but no .jar asset attached.");
                        if (sender != null) sender.sendMessage(noAssetMsg);
                    }
                    return true;
                } else {
                    if (manual && sender != null) {
                        sender.sendMessage(plugin.color("&8[&bUpdater&8] &aPlugin is up-to-date! Current version: &e" + currentVersion));
                    }
                    return false;
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[Updater] Failed to check for updates: " + e.getMessage());
                if (manual && sender != null) {
                    sender.sendMessage(plugin.color("&8[&bUpdater&8] &cFailed to check for updates: " + e.getMessage()));
                }
                return false;
            }
        });
    }

    private void downloadUpdate(String downloadUrl, String newVersion, CommandSender initiator) {
        try {
            plugin.getLogger().info("[Updater] Downloading update v" + newVersion + " from " + downloadUrl + "...");
            if (initiator != null) {
                initiator.sendMessage(plugin.color("&8[&bUpdater&8] &eDownloading update v" + newVersion + "..."));
            }

            File pluginsDir = plugin.getDataFolder().getParentFile();
            File updateFolder = new File(pluginsDir, "update");
            if (!updateFolder.exists()) {
                updateFolder.mkdirs();
            }

            File targetJar = new File(updateFolder, "DTEmpireAIChat.jar");
            File tempJar = new File(updateFolder, "DTEmpireAIChat.jar.tmp");

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(downloadUrl))
                    .header("User-Agent", "DTEmpireAIChat-PluginUpdater")
                    .timeout(Duration.ofSeconds(60))
                    .GET()
                    .build();

            HttpResponse<InputStream> resp = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() != 200) {
                plugin.getLogger().warning("[Updater] Failed to download update file (HTTP " + resp.statusCode() + ")");
                return;
            }

            try (InputStream in = resp.body()) {
                Files.copy(in, tempJar.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            Files.move(tempJar.toPath(), targetJar.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

            String successMsg = plugin.color("&8[&bUpdater&8] &aSuccessfully downloaded update &e" + newVersion +
                    "&a to &7plugins/update/&a! Restart or reload server to apply.");
            plugin.getLogger().info("[Updater] Update v" + newVersion + " downloaded successfully to plugins/update/!");

            if (initiator != null) {
                initiator.sendMessage(successMsg);
            }
            notifyStaff(successMsg);

        } catch (Exception e) {
            plugin.getLogger().warning("[Updater] Error while downloading update: " + e.getMessage());
            if (initiator != null) {
                initiator.sendMessage(plugin.color("&8[&bUpdater&8] &cDownload failed: " + e.getMessage()));
            }
        }
    }

    private void notifyStaff(String message) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.hasPermission("dtempire.admin") || player.isOp()) {
                    player.sendMessage(message);
                }
            }
        });
    }

    private String cleanVersion(String ver) {
        if (ver == null) return "0.0.0";
        return ver.trim().replaceFirst("^[vV]", "");
    }

    private boolean isNewer(String latest, String current) {
        try {
            String[] lParts = latest.split("[.-]");
            String[] cParts = current.split("[.-]");
            int maxLen = Math.max(lParts.length, cParts.length);
            for (int i = 0; i < maxLen; i++) {
                int l = i < lParts.length ? parseOrZero(lParts[i]) : 0;
                int c = i < cParts.length ? parseOrZero(cParts[i]) : 0;
                if (l > c) return true;
                if (l < c) return false;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private int parseOrZero(String s) {
        try {
            return Integer.parseInt(s.replaceAll("\\D+", ""));
        } catch (Exception e) {
            return 0;
        }
    }
}
