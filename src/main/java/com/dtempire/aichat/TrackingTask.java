package com.dtempire.aichat;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/** Periodically banks playtime and posts server status to Discord. */
public class TrackingTask extends BukkitRunnable {

    private final DTEmpireAIChatPlugin plugin;

    public TrackingTask(DTEmpireAIChatPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        // Bank accrued playtime for online players so a crash or stop never loses it
        plugin.getManager().bankPlaytimeNow();
        plugin.getTrackingReporter().postNow().join();
    }
}