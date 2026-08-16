package com.dtempire.aichat;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class AIChatSession {

    private final String playerName;
    private final List<ChatMessage> history = new ArrayList<>();
    private Instant lastActivity = Instant.now();

    public AIChatSession(String playerName) {
        this.playerName = playerName;
    }

    public synchronized void addMessage(String role, String content) {
        history.add(new ChatMessage(role, content));
        lastActivity = Instant.now();
    }

    public synchronized List<ChatMessage> getHistory() {
        return new ArrayList<>(history);
    }

    public synchronized boolean isExpired(int timeoutSeconds) {
        return Instant.now().minusSeconds(timeoutSeconds).isAfter(lastActivity);
    }

    public synchronized CompletableFuture<String> sendToAI(DTEmpireAIChatPlugin plugin) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return callAPI(plugin);
            } catch (Exception e) {
                plugin.getLogger().warning("AI API error for " + playerName + ": " + e.getMessage());
                return null;
            }
        });
    }

    private String callAPI(DTEmpireAIChatPlugin plugin) throws Exception {
        String baseUrl = plugin.getConfig().getString("api.base-url", "http://127.0.0.1:25607");
        String model = plugin.getConfig().getString("api.model", "DiscordBot");
        String apiKey = plugin.getConfig().getString("api.api-key", "");
        int timeoutMs = plugin.getConfig().getInt("api.timeout-ms", 30000);
        String systemPrompt = plugin.getConfig().getString("api.system-prompt", "You are a helpful AI assistant in a Minecraft server. Keep responses concise.");

        if (apiKey.isEmpty()) {
            return plugin.color(plugin.getConfig().getString("messages.api-not-configured", "AI API not configured."));
        }

        // Build messages array
        JsonArray messages = new JsonArray();
        messages.add(createMessage("system", systemPrompt));
        for (ChatMessage msg : history) {
            messages.add(createMessage(msg.role, msg.content));
        }

        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.add("messages", messages);
        body.addProperty("stream", false);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofMillis(timeoutMs))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl.replaceFirst("/+$", "") + "/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(new Gson().toJson(body)))
                .timeout(java.time.Duration.ofMillis(timeoutMs))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            plugin.getLogger().warning("AI API returned " + response.statusCode() + ": " + response.body());
            return plugin.color(plugin.getConfig().getString("messages.ai-error", "Error contacting AI service."));
        }

        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        String text = json.getAsJsonArray("choices")
                .get(0).getAsJsonObject()
                .getAsJsonObject("message")
                .get("content").getAsString();

        return text.trim();
    }

    private JsonObject createMessage(String role, String content) {
        JsonObject obj = new JsonObject();
        obj.addProperty("role", role);
        obj.addProperty("content", content);
        return obj;
    }

    public String getPlayerName() {
        return playerName;
    }

    private static class ChatMessage {
        final String role;
        final String content;
        ChatMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }
}