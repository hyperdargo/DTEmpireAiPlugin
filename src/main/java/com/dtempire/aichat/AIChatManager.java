package com.dtempire.aichat;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Tracks active private AI chat sessions per player. */
public class AIChatManager {

    private final Map<UUID, AIChatSession> sessions = new ConcurrentHashMap<>();

    public boolean startSession(Player player, String initialMessage) {
        UUID id = player.getUniqueId();
        if (sessions.containsKey(id)) return false;
        AIChatSession session = new AIChatSession(player.getName());
        if (initialMessage != null && !initialMessage.isBlank()) {
            session.addMessage("user", initialMessage);
        }
        sessions.put(id, session);
        return true;
    }

    public void endSession(Player player) {
        sessions.remove(player.getUniqueId());
    }

    public AIChatSession getSession(Player player) {
        return sessions.get(player.getUniqueId());
    }

    public boolean hasSession(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    public void shutdown() {
        sessions.clear();
    }
}