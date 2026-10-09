package com.dtempire.aichat;

import com.dtempire.aichat.daily.DailyTask;
import com.dtempire.aichat.telemetry.PlayerTelemetry;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** SQLite persistence (tracking.db): playtime, telemetry, daily tasks, care packages, and Watchdog logs. */
public class SqliteStore {

    private final Connection conn;
    private static final int MAX_RECENT = 10;

    public SqliteStore(File dbFile) throws SQLException {
        dbFile.getParentFile().mkdirs();
        conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS playtime (player TEXT PRIMARY KEY, total_ms INTEGER NOT NULL)");
            st.execute("CREATE TABLE IF NOT EXISTS recent_joins (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, ts INTEGER NOT NULL)");
            st.execute("CREATE TABLE IF NOT EXISTS recent_leaves (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, ts INTEGER NOT NULL)");
            st.execute("CREATE TABLE IF NOT EXISTS meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)");

            // Player Telemetry (learning how players play)
            st.execute("CREATE TABLE IF NOT EXISTS player_telemetry (" +
                    "uuid TEXT PRIMARY KEY, " +
                    "player_name TEXT NOT NULL, " +
                    "blocks_mined INTEGER NOT NULL DEFAULT 0, " +
                    "ores_mined INTEGER NOT NULL DEFAULT 0, " +
                    "blocks_placed INTEGER NOT NULL DEFAULT 0, " +
                    "mobs_killed INTEGER NOT NULL DEFAULT 0, " +
                    "pvp_kills INTEGER NOT NULL DEFAULT 0, " +
                    "deaths INTEGER NOT NULL DEFAULT 0, " +
                    "consecutive_deaths INTEGER NOT NULL DEFAULT 0, " +
                    "last_death_ts INTEGER NOT NULL DEFAULT 0, " +
                    "distance_traveled REAL NOT NULL DEFAULT 0.0, " +
                    "archetype TEXT NOT NULL DEFAULT 'SURVIVOR')");

            // Daily Tasks
            st.execute("CREATE TABLE IF NOT EXISTS daily_tasks (" +
                    "uuid TEXT NOT NULL, " +
                    "day_key TEXT NOT NULL, " +
                    "task_type TEXT NOT NULL, " +
                    "target_count INTEGER NOT NULL, " +
                    "current_progress INTEGER NOT NULL DEFAULT 0, " +
                    "completed INTEGER NOT NULL DEFAULT 0, " +
                    "claimed INTEGER NOT NULL DEFAULT 0, " +
                    "reward_desc TEXT NOT NULL, " +
                    "PRIMARY KEY (uuid, day_key))");

            // Care Packages & Sympathy gifts cooldowns
            st.execute("CREATE TABLE IF NOT EXISTS care_packages (" +
                    "uuid TEXT PRIMARY KEY, " +
                    "last_received_ts INTEGER NOT NULL, " +
                    "package_count INTEGER NOT NULL DEFAULT 0)");

            // Watchdog Anti-Cheat violations & bans
            st.execute("CREATE TABLE IF NOT EXISTS watchdog_violations (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "uuid TEXT NOT NULL, " +
                    "player_name TEXT NOT NULL, " +
                    "check_name TEXT NOT NULL, " +
                    "vl INTEGER NOT NULL, " +
                    "details TEXT NOT NULL, " +
                    "ts INTEGER NOT NULL)");

            st.execute("CREATE TABLE IF NOT EXISTS watchdog_bans (" +
                    "uuid TEXT PRIMARY KEY, " +
                    "player_name TEXT NOT NULL, " +
                    "reason TEXT NOT NULL, " +
                    "ban_id TEXT NOT NULL, " +
                    "ts INTEGER NOT NULL)");

            // Anarchy / SMP Bounty System
            st.execute("CREATE TABLE IF NOT EXISTS bounties (" +
                    "uuid TEXT PRIMARY KEY, " +
                    "player_name TEXT NOT NULL, " +
                    "diamonds INTEGER NOT NULL DEFAULT 0, " +
                    "placed_by TEXT NOT NULL, " +
                    "last_updated INTEGER NOT NULL)");
        }
    }

    public synchronized void addPlaytime(String name, long ms) {
        if (ms <= 0) return;
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO playtime(player, total_ms) VALUES(?,?) " +
                "ON CONFLICT(player) DO UPDATE SET total_ms = total_ms + excluded.total_ms")) {
            ps.setString(1, name);
            ps.setLong(2, ms);
            ps.executeUpdate();
        } catch (SQLException ignored) {
        }
    }

    /** All players sorted by total playtime, highest first. */
    public synchronized Map<String, Long> getPlaytime() {
        Map<String, Long> out = new LinkedHashMap<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT player, total_ms FROM playtime ORDER BY total_ms DESC")) {
            while (rs.next()) out.put(rs.getString(1), rs.getLong(2));
        } catch (SQLException ignored) {
        }
        return out;
    }

    public synchronized void addJoin(String name) {
        insertRecent("recent_joins", name);
    }

    public synchronized void addLeave(String name) {
        insertRecent("recent_leaves", name);
    }

    private void insertRecent(String table, String name) {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO " + table + "(name, ts) VALUES(?,?)")) {
            ps.setString(1, name);
            ps.setLong(2, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException ignored) {
        }
        try (PreparedStatement del = conn.prepareStatement(
                "DELETE FROM " + table + " WHERE id NOT IN " +
                "(SELECT id FROM " + table + " ORDER BY id DESC LIMIT " + MAX_RECENT + ")")) {
            del.executeUpdate();
        } catch (SQLException ignored) {
        }
    }

    public synchronized List<String> getRecent(String table, int n) {
        java.util.LinkedHashSet<String> uniq = new java.util.LinkedHashSet<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT name FROM " + table + " ORDER BY id DESC LIMIT 50")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) uniq.add(rs.getString(1));
            }
        } catch (SQLException ignored) {
        }
        List<String> out = new ArrayList<>(uniq);
        while (out.size() > Math.min(n, 50)) out.remove(out.size() - 1);
        return out;
    }

    public synchronized void setMeta(String key, String value) {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO meta(key, value) VALUES(?,?) " +
                "ON CONFLICT(key) DO UPDATE SET value = excluded.value")) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        } catch (SQLException ignored) {
        }
    }

    public synchronized String getMeta(String key) {
        try (PreparedStatement ps = conn.prepareStatement("SELECT value FROM meta WHERE key=?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString(1);
            }
        } catch (SQLException ignored) {
        }
        return null;
    }

    // ==========================================
    // Telemetry & Learning
    // ==========================================

    public synchronized PlayerTelemetry loadTelemetry(UUID uuid, String defaultName) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT player_name, blocks_mined, ores_mined, blocks_placed, mobs_killed, pvp_kills, " +
                "deaths, consecutive_deaths, last_death_ts, distance_traveled, archetype FROM player_telemetry WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new PlayerTelemetry(
                            uuid,
                            rs.getString("player_name"),
                            rs.getLong("blocks_mined"),
                            rs.getLong("ores_mined"),
                            rs.getLong("blocks_placed"),
                            rs.getLong("mobs_killed"),
                            rs.getLong("pvp_kills"),
                            rs.getLong("deaths"),
                            rs.getInt("consecutive_deaths"),
                            rs.getLong("last_death_ts"),
                            rs.getDouble("distance_traveled"),
                            rs.getString("archetype")
                    );
                }
            }
        } catch (SQLException ignored) {
        }
        return new PlayerTelemetry(uuid, defaultName);
    }

    public synchronized void saveTelemetry(PlayerTelemetry t) {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO player_telemetry(uuid, player_name, blocks_mined, ores_mined, blocks_placed, " +
                "mobs_killed, pvp_kills, deaths, consecutive_deaths, last_death_ts, distance_traveled, archetype) " +
                "VALUES(?,?,?,?,?,?,?,?,?,?,?,?) " +
                "ON CONFLICT(uuid) DO UPDATE SET " +
                "player_name = excluded.player_name, " +
                "blocks_mined = excluded.blocks_mined, " +
                "ores_mined = excluded.ores_mined, " +
                "blocks_placed = excluded.blocks_placed, " +
                "mobs_killed = excluded.mobs_killed, " +
                "pvp_kills = excluded.pvp_kills, " +
                "deaths = excluded.deaths, " +
                "consecutive_deaths = excluded.consecutive_deaths, " +
                "last_death_ts = excluded.last_death_ts, " +
                "distance_traveled = excluded.distance_traveled, " +
                "archetype = excluded.archetype")) {
            ps.setString(1, t.getUuid().toString());
            ps.setString(2, t.getPlayerName());
            ps.setLong(3, t.getBlocksMined());
            ps.setLong(4, t.getOresMined());
            ps.setLong(5, t.getBlocksPlaced());
            ps.setLong(6, t.getMobsKilled());
            ps.setLong(7, t.getPvpKills());
            ps.setLong(8, t.getDeaths());
            ps.setInt(9, t.getConsecutiveDeaths());
            ps.setLong(10, t.getLastDeathTs());
            ps.setDouble(11, t.getDistanceTraveled());
            ps.setString(12, t.getArchetype());
            ps.executeUpdate();
            t.setDirty(false);
        } catch (SQLException ignored) {
        }
    }

    // ==========================================
    // Daily Tasks
    // ==========================================

    public synchronized DailyTask getDailyTask(UUID uuid, String dayKey) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT task_type, target_count, current_progress, completed, claimed, reward_desc " +
                "FROM daily_tasks WHERE uuid=? AND day_key=?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, dayKey);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new DailyTask(
                            uuid,
                            dayKey,
                            rs.getString("task_type"),
                            rs.getInt("target_count"),
                            rs.getInt("current_progress"),
                            rs.getInt("completed") == 1,
                            rs.getInt("claimed") == 1,
                            rs.getString("reward_desc")
                    );
                }
            }
        } catch (SQLException ignored) {
        }
        return null;
    }

    public synchronized void saveDailyTask(DailyTask task) {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO daily_tasks(uuid, day_key, task_type, target_count, current_progress, completed, claimed, reward_desc) " +
                "VALUES(?,?,?,?,?,?,?,?) " +
                "ON CONFLICT(uuid, day_key) DO UPDATE SET " +
                "current_progress = excluded.current_progress, " +
                "completed = excluded.completed, " +
                "claimed = excluded.claimed")) {
            ps.setString(1, task.getUuid().toString());
            ps.setString(2, task.getDayKey());
            ps.setString(3, task.getTaskType());
            ps.setInt(4, task.getTargetCount());
            ps.setInt(5, task.getCurrentProgress());
            ps.setInt(6, task.isCompleted() ? 1 : 0);
            ps.setInt(7, task.isClaimed() ? 1 : 0);
            ps.setString(8, task.getRewardDesc());
            ps.executeUpdate();
        } catch (SQLException ignored) {
        }
    }

    // ==========================================
    // Care Packages
    // ==========================================

    public synchronized long getLastCarePackageTime(UUID uuid) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT last_received_ts FROM care_packages WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong(1);
            }
        } catch (SQLException ignored) {
        }
        return 0L;
    }

    public synchronized void recordCarePackageGiven(UUID uuid) {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO care_packages(uuid, last_received_ts, package_count) VALUES(?,?,1) " +
                "ON CONFLICT(uuid) DO UPDATE SET last_received_ts = excluded.last_received_ts, " +
                "package_count = package_count + 1")) {
            ps.setString(1, uuid.toString());
            ps.setLong(2, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException ignored) {
        }
    }

    // ==========================================
    // Watchdog Violations & Bans
    // ==========================================

    public synchronized void logWatchdogViolation(UUID uuid, String name, String check, int vl, String details) {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO watchdog_violations(uuid, player_name, check_name, vl, details, ts) VALUES(?,?,?,?,?,?)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.setString(3, check);
            ps.setInt(4, vl);
            ps.setString(5, details);
            ps.setLong(6, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException ignored) {
        }
    }

    public synchronized void recordWatchdogBan(UUID uuid, String name, String reason, String banId) {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO watchdog_bans(uuid, player_name, reason, ban_id, ts) VALUES(?,?,?,?,?) " +
                "ON CONFLICT(uuid) DO UPDATE SET reason = excluded.reason, ts = excluded.ts")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.setString(3, reason);
            ps.setString(4, banId);
            ps.setLong(5, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException ignored) {
        }
    }

    public static class WatchdogBanRecord {
        public final String reason;
        public final String banId;
        public final long ts;

        public WatchdogBanRecord(String reason, String banId, long ts) {
            this.reason = reason;
            this.banId = banId;
            this.ts = ts;
        }
    }

    public synchronized WatchdogBanRecord getWatchdogBanInfo(UUID uuid) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT reason, ban_id, ts FROM watchdog_bans WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new WatchdogBanRecord(
                            rs.getString("reason"),
                            rs.getString("ban_id"),
                            rs.getLong("ts")
                    );
                }
            }
        } catch (SQLException ignored) {
        }
        return null;
    }

    public synchronized boolean isWatchdogBanned(UUID uuid) {
        try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM watchdog_bans WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException ignored) {
        }
        return false;
    }

    public synchronized boolean unbanWatchdog(String nameOrUuid) {
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM watchdog_bans WHERE uuid = ? OR LOWER(player_name) = LOWER(?)")) {
            ps.setString(1, nameOrUuid);
            ps.setString(2, nameOrUuid);
            int rows = ps.executeUpdate();
            return rows > 0;
        } catch (SQLException ignored) {
            return false;
        }
    }

    public synchronized boolean clearWatchdogViolations(String nameOrUuid) {
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM watchdog_violations WHERE uuid = ? OR LOWER(player_name) = LOWER(?)")) {
            ps.setString(1, nameOrUuid);
            ps.setString(2, nameOrUuid);
            int rows = ps.executeUpdate();
            return rows > 0;
        } catch (SQLException ignored) {
            return false;
        }
    }

    public synchronized int getWatchdogBanCount() {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM watchdog_bans")) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException ignored) {
        }
        return 0;
    }

    public synchronized List<String> getRecentWatchdogBans(int limit) {
        List<String> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT player_name, reason, ban_id FROM watchdog_bans ORDER BY ts DESC LIMIT ?")) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getString("player_name") + " (" + rs.getString("reason") + " - " + rs.getString("ban_id") + ")");
                }
            }
        } catch (SQLException ignored) {
        }
        return out;
    }

    public static class BountyRecord {
        public final UUID uuid;
        public final String playerName;
        public final int diamonds;
        public final String placedBy;
        public final long lastUpdated;

        public BountyRecord(UUID uuid, String playerName, int diamonds, String placedBy, long lastUpdated) {
            this.uuid = uuid;
            this.playerName = playerName;
            this.diamonds = diamonds;
            this.placedBy = placedBy;
            this.lastUpdated = lastUpdated;
        }
    }

    public synchronized int addOrIncreaseBounty(UUID uuid, String playerName, int diamonds, String placedBy) {
        if (diamonds <= 0) return 0;
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO bounties(uuid, player_name, diamonds, placed_by, last_updated) VALUES(?,?,?,?,?) " +
                "ON CONFLICT(uuid) DO UPDATE SET diamonds = diamonds + excluded.diamonds, " +
                "player_name = excluded.player_name, placed_by = excluded.placed_by, last_updated = excluded.last_updated")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, playerName);
            ps.setInt(3, diamonds);
            ps.setString(4, placedBy);
            ps.setLong(5, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException ignored) {
        }
        BountyRecord rec = getBounty(uuid);
        return rec != null ? rec.diamonds : diamonds;
    }

    public synchronized BountyRecord getBounty(UUID uuid) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT player_name, diamonds, placed_by, last_updated FROM bounties WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new BountyRecord(uuid, rs.getString("player_name"), rs.getInt("diamonds"),
                            rs.getString("placed_by"), rs.getLong("last_updated"));
                }
            }
        } catch (SQLException ignored) {
        }
        return null;
    }

    public synchronized int removeBounty(UUID uuid) {
        BountyRecord rec = getBounty(uuid);
        if (rec == null) return 0;
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM bounties WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException ignored) {
        }
        return rec.diamonds;
    }

    public synchronized List<BountyRecord> getTopBounties(int limit) {
        List<BountyRecord> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT uuid, player_name, diamonds, placed_by, last_updated FROM bounties ORDER BY diamonds DESC LIMIT ?")) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    try {
                        UUID id = UUID.fromString(rs.getString("uuid"));
                        out.add(new BountyRecord(id, rs.getString("player_name"), rs.getInt("diamonds"),
                                rs.getString("placed_by"), rs.getLong("last_updated")));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
        } catch (SQLException ignored) {
        }
        return out;
    }
}
