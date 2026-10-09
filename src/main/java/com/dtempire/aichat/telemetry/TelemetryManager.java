package com.dtempire.aichat.telemetry;

import com.dtempire.aichat.DTEmpireAIChatPlugin;
import com.dtempire.aichat.SqliteStore;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Manages online player telemetry cache and periodic SQLite sync. */
public class TelemetryManager {

    private final DTEmpireAIChatPlugin plugin;
    private final SqliteStore store;
    private final Map<UUID, PlayerTelemetry> cache = new ConcurrentHashMap<>();

    public TelemetryManager(DTEmpireAIChatPlugin plugin, SqliteStore store) {
        this.plugin = plugin;
        this.store = store;

        // Auto-save dirty telemetry every 60 seconds
        new BukkitRunnable() {
            @Override
            public void run() {
                saveAllDirty();
            }
        }.runTaskTimerAsynchronously(plugin, 1200L, 1200L);
    }

    public PlayerTelemetry getTelemetry(Player player) {
        return cache.computeIfAbsent(player.getUniqueId(),
                id -> store.loadTelemetry(id, player.getName()));
    }

    public PlayerTelemetry getTelemetry(UUID uuid, String fallbackName) {
        return cache.computeIfAbsent(uuid,
                id -> store.loadTelemetry(id, fallbackName));
    }

    public void onJoin(Player player) {
        PlayerTelemetry t = store.loadTelemetry(player.getUniqueId(), player.getName());
        t.setPlayerName(player.getName());
        cache.put(player.getUniqueId(), t);
    }

    public void onQuit(Player player) {
        PlayerTelemetry t = cache.remove(player.getUniqueId());
        if (t != null) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    store.saveTelemetry(t);
                }
            }.runTaskAsynchronously(plugin);
        }
    }

    public void saveAllDirty() {
        for (PlayerTelemetry t : cache.values()) {
            if (t.isDirty()) {
                store.saveTelemetry(t);
            }
        }
    }

    public void shutdown() {
        saveAllDirty();
        cache.clear();
    }
}
