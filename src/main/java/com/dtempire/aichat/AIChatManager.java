package com.dtempire.aichat;

import org.bukkit.entity.Player;

import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Tracks active private AI chat sessions + join/leave history with SQLite persistence. */
public class AIChatManager {

    private final Map<UUID, AIChatSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, Long> joinTimes = new ConcurrentHashMap<>();
    private final SqliteStore store;

    public AIChatManager(File dataFolder) {
        try {
            this.store = new SqliteStore(new File(dataFolder, "tracking.db"));
        } catch (SQLException e) {
            throw new RuntimeException("Failed to open tracking.db", e);
        }
    }

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
        bankPlaytimeNow();
        sessions.clear();
    }

    public int activeSessionCount() {
        return sessions.size();
    }

    // --- join/leave tracking (persisted to SQLite) ---
    public void onJoin(Player player) {
        joinTimes.put(player.getUniqueId(), System.currentTimeMillis());
        store.addJoin(player.getName());
    }

    public void onLeave(Player player) {
        Long joined = joinTimes.remove(player.getUniqueId());
        long sessionMs = joined == null ? 0 : System.currentTimeMillis() - joined;
        store.addPlaytime(player.getName(), sessionMs);
        store.addLeave(player.getName());
        sessions.remove(player.getUniqueId());
    }

    /** Move live session time into persisted playtime ledger. Safe to call anytime (runs each tracking tick). */
    public void bankPlaytimeNow() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Long> e : joinTimes.entrySet()) {
            Player p = pluginGetPlayer(e.getKey());
            if (p != null && p.isOnline()) {
                long live = now - e.getValue();
                if (live > 0) {
                    store.addPlaytime(p.getName(), live);
                    e.setValue(now);
                }
            }
        }
    }

    /** Top N by accumulated playtime including live session time, with durations. */
    public List<String> getTopPlayers(int n) {
        Map<String, Long> total = new java.util.HashMap<>(store.getPlaytime());
        long now = System.currentTimeMillis();
        Map<String, Long> live = new java.util.HashMap<>();
        for (Map.Entry<UUID, Long> e : joinTimes.entrySet()) {
            Player p = pluginGetPlayer(e.getKey());
            if (p != null && p.isOnline()) {
                live.put(p.getName(), Math.max(0, now - e.getValue()));
            }
        }
        for (Map.Entry<String, Long> e : live.entrySet()) {
            total.merge(e.getKey(), e.getValue(), Long::sum);
        }
        List<Map.Entry<String, Long>> sorted = new ArrayList<>(total.entrySet());
        sorted.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        List<String> result = new ArrayList<>();
        for (Map.Entry<String, Long> e : sorted) {
            String line = e.getKey() + " — " + formatDuration(e.getValue());
            result.add(line);
            if (result.size() >= n) break;
        }
        return result;
    }

    private static String formatDuration(long ms) {
        long totalMin = ms / 60000L;
        long h = totalMin / 60;
        long m = totalMin % 60;
        if (h >= 1) return h + "h " + m + "m";
        return m + "m";
    }

    public List<String> getRecentJoins(int n) {
        return store.getRecent("recent_joins", n);
    }

    public List<String> getRecentLeaves(int n) {
        return store.getRecent("recent_leaves", n);
    }

    public SqliteStore getStore() {
        return store;
    }

    private Player pluginGetPlayer(UUID id) {
        return org.bukkit.Bukkit.getPlayer(id);
    }
}