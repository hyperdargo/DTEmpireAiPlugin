package com.dtempire.aichat.telemetry;

import java.util.UUID;

/** Stores behavioral metrics for a player to learn their playstyle. */
public class PlayerTelemetry {

    private final UUID uuid;
    private String playerName;
    private long blocksMined;
    private long oresMined;
    private long blocksPlaced;
    private long mobsKilled;
    private long pvpKills;
    private long deaths;
    private int consecutiveDeaths;
    private long lastDeathTs;
    private double distanceTraveled;
    private String archetype;
    private boolean dirty;

    public PlayerTelemetry(UUID uuid, String playerName) {
        this.uuid = uuid;
        this.playerName = playerName;
        this.archetype = "SURVIVOR";
        this.dirty = false;
    }

    public PlayerTelemetry(UUID uuid, String playerName, long blocksMined, long oresMined,
                           long blocksPlaced, long mobsKilled, long pvpKills, long deaths,
                           int consecutiveDeaths, long lastDeathTs, double distanceTraveled,
                           String archetype) {
        this.uuid = uuid;
        this.playerName = playerName;
        this.blocksMined = blocksMined;
        this.oresMined = oresMined;
        this.blocksPlaced = blocksPlaced;
        this.mobsKilled = mobsKilled;
        this.pvpKills = pvpKills;
        this.deaths = deaths;
        this.consecutiveDeaths = consecutiveDeaths;
        this.lastDeathTs = lastDeathTs;
        this.distanceTraveled = distanceTraveled;
        this.archetype = (archetype != null && !archetype.isBlank()) ? archetype : "SURVIVOR";
        this.dirty = false;
    }

    /** Re-evaluates player archetype based on telemetry ratios. */
    public String updateArchetype() {
        if (oresMined >= 20 || (blocksMined >= 100 && ((double) oresMined / Math.max(1, blocksMined) >= 0.15))) {
            this.archetype = "MINER";
        } else if (blocksPlaced >= 100 && blocksPlaced >= blocksMined) {
            this.archetype = "BUILDER";
        } else if (mobsKilled >= 30 || pvpKills >= 3) {
            this.archetype = "WARRIOR";
        } else if (distanceTraveled >= 3000.0) {
            this.archetype = "EXPLORER";
        } else {
            this.archetype = "SURVIVOR";
        }
        return this.archetype;
    }

    public void addBlocksMined(long count) {
        this.blocksMined += count;
        this.dirty = true;
        updateArchetype();
    }

    public void addOresMined(long count) {
        this.oresMined += count;
        this.blocksMined += count;
        this.dirty = true;
        updateArchetype();
    }

    public void addBlocksPlaced(long count) {
        this.blocksPlaced += count;
        this.dirty = true;
        updateArchetype();
    }

    public void addMobsKilled(long count) {
        this.mobsKilled += count;
        this.dirty = true;
        updateArchetype();
    }

    public void addPvpKills(long count) {
        this.pvpKills += count;
        this.dirty = true;
        updateArchetype();
    }

    public void recordDeath() {
        long now = System.currentTimeMillis();
        // If died within 15 minutes of last death, increment consecutive count
        if (now - this.lastDeathTs < 15 * 60 * 1000L) {
            this.consecutiveDeaths++;
        } else {
            this.consecutiveDeaths = 1;
        }
        this.deaths++;
        this.lastDeathTs = now;
        this.dirty = true;
    }

    public void resetConsecutiveDeaths() {
        if (this.consecutiveDeaths > 0) {
            this.consecutiveDeaths = 0;
            this.dirty = true;
        }
    }

    public void addDistance(double dist) {
        if (dist > 0) {
            this.distanceTraveled += dist;
            this.dirty = true;
        }
    }

    public UUID getUuid() { return uuid; }
    public String getPlayerName() { return playerName; }
    public void setPlayerName(String name) { this.playerName = name; }
    public long getBlocksMined() { return blocksMined; }
    public long getOresMined() { return oresMined; }
    public long getBlocksPlaced() { return blocksPlaced; }
    public long getMobsKilled() { return mobsKilled; }
    public long getPvpKills() { return pvpKills; }
    public long getDeaths() { return deaths; }
    public int getConsecutiveDeaths() { return consecutiveDeaths; }
    public long getLastDeathTs() { return lastDeathTs; }
    public double getDistanceTraveled() { return distanceTraveled; }
    public String getArchetype() { return archetype; }
    public boolean isDirty() { return dirty; }
    public void setDirty(boolean dirty) { this.dirty = dirty; }
}
