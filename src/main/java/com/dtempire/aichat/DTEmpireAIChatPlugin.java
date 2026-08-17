package com.dtempire.aichat;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class DTEmpireAIChatPlugin extends JavaPlugin {

    private static DTEmpireAIChatPlugin instance;
    private AIChatManager manager;
    private DiscordReporter reporter;
    private TrackingTask trackingTask;
    private TrackingListener trackingListener;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        mergeDefaultConfig();
        reloadConfig();

        manager = new AIChatManager(getDataFolder());
        getCommand("aichat").setExecutor(new AIChatCommand(this, manager));
        getCommand("aiexit").setExecutor(new AIExitCommand(this, manager));
        getCommand("aihelp").setExecutor(new AIHelpCommand(this));
        getCommand("dtempireai").setExecutor(new TrackingCommand(this));
        getServer().getPluginManager().registerEvents(new ChatListener(this, manager), this);

        trackingListener = new TrackingListener(this);
        reporter = new DiscordReporter(this);
        if (getConfig().getBoolean("tracking.enabled", false)) {
            startTracking();
        }

        // Colorful startup message
        String green = "\u001B[32m";
        String cyan = "\u001B[36m";
        String reset = "\u001B[0m";
        String bold = "\u001B[1m";
        getLogger().info(green + bold + "╔════════════════════════════════════════╗" + reset);
        getLogger().info(green + bold + "║" + cyan + "       DTEmpire AI Chat Bot           " + green + bold + "║" + reset);
        getLogger().info(green + bold + "║" + cyan + "   Private AI Chat for Minecraft       " + green + bold + "║" + reset);
        getLogger().info(green + bold + "║" + cyan + "   /aichat <msg>  |  /aiexit           " + green + bold + "║" + reset);
        getLogger().info(green + bold + "╚════════════════════════════════════════╝" + reset);
        getLogger().info("Server tracking " + (isTrackingEnabled() ? "ENABLED" : "disabled"));
    }

    @Override
    public void onDisable() {
        stopTracking();
        if (manager != null) manager.shutdown();
        getLogger().info("DTEmpireAIChat disabled.");
    }

    /** Merge any new default keys into existing config so updates don't wipe user settings. */
    private void mergeDefaultConfig() {
        java.io.InputStream def = getResource("config.yml");
        if (def == null) return;
        org.bukkit.configuration.file.YamlConfiguration defaults =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                        new java.io.InputStreamReader(def));
        getConfig().addDefaults(defaults);
        getConfig().options().copyDefaults(true);
        saveConfig();
    }

    public static DTEmpireAIChatPlugin getInstance() {
        return instance;
    }

    public AIChatManager getManager() {
        return manager;
    }

    public DiscordReporter getTrackingReporter() {
        return reporter;
    }

    public boolean isTrackingEnabled() {
        return trackingTask != null;
    }

    public synchronized void startTracking() {
        if (trackingTask != null) return;
        if (!reporter.isConfigured()) {
            getLogger().warning("Tracking enabled but no webhook-url or bot-token/channel-id configured. Set tracking.discord in config.yml.");
            return;
        }
        trackingTask = new TrackingTask(this);
        trackingTask.runTaskTimerAsynchronously(this, 0L, intervalTicks());
        getLogger().info("Server tracking started (every " + getConfig().getInt("tracking.interval-minutes", 15) + " min).");
    }

    public synchronized void stopTracking() {
        if (trackingTask != null) {
            trackingTask.cancel();
            trackingTask = null;
        }
    }

    public synchronized void reloadAndRestartTracking() {
        stopTracking();
        reloadConfig();
        if (getConfig().getBoolean("tracking.enabled", false)) {
            startTracking();
        }
    }

    private long intervalTicks() {
        return Math.max(1L, getConfig().getInt("tracking.interval-minutes", 15)) * 1200L;
    }

    // --- metrics delegates ---
    public List<String> getTopPlayers(int n) {
        return manager.getTopPlayers(n);
    }

    public List<String> getRecentJoins(int n) {
        return manager.getRecentJoins(n);
    }

    public List<String> getRecentLeaves(int n) {
        return manager.getRecentLeaves(n);
    }

    public String color(String text) {
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', text);
    }
}