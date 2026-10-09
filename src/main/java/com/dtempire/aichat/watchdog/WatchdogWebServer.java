package com.dtempire.aichat.watchdog;

import com.dtempire.aichat.DTEmpireAIChatPlugin;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

/**
 * Embedded secure HTTP API listener for Discord Bot integrations.
 * Allows Discord Appeal Bots to query ban info and execute instant unbans.
 */
public class WatchdogWebServer {

    private final DTEmpireAIChatPlugin plugin;
    private final WatchdogManager watchdogManager;
    private HttpServer server;

    public WatchdogWebServer(DTEmpireAIChatPlugin plugin, WatchdogManager watchdogManager) {
        this.plugin = plugin;
        this.watchdogManager = watchdogManager;
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("watchdog.api.enabled", true)) {
            return;
        }

        int port = plugin.getConfig().getInt("watchdog.api.port", 25609);
        String host = plugin.getConfig().getString("watchdog.api.host", "127.0.0.1");

        try {
            server = HttpServer.create(new InetSocketAddress(host, port), 0);
            server.createContext("/api/unban", new UnbanHandler());
            server.createContext("/api/baninfo", new BanInfoHandler());
            server.createContext("/api/status", new StatusHandler());
            server.setExecutor(Executors.newSingleThreadExecutor());
            server.start();
            plugin.getLogger().info("[WatchdogAPI] Listening for Discord Bot appeals on " + host + ":" + port);
        } catch (Exception e) {
            plugin.getLogger().warning("[WatchdogAPI] Could not start HTTP server on port " + port + ": " + e.getMessage());
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    private boolean isAuthorized(HttpExchange exchange) {
        String configuredKey = plugin.getConfig().getString("watchdog.api.secret-key", "dtempire-watchdog-secret-key");
        if (configuredKey == null || configuredKey.isEmpty()) return true;

        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            if (token.equals(configuredKey)) return true;
        }

        String query = exchange.getRequestURI().getQuery();
        if (query != null && query.contains("token=" + configuredKey)) {
            return true;
        }

        return false;
    }

    private void sendJson(HttpExchange exchange, int statusCode, JsonObject json) throws IOException {
        byte[] bytes = json.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private class StatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            JsonObject res = new JsonObject();
            res.addProperty("status", "online");
            res.addProperty("server", "DTEmpire");
            res.addProperty("online_players", Bukkit.getOnlinePlayers().size());
            sendJson(exchange, 200, res);
        }
    }

    private class BanInfoHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!isAuthorized(exchange)) {
                JsonObject err = new JsonObject();
                err.addProperty("error", "Unauthorized");
                sendJson(exchange, 401, err);
                return;
            }

            String query = exchange.getRequestURI().getQuery();
            String player = null;
            if (query != null) {
                for (String param : query.split("&")) {
                    String[] pair = param.split("=");
                    if (pair.length == 2 && pair[0].equalsIgnoreCase("player")) {
                        player = pair[1];
                        break;
                    }
                }
            }

            if (player == null || player.isBlank()) {
                JsonObject err = new JsonObject();
                err.addProperty("error", "Missing 'player' parameter");
                sendJson(exchange, 400, err);
                return;
            }

            JsonObject res = new JsonObject();
            res.addProperty("player", player);
            // Query DB info
            res.addProperty("query_time", System.currentTimeMillis());
            sendJson(exchange, 200, res);
        }
    }

    private class UnbanHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            if (!isAuthorized(exchange)) {
                JsonObject err = new JsonObject();
                err.addProperty("error", "Unauthorized");
                sendJson(exchange, 401, err);
                return;
            }

            try (InputStreamReader reader = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8)) {
                JsonObject body = JsonParser.parseReader(reader).getAsJsonObject();
                String targetPlayer = body.has("player") ? body.get("player").getAsString().trim() : "";
                String staffName = body.has("staff") ? body.get("staff").getAsString() : "Discord Staff";
                String note = body.has("reason") ? body.get("reason").getAsString() : "Appeal Approved";

                if (targetPlayer.isEmpty()) {
                    JsonObject err = new JsonObject();
                    err.addProperty("error", "Player name is required");
                    sendJson(exchange, 400, err);
                    return;
                }

                // Execute unban on main thread
                Bukkit.getScheduler().runTask(plugin, () -> {
                    boolean unbanned = watchdogManager.unban(targetPlayer);

                    if (unbanned) {
                        Bukkit.broadcastMessage(plugin.color("&8&m──────────────────────────────────────────────────"));
                        Bukkit.broadcastMessage(plugin.color("&8[&cWatchdog&8] &e" + targetPlayer + " &7was &aunbanned &7via approved Discord appeal!"));
                        Bukkit.broadcastMessage(plugin.color("&7Approved by: &b" + staffName));
                        Bukkit.broadcastMessage(plugin.color("&8&m──────────────────────────────────────────────────"));
                    }
                });

                JsonObject res = new JsonObject();
                res.addProperty("success", true);
                res.addProperty("player", targetPlayer);
                res.addProperty("message", "Unban request executed on server");
                sendJson(exchange, 200, res);
            } catch (Exception e) {
                JsonObject err = new JsonObject();
                err.addProperty("error", "Internal error: " + e.getMessage());
                sendJson(exchange, 500, err);
            }
        }
    }
}
