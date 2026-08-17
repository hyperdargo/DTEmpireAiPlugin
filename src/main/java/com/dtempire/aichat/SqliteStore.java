package com.dtempire.aichat;

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

/** SQLite persistence (tracking.db): playtime ledger + recent join/leave history. Survives restarts. */
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

    /** Most recent N UNIQUE names from the given table (recent_joins / recent_leaves). */
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
}