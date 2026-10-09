package com.dtempire.aichat.anarchy;

import com.dtempire.aichat.DTEmpireAIChatPlugin;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages PvP Combat Tagging and prevents combat-logging on Anarchy/SMP servers.
 * Displays real-time actionbar countdowns and tracks the last PvP attacker.
 */
public class CombatTagManager {

    private final DTEmpireAIChatPlugin plugin;
    private final Map<UUID, Long> combatTimers = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> lastAttacker = new ConcurrentHashMap<>();
    private BukkitTask tickerTask;

    public CombatTagManager(DTEmpireAIChatPlugin plugin) {
        this.plugin = plugin;
        startActionBarTicker();
    }

    private void startActionBarTicker() {
        tickerTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            for (Map.Entry<UUID, Long> entry : combatTimers.entrySet()) {
                UUID uuid = entry.getKey();
                long expiry = entry.getValue();
                Player player = Bukkit.getPlayer(uuid);

                if (player == null || !player.isOnline()) {
                    continue;
                }

                if (now >= expiry) {
                    combatTimers.remove(uuid);
                    lastAttacker.remove(uuid);
                    player.sendActionBar(LegacyComponentSerializer.legacyAmpersand()
                            .deserialize("&a✔ You are no longer in combat."));
                } else {
                    int remainingSeconds = (int) Math.ceil((expiry - now) / 1000.0);
                    player.sendActionBar(LegacyComponentSerializer.legacyAmpersand()
                            .deserialize("&c⚔ In Combat: &e" + remainingSeconds + "s &8| &cDo not disconnect!"));
                }
            }
        }, 20L, 20L); // Every 1 second
    }

    /** Tags both players in combat for the configured duration (default 15s). */
    public void tag(Player victim, Player attacker) {
        int durationSeconds = plugin.getConfig().getInt("anarchy.combat-tag-seconds", 15);
        long expiry = System.currentTimeMillis() + (durationSeconds * 1000L);

        combatTimers.put(victim.getUniqueId(), expiry);
        combatTimers.put(attacker.getUniqueId(), expiry);

        lastAttacker.put(victim.getUniqueId(), attacker.getUniqueId());
        lastAttacker.put(attacker.getUniqueId(), victim.getUniqueId());
    }

    public boolean isTagged(Player player) {
        Long expiry = combatTimers.get(player.getUniqueId());
        return expiry != null && System.currentTimeMillis() < expiry;
    }

    public int getRemainingSeconds(Player player) {
        Long expiry = combatTimers.get(player.getUniqueId());
        if (expiry == null) return 0;
        long diff = expiry - System.currentTimeMillis();
        return diff > 0 ? (int) Math.ceil(diff / 1000.0) : 0;
    }

    public UUID getLastAttacker(UUID victimId) {
        return lastAttacker.get(victimId);
    }

    public void clear(UUID uuid) {
        combatTimers.remove(uuid);
        lastAttacker.remove(uuid);
    }

    public void shutdown() {
        if (tickerTask != null) {
            tickerTask.cancel();
            tickerTask = null;
        }
        combatTimers.clear();
        lastAttacker.clear();
    }
}
