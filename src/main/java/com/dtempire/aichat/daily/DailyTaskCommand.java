package com.dtempire.aichat.daily;

import com.dtempire.aichat.DTEmpireAIChatPlugin;
import com.dtempire.aichat.telemetry.PlayerTelemetry;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Command executor for /aidaily [claim]. */
public class DailyTaskCommand implements CommandExecutor {

    private final DTEmpireAIChatPlugin plugin;
    private final DailyTaskManager taskManager;

    public DailyTaskCommand(DTEmpireAIChatPlugin plugin, DailyTaskManager taskManager) {
        this.plugin = plugin;
        this.taskManager = taskManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.color("&cOnly players can check or claim daily tasks."));
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("claim")) {
            taskManager.claimRewards(player);
            return true;
        }

        DailyTask task = taskManager.getOrCreateTask(player);
        PlayerTelemetry telemetry = plugin.getTelemetryManager().getTelemetry(player);

        player.sendMessage(plugin.color("&8&m────────────────────────────────────────"));
        player.sendMessage(plugin.color("&8[&bAI Daily Quest&8] &fPlayer Archetype: &e&l" + telemetry.getArchetype()));
        player.sendMessage(plugin.color("&7Quest: &f" + task.getDisplayName()));
        player.sendMessage(plugin.color("&7Progress: " + task.getProgressBar(12)));
        player.sendMessage(plugin.color("&7Reward: &6" + task.getRewardDesc()));

        if (task.isClaimed()) {
            player.sendMessage(plugin.color("&a✔ Bounty already claimed for today! Resets at midnight."));
        } else if (task.isCompleted()) {
            player.sendMessage(plugin.color("&e★ Completed! Type &a/aidaily claim &eto collect your rewards!"));
        } else {
            player.sendMessage(plugin.color("&7Keep playing to complete your task and claim rewards."));
        }
        player.sendMessage(plugin.color("&8&m────────────────────────────────────────"));
        return true;
    }
}
