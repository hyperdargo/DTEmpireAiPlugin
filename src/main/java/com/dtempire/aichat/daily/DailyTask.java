package com.dtempire.aichat.daily;

import java.util.UUID;

/** Represents a player's daily quest. */
public class DailyTask {

    private final UUID uuid;
    private final String dayKey;
    private final String taskType;
    private final int targetCount;
    private int currentProgress;
    private boolean completed;
    private boolean claimed;
    private final String rewardDesc;

    public DailyTask(UUID uuid, String dayKey, String taskType, int targetCount,
                     int currentProgress, boolean completed, boolean claimed, String rewardDesc) {
        this.uuid = uuid;
        this.dayKey = dayKey;
        this.taskType = taskType;
        this.targetCount = targetCount;
        this.currentProgress = currentProgress;
        this.completed = completed;
        this.claimed = claimed;
        this.rewardDesc = rewardDesc;
    }

    public boolean addProgress(int delta) {
        if (completed) return false;
        this.currentProgress = Math.min(this.targetCount, this.currentProgress + delta);
        if (this.currentProgress >= this.targetCount) {
            this.completed = true;
            return true; // just became completed!
        }
        return false;
    }

    public String getDisplayName() {
        switch (taskType) {
            case "MINE_ORES":
                return "Mine " + targetCount + " Rare Ores (Iron/Gold/Diamond/Ancient Debris)";
            case "MINE_BLOCKS":
                return "Mine " + targetCount + " Blocks in Deep Caverns";
            case "PLACE_BLOCKS":
                return "Place " + targetCount + " Building Blocks";
            case "KILL_MOBS":
                return "Defeat " + targetCount + " Hostile Monsters";
            case "TRAVEL_DISTANCE":
                return "Explore " + targetCount + " Blocks Across the Realm";
            default:
                return "Complete " + targetCount + " Daily Actions";
        }
    }

    public String getProgressBar(int barLength) {
        double ratio = (double) currentProgress / Math.max(1, targetCount);
        int filled = (int) Math.round(ratio * barLength);
        StringBuilder sb = new StringBuilder("&a[");
        for (int i = 0; i < barLength; i++) {
            if (i < filled) sb.append("■");
            else if (i == filled) sb.append("&7□");
            else sb.append("□");
        }
        sb.append("&a] &f(").append(currentProgress).append("/").append(targetCount).append(")");
        return sb.toString();
    }

    public UUID getUuid() { return uuid; }
    public String getDayKey() { return dayKey; }
    public String getTaskType() { return taskType; }
    public int getTargetCount() { return targetCount; }
    public int getCurrentProgress() { return currentProgress; }
    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }
    public boolean isClaimed() { return claimed; }
    public void setClaimed(boolean claimed) { this.claimed = claimed; }
    public String getRewardDesc() { return rewardDesc; }
}
