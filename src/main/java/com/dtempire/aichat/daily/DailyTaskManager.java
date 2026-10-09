package com.dtempire.aichat.daily;

import com.dtempire.aichat.DTEmpireAIChatPlugin;
import com.dtempire.aichat.SqliteStore;
import com.dtempire.aichat.telemetry.PlayerTelemetry;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Generates, tracks, and rewards personalized daily quests. */
public class DailyTaskManager {

    private final DTEmpireAIChatPlugin plugin;
    private final SqliteStore store;
    private final Map<UUID, DailyTask> cache = new ConcurrentHashMap<>();

    public DailyTaskManager(DTEmpireAIChatPlugin plugin, SqliteStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    public String getCurrentDayKey() {
        return LocalDate.now().toString();
    }

    public DailyTask getOrCreateTask(Player player) {
        UUID id = player.getUniqueId();
        String today = getCurrentDayKey();
        DailyTask cached = cache.get(id);
        if (cached != null && cached.getDayKey().equals(today)) {
            return cached;
        }

        DailyTask dbTask = store.getDailyTask(id, today);
        if (dbTask != null) {
            cache.put(id, dbTask);
            return dbTask;
        }

        // Generate tailored task based on archetype
        PlayerTelemetry telemetry = plugin.getTelemetryManager().getTelemetry(player);
        String archetype = telemetry.getArchetype();
        DailyTask newTask = generateTaskForArchetype(id, today, archetype);
        store.saveDailyTask(newTask);
        cache.put(id, newTask);
        return newTask;
    }

    private DailyTask generateTaskForArchetype(UUID uuid, String dayKey, String archetype) {
        switch (archetype) {
            case "MINER":
                return new DailyTask(uuid, dayKey, "MINE_ORES", 20, 0, false, false, "6x Diamonds, 32x Iron Ingots & 300 EXP");
            case "BUILDER":
                return new DailyTask(uuid, dayKey, "PLACE_BLOCKS", 100, 0, false, false, "64x Deepslate Bricks, 32x Quartz & 300 EXP");
            case "WARRIOR":
                return new DailyTask(uuid, dayKey, "KILL_MOBS", 15, 0, false, false, "1x Enchanted Golden Apple, 10x Ender Pearls & 500 EXP");
            case "EXPLORER":
                return new DailyTask(uuid, dayKey, "TRAVEL_DISTANCE", 1500, 0, false, false, "16x Fireworks, 1x Spyglass & 400 EXP");
            default:
                return new DailyTask(uuid, dayKey, "MINE_BLOCKS", 64, 0, false, false, "16x Cooked Beef, 16x Iron Ingots & 250 EXP");
        }
    }

    public void onBlockBreak(Player player, Material type) {
        DailyTask task = getOrCreateTask(player);
        if (task.isCompleted()) return;

        boolean isOre = type.name().endsWith("_ORE") || type == Material.ANCIENT_DEBRIS;
        if ("MINE_ORES".equals(task.getTaskType()) && isOre) {
            progressTask(player, task, 1);
        } else if ("MINE_BLOCKS".equals(task.getTaskType())) {
            progressTask(player, task, 1);
        }
    }

    public void onBlockPlace(Player player, Material type) {
        DailyTask task = getOrCreateTask(player);
        if (task.isCompleted()) return;

        if ("PLACE_BLOCKS".equals(task.getTaskType())) {
            progressTask(player, task, 1);
        }
    }

    public void onEntityKill(Player player, org.bukkit.entity.Entity victim) {
        DailyTask task = getOrCreateTask(player);
        if (task.isCompleted()) return;

        if ("KILL_MOBS".equals(task.getTaskType()) && victim instanceof Monster) {
            progressTask(player, task, 1);
        }
    }

    public void onMoveDistance(Player player, double deltaDistance) {
        DailyTask task = getOrCreateTask(player);
        if (task.isCompleted()) return;

        if ("TRAVEL_DISTANCE".equals(task.getTaskType())) {
            progressTask(player, task, (int) Math.round(deltaDistance));
        }
    }

    private void progressTask(Player player, DailyTask task, int amount) {
        boolean justCompleted = task.addProgress(amount);
        store.saveDailyTask(task);

        if (justCompleted) {
            player.sendTitle(
                    plugin.color("&a&lDAILY TASK COMPLETE!"),
                    plugin.color("&fType &e/aidaily claim &fto receive rewards!"),
                    10, 70, 20
            );
            player.sendMessage(plugin.color("&8[&bDaily Task&8] &aCongratulations! &fYou completed today's quest. Type &e/aidaily claim &fto collect your bounty."));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
        }
    }

    public boolean claimRewards(Player player) {
        DailyTask task = getOrCreateTask(player);
        if (!task.isCompleted()) {
            player.sendMessage(plugin.color("&8[&bDaily Task&8] &cYour daily task is not completed yet! Keep going: " + task.getProgressBar(10)));
            return false;
        }
        if (task.isClaimed()) {
            player.sendMessage(plugin.color("&8[&bDaily Task&8] &cYou have already claimed today's reward! Come back tomorrow for a new quest."));
            return false;
        }

        task.setClaimed(true);
        store.saveDailyTask(task);

        // Deliver rewards based on task type
        switch (task.getTaskType()) {
            case "MINE_ORES":
                giveOrDrop(player, new ItemStack(Material.DIAMOND, 6), new ItemStack(Material.IRON_INGOT, 32));
                player.giveExp(300);
                break;
            case "BUILDER":
                giveOrDrop(player, new ItemStack(Material.DEEPSLATE_BRICKS, 64), new ItemStack(Material.QUARTZ_BLOCK, 32));
                player.giveExp(300);
                break;
            case "WARRIOR":
                giveOrDrop(player, new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 1), new ItemStack(Material.ENDER_PEARL, 10));
                player.giveExp(500);
                break;
            case "TRAVEL_DISTANCE":
                giveOrDrop(player, new ItemStack(Material.FIREWORK_ROCKET, 16), new ItemStack(Material.SPYGLASS, 1));
                player.giveExp(400);
                break;
            default:
                giveOrDrop(player, new ItemStack(Material.COOKED_BEEF, 16), new ItemStack(Material.IRON_INGOT, 16));
                player.giveExp(250);
                break;
        }

        // Celebration
        spawnCelebrationFirework(player);
        player.sendTitle(plugin.color("&6&lREWARDS CLAIMED!"), plugin.color("&e" + task.getRewardDesc()), 10, 70, 20);
        player.sendMessage(plugin.color("&8[&bDaily Task&8] &aBounty Claimed! &fGranted: &e" + task.getRewardDesc()));
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        return true;
    }

    private void giveOrDrop(Player player, ItemStack... items) {
        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(items);
        for (ItemStack drop : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), drop);
        }
    }

    private void spawnCelebrationFirework(Player player) {
        Firework fw = (Firework) player.getWorld().spawnEntity(player.getLocation(), EntityType.FIREWORK_ROCKET);
        FireworkMeta meta = fw.getFireworkMeta();
        meta.addEffect(FireworkEffect.builder()
                .withColor(Color.ORANGE, Color.YELLOW, Color.GREEN)
                .withFade(Color.WHITE)
                .with(FireworkEffect.Type.BALL_LARGE)
                .withFlicker()
                .build());
        meta.setPower(1);
        fw.setFireworkMeta(meta);
    }
}
