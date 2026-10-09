package com.dtempire.aichat;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Monitors public server chat and autonomously determines if Hermes should
 * intervene with helpful answers, while remaining quiet during normal player-to-player banter.
 */
public class PublicChatAIHandler {

    private final DTEmpireAIChatPlugin plugin;
    private final HttpClient httpClient;
    private final Gson gson = new Gson();

    private long lastGlobalReplyTime = 0L;
    private final Map<UUID, Long> lastPlayerReplyTime = new ConcurrentHashMap<>();

    private static final Pattern DIRECT_TRIGGER = Pattern.compile("(?i)^(@ai|@hermes|ai:|hermes:)\\s*");
    private static final Pattern AI_MENTION = Pattern.compile("(?i)\\b(ai|hermes|bot|server bot)\\b");
    private static final Pattern QUESTION_WORDS = Pattern.compile(
            "(?i)\\b(how|what|where|who|why|can someone|is there|help|rules?|daily|quest|watchdog|ip|discord|admin|owner|meteor|event)\\b"
    );

    public PublicChatAIHandler(DTEmpireAIChatPlugin plugin) {
        this.plugin = plugin;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    public void processPublicMessage(Player player, String rawMessage) {
        if (!plugin.getConfig().getBoolean("public-chat-ai.enabled", true)) {
            return;
        }

        String msg = rawMessage.trim();
        if (msg.length() < 2) return;

        boolean isExplicit = DIRECT_TRIGGER.matcher(msg).find();
        String cleanedMsg = DIRECT_TRIGGER.matcher(msg).replaceFirst("").trim();

        long now = System.currentTimeMillis();
        long globalCooldownMs = plugin.getConfig().getLong("public-chat-ai.cooldown-seconds", 12) * 1000L;
        long playerCooldownMs = 15000L;

        // If not explicit, verify cooldowns and smart heuristic pre-filters
        if (!isExplicit) {
            if (now - lastGlobalReplyTime < globalCooldownMs) {
                return;
            }
            if (now - lastPlayerReplyTime.getOrDefault(player.getUniqueId(), 0L) < playerCooldownMs) {
                return;
            }

            // Must mention AI OR contain a question/help keyword with a question mark or inquiry
            boolean mentionsAI = AI_MENTION.matcher(msg).find();
            boolean isQuestion = msg.contains("?") || QUESTION_WORDS.matcher(msg).find();

            if (!mentionsAI && !isQuestion) {
                // Regular player conversation; stay quiet without wasting API tokens
                return;
            }
        }

        String evaluationPrompt = ServerContext.getPublicChatEvaluationPrompt(plugin, player.getName(), cleanedMsg.isEmpty() ? msg : cleanedMsg);

        sendPublicPrompt(evaluationPrompt).thenAccept(reply -> {
            if (reply == null || reply.isBlank()) return;

            String trimmed = reply.trim();
            // Check if the AI decided to ignore
            if (trimmed.equalsIgnoreCase("IGNORE") ||
                    trimmed.toUpperCase(Locale.ROOT).startsWith("IGNORE") ||
                    trimmed.toUpperCase(Locale.ROOT).endsWith("IGNORE")) {
                return;
            }

            // Strip enclosing quotes if AI wrapped the response in them
            if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() > 2) {
                trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
            }

            final String finalReply = trimmed;
            Bukkit.getScheduler().runTask(plugin, () -> {
                lastGlobalReplyTime = System.currentTimeMillis();
                lastPlayerReplyTime.put(player.getUniqueId(), System.currentTimeMillis());

                String prefix = plugin.getConfig().getString("messages.ai-prefix", "&8[&bAI&8] &r");
                Bukkit.broadcastMessage(plugin.color(prefix + "&f" + finalReply));

                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.4f, 1.6f);
                }
            });
        });
    }

    private CompletableFuture<String> sendPublicPrompt(String systemPrompt) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String baseUrl = plugin.getConfig().getString("api.base-url", "http://127.0.0.1:25607");
                String model = plugin.getConfig().getString("api.model", "DiscordBot");
                String apiKey = plugin.getConfig().getString("api.api-key", "");
                int timeoutMs = plugin.getConfig().getInt("api.timeout-ms", 25000);

                if (apiKey.isEmpty()) return null;

                JsonArray messages = new JsonArray();
                JsonObject sysMsg = new JsonObject();
                sysMsg.addProperty("role", "system");
                sysMsg.addProperty("content", systemPrompt);
                messages.add(sysMsg);

                JsonObject body = new JsonObject();
                body.addProperty("model", model);
                body.add("messages", messages);
                body.addProperty("stream", false);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl.replaceFirst("/+$", "") + "/chat/completions"))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + apiKey)
                        .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                        .timeout(Duration.ofMillis(timeoutMs))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    plugin.getLogger().warning("[PublicChatAI] API returned status " + response.statusCode());
                    return null;
                }

                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                return json.getAsJsonArray("choices")
                        .get(0).getAsJsonObject()
                        .getAsJsonObject("message")
                        .get("content").getAsString();
            } catch (Exception e) {
                plugin.getLogger().warning("[PublicChatAI] Error evaluating public message: " + e.getMessage());
                return null;
            }
        });
    }
}
