package com.dtempire.aichat;

import com.dtempire.aichat.daily.DailyTaskCommand;
import com.dtempire.aichat.daily.DailyTaskManager;
import com.dtempire.aichat.gamemaster.AIAdminCommand;
import com.dtempire.aichat.gamemaster.AIGameMaster;
import com.dtempire.aichat.gift.CarePackageManager;
import com.dtempire.aichat.telemetry.TelemetryListener;
import com.dtempire.aichat.telemetry.TelemetryManager;
import com.dtempire.aichat.updater.PluginUpdater;
import com.dtempire.aichat.watchdog.WatchdogCommand;
import com.dtempire.aichat.watchdog.WatchdogListener;
import com.dtempire.aichat.watchdog.WatchdogManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class DTEmpireAIChatPlugin extends JavaPlugin {

    private static DTEmpireAIChatPlugin instance;
    private AIChatManager manager;
    private DiscordReporter reporter;
    private TrackingTask trackingTask;
    private TrackingListener trackingListener;

    // AI Game Master & Telemetry
    private TelemetryManager telemetryManager;
    private CarePackageManager carePackageManager;
    private DailyTaskManager dailyTaskManager;
    private AIGameMaster gameMaster;

    // Hypixel Watchdog Anti-Cheat
    private WatchdogManager watchdogManager;

    // Public Chat & Auto-Updater
    private PublicChatAIHandler publicChatAIHandler;
    private PluginUpdater pluginUpdater;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        mergeDefaultConfig();
        reloadConfig();

        // 1. Core Chat, Persistence & Public AI Chat Observer
        manager = new AIChatManager(getDataFolder());
        publicChatAIHandler = new PublicChatAIHandler(this);
        getCommand("aichat").setExecutor(new AIChatCommand(this, manager));
        getCommand("aiexit").setExecutor(new AIExitCommand(this, manager));
        getCommand("aihelp").setExecutor(new AIHelpCommand(this));
        getCommand("dtempireai").setExecutor(new TrackingCommand(this));
        getServer().getPluginManager().registerEvents(new ChatListener(this, manager, publicChatAIHandler), this);

        // 2. Telemetry, Gifts & Daily Tasks
        telemetryManager = new TelemetryManager(this, manager.getStore());
        carePackageManager = new CarePackageManager(this, manager.getStore());
        dailyTaskManager = new DailyTaskManager(this, manager.getStore());
        getServer().getPluginManager().registerEvents(
                new TelemetryListener(this, telemetryManager, dailyTaskManager, carePackageManager), this);

        getCommand("aidaily").setExecutor(new DailyTaskCommand(this, dailyTaskManager));

        // 3. Autonomous AI Game Master & Admin
        gameMaster = new AIGameMaster(this);
        getCommand("aiadmin").setExecutor(new AIAdminCommand(this, gameMaster));

        // 4. Watchdog Anti-Cheat
        watchdogManager = new WatchdogManager(this, manager.getStore());
        getServer().getPluginManager().registerEvents(new WatchdogListener(this, watchdogManager), this);
        getCommand("watchdog").setExecutor(new WatchdogCommand(this, watchdogManager));

        // 5. Discord Tracking & Welcomer
        trackingListener = new TrackingListener(this);
        reporter = new DiscordReporter(this);
        if (getConfig().getBoolean("tracking.enabled", false)) {
            startTracking();
        }

        // 6. Plugin Auto-Updater
        pluginUpdater = new PluginUpdater(this);
        pluginUpdater.startScheduledCheck();

        // Startup banner
        String green = "\u001B[32m";
        String cyan = "\u001B[36m";
        String reset = "\u001B[0m";
        String bold = "\u001B[1m";
        getLogger().info(green + bold + "╔════════════════════════════════════════════════╗" + reset);
        getLogger().info(green + bold + "║" + cyan + "     DTEmpire Autonomous AI Game Master         " + green + bold + "║" + reset);
        getLogger().info(green + bold + "║" + cyan + "  • Private AI Chat & Public Chat Observer       " + green + bold + "║" + reset);
        getLogger().info(green + bold + "║" + cyan + "  • Adaptive Telemetry & Sympathy Care Packages  " + green + bold + "║" + reset);
        getLogger().info(green + bold + "║" + cyan + "  • Personalized Daily Tasks & Bounties          " + green + bold + "║" + reset);
        getLogger().info(green + bold + "║" + cyan + "  • Server Events & Admin Orchestrator           " + green + bold + "║" + reset);
        getLogger().info(green + bold + "║" + cyan + "  • Watchdog Anti-Cheat with Discord Appeals     " + green + bold + "║" + reset);
        getLogger().info(green + bold + "║" + cyan + "  • GitHub Auto-Updater (plugins/update/)        " + green + bold + "║" + reset);
        getLogger().info(green + bold + "╚════════════════════════════════════════════════╝" + reset);
        getLogger().info("Server tracking " + (isTrackingEnabled() ? "ENABLED" : "disabled"));
    }

    @Override
    public void onDisable() {
        stopTracking();
        if (gameMaster != null) gameMaster.stopEventScheduler();
        if (telemetryManager != null) telemetryManager.shutdown();
        if (watchdogManager != null) watchdogManager.cleanup();
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
        if (!getConfig().isSet("tracking.interval-minutes")) {
            getConfig().set("tracking.interval-minutes", 1);
        }
        saveConfig();
    }

    public static DTEmpireAIChatPlugin getInstance() {
        return instance;
    }

    public AIChatManager getManager() {
        return manager;
    }

    public SqliteStore getStore() {
        return manager.getStore();
    }

    public TelemetryManager getTelemetryManager() {
        return telemetryManager;
    }

    public CarePackageManager getCarePackageManager() {
        return carePackageManager;
    }

    public DailyTaskManager getDailyTaskManager() {
        return dailyTaskManager;
    }

    public AIGameMaster getGameMaster() {
        return gameMaster;
    }

    public WatchdogManager getWatchdogManager() {
        return watchdogManager;
    }

    public PublicChatAIHandler getPublicChatAIHandler() {
        return publicChatAIHandler;
    }

    public PluginUpdater getPluginUpdater() {
        return pluginUpdater;
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
        if (gameMaster != null) {
            gameMaster.startEventScheduler();
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
