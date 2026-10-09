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
 * Monitors public server chat and handles player requests for Hermes AI assistance
 * in global chat (e.g. 'hey ai', 'hey aichat', 'message ai', etc.),
 * helping players answer each other, translate, or explain gameplay.
 */
public class PublicChatAIHandler {

    private final DTEmpireAIChatPlugin plugin;
    private final HttpClient httpClient;
    private final Gson gson = new Gson();

    private long lastGlobalReplyTime = 0L;
    private final Map<UUID, Long> lastPlayerReplyTime = new ConcurrentHashMap<>();

    // Matches natural speech triggers: hey aichat, hey ai, message ai, msg ai, tell ai, ask ai, @ai, etc.
    private static final Pattern DIRECT_TRIGGER = Pattern.compile(
            "(?i)^(hey\\s+aichat|hey\\s+ai|hi\\s+aichat|hi\\s+ai|hello\\s+aichat|hello\\s+ai|message\\s+ai|msg\\s+ai|message\\s+aichat|msg\\s+aichat|tell\\s+ai|ask\\s+ai|hey\\s+hermes|@aichat|@ai|@hermes|aichat:|ai:|hermes:|^aichat\\b|^ai\\b)[:,-]?\\s*"
    );

    private static final Pattern AI_MENTION = Pattern.compile("(?i)\\b(ai|hermes|bot|server bot)\\b");
    private static final Pattern QUESTION_WORDS = Pattern.compile(
            "(?i)\\b(how|what|where|who|why|can someone|is there|help|rules?|daily|quest|watchdog|ip|discord|admin|owner|meteor|event|bounty)\\b"
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
        long globalCooldownMs = plugin.getConfig().getLong("public-chat-ai.cooldown-seconds", 10) * 1000L;
        long playerCooldownMs = 12000L;

        if (isExplicit) {
            // Anti-spam protection for explicit triggers (5 seconds per player)
            long lastExplicit = lastPlayerReplyTime.getOrDefault(player.getUniqueId(), 0L);
            if (now - lastExplicit < 5000L) {
                player.sendMessage(plugin.color("&cPlease wait a moment before prompting AI in global chat again."));
                return;
            }
            lastPlayerReplyTime.put(player.getUniqueId(), now);
        } else {
            // Heuristic filtering for passive overhearing
            if (now - lastGlobalReplyTime < globalCooldownMs) {
                return;
            }
            if (now - lastPlayerReplyTime.getOrDefault(player.getUniqueId(), 0L) < playerCooldownMs) {
                return;
            }

            boolean mentionsAI = AI_MENTION.matcher(msg).find();
            boolean isQuestion = msg.contains("?") || QUESTION_WORDS.matcher(msg).find();

            if (!mentionsAI && !isQuestion) {
                return;
            }
        }

        String promptMessage = (isExplicit && !cleanedMsg.isEmpty()) ? cleanedMsg : msg;
        String evaluationPrompt = ServerContext.getPublicChatEvaluationPrompt(plugin, player.getName(), promptMessage, isExplicit);

        sendPublicPrompt(evaluationPrompt).thenAccept(reply -> {
            if (reply == null || reply.isBlank()) return;

            String trimmed = reply.trim();
            if (!isExplicit) {
                if (trimmed.equalsIgnoreCase("IGNORE") ||
                        trimmed.toUpperCase(Locale.ROOT).startsWith("IGNORE") ||
                        trimmed.toUpperCase(Locale.ROOT).endsWith("IGNORE")) {
                    return;
                }
            }

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

    public void processExplicitCommand(Player player, String message) {
        if (!plugin.getConfig().getBoolean("public-chat-ai.enabled", true)) {
            player.sendMessage(plugin.color("&cGlobal AI chat is currently disabled by server configuration."));
            return;
        }

        long now = System.currentTimeMillis();
        long lastExplicit = lastPlayerReplyTime.getOrDefault(player.getUniqueId(), 0L);
        if (now - lastExplicit < 5000L) {
            player.sendMessage(plugin.color("&cPlease wait a moment before asking AI in global chat again."));
            return;
        }
        lastPlayerReplyTime.put(player.getUniqueId(), now);

        player.sendMessage(plugin.color("&7[AI] Asking AI assistant... Response will appear in global chat shortly."));

        String evaluationPrompt = ServerContext.getPublicChatEvaluationPrompt(plugin, player.getName(), message, true);
        sendPublicPrompt(evaluationPrompt).thenAccept(reply -> {
            if (reply == null || reply.isBlank()) {
                player.sendMessage(plugin.color("&cError contacting AI assistant. Please try again."));
                return;
            }

            String trimmed = reply.trim();
            if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() > 2) {
                trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
            }

            final String finalReply = trimmed;
            Bukkit.getScheduler().runTask(plugin, () -> {
                lastGlobalReplyTime = System.currentTimeMillis();
                String prefix = plugin.getConfig().getString("messages.ai-prefix", "&8[&bAI&8] &r");
                Bukkit.broadcastMessage(plugin.color(prefix + "&f" + finalReply));

                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.4f, 1.6f);
                }
            });
        });
    }

    public void processConsoleCommand(String message) {
        String evaluationPrompt = ServerContext.getPublicChatEvaluationPrompt(plugin, "Console", message, true);
        sendPublicPrompt(evaluationPrompt).thenAccept(reply -> {
            if (reply == null || reply.isBlank()) return;

            String trimmed = reply.trim();
            if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() > 2) {
                trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
            }

            final String finalReply = trimmed;
            Bukkit.getScheduler().runTask(plugin, () -> {
                lastGlobalReplyTime = System.currentTimeMillis();
                String prefix = plugin.getConfig().getString("messages.ai-prefix", "&8[&bAI&8] &r");
                Bukkit.broadcastMessage(plugin.color(prefix + "&f" + finalReply));
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
