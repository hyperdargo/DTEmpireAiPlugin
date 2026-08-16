package com.dtempire.aichat;

import org.bukkit.plugin.java.JavaPlugin;

public final class DTEmpireAIChatPlugin extends JavaPlugin {

    private static DTEmpireAIChatPlugin instance;
    private AIChatManager manager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        manager = new AIChatManager();
        getCommand("aichat").setExecutor(new AIChatCommand(this, manager));
        getCommand("aiexit").setExecutor(new AIExitCommand(this, manager));
        getServer().getPluginManager().registerEvents(new ChatListener(this, manager), this);
        
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
    }

    @Override
    public void onDisable() {
        if (manager != null) manager.shutdown();
        getLogger().info("DTEmpireAIChat disabled.");
    }

    public static DTEmpireAIChatPlugin getInstance() {
        return instance;
    }

    public AIChatManager getManager() {
        return manager;
    }

    public String color(String text) {
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', text);
    }
}