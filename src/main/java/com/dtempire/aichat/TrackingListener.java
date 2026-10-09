package com.dtempire.aichat;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Listens for player join/leave to track metrics, send cinematic welcomes,
 * give starter packages for first-timers, and post Discord status updates.
 */
public class TrackingListener implements Listener {

    private final DTEmpireAIChatPlugin plugin;

    public TrackingListener(DTEmpireAIChatPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getManager().onJoin(player);

        boolean isFirstJoin = !player.hasPlayedBefore();

        if (plugin.getConfig().getBoolean("welcome.enabled", true)) {
            int delay = plugin.getConfig().getInt("welcome.delay-ticks", 40);

            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!player.isOnline()) return;

                    if (isFirstJoin) {
                        // First-time join experience
                        // 1. Broadcast to whole server
                        String broadcast = plugin.getConfig().getString("welcome.first-join-broadcast",
                                "&6[Welcome] &fLet's give a warm welcome to &e{player} &ffor joining &bDTEmpire&f for the first time! 🎉")
                                .replace("{player}", player.getName());
                        Bukkit.broadcastMessage(plugin.color(broadcast));

                        // 2. Play celebratory sounds
                        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.2f);

                        // 3. Send Title & Subtitle
                        String title = plugin.getConfig().getString("welcome.first-join-title", "&6&lWELCOME TO DTEMPIRE");
                        String subtitle = plugin.getConfig().getString("welcome.first-join-subtitle", "&fYour adventure begins! Type &e/aihelp");
                        player.sendTitle(plugin.color(title), plugin.color(subtitle), 10, 80, 20);

                        // 4. Launch firework
                        if (plugin.getConfig().getBoolean("welcome.first-join-firework", true)) {
                            spawnWelcomeFirework(player);
                        }

                        // 5. Give Starter Kit if enabled
                        if (plugin.getConfig().getBoolean("welcome.starter-kit.enabled", true)) {
                            giveStarterKit(player);
                        }

                        // 6. AI Game Master Welcome Whisper
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                if (player.isOnline()) {
                                    player.sendMessage(plugin.color("&8&m────────────────────────────────────────"));
                                    player.sendMessage(plugin.color("&8[&bHermes AI&8] &bGreetings, &f" + player.getName() + "&b! I am Hermes, the server's AI Game Master."));
                                    player.sendMessage(plugin.color("&8[&bHermes AI&8] &7Type &e/aidaily &7to check your first daily quest & bounty."));
                                    player.sendMessage(plugin.color("&8[&bHermes AI&8] &7Type &e/aichat <message> &7to talk with me privately, or ask me questions directly in public chat!"));
                                    player.sendMessage(plugin.color("&8&m────────────────────────────────────────"));
                                }
                            }
                        }.runTaskLater(plugin, 40L);

                    } else {
                        // Returning player experience
                        String msg = plugin.getConfig().getString("welcome.message",
                                "&8[&bDTEmpire&8] &rWelcome back, &f{player}&r! Type &e/aidaily&r for today's quest.")
                                .replace("{player}", player.getName());
                        player.sendMessage(plugin.color(msg));
                        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
                    }
                }
            }.runTaskLater(plugin, delay);
        }

        // Instant Discord update on join (if tracking enabled)
        if (plugin.isTrackingEnabled()) {
            plugin.getTrackingReporter().postNow();
        }
    }

    private void spawnWelcomeFirework(Player player) {
        try {
            Firework fw = player.getWorld().spawn(player.getLocation().add(0, 1, 0), Firework.class);
            FireworkMeta meta = fw.getFireworkMeta();
            meta.setPower(1);
            meta.addEffect(FireworkEffect.builder()
                    .with(FireworkEffect.Type.BALL_LARGE)
                    .withColor(Color.ORANGE, Color.YELLOW, Color.AQUA)
                    .withFade(Color.WHITE)
                    .trail(true)
                    .flicker(true)
                    .build());
            fw.setFireworkMeta(meta);
        } catch (Exception ignored) {
        }
    }

    private void giveStarterKit(Player player) {
        player.getInventory().addItem(
                new ItemStack(Material.COOKED_BEEF, 16),
                new ItemStack(Material.STONE_PICKAXE, 1),
                new ItemStack(Material.STONE_AXE, 1),
                new ItemStack(Material.OAK_LOG, 16)
        );
        player.sendMessage(plugin.color("&8[&bStarter Kit&8] &aYou received 16x Cooked Beef, Starter Tools, and 16x Logs to begin your journey!"));
    }

    @EventHandler
    public void onLeave(PlayerQuitEvent event) {
        plugin.getManager().onLeave(event.getPlayer());

        // Instant Discord update on leave
        if (plugin.isTrackingEnabled()) {
            plugin.getTrackingReporter().postNow();
        }
    }
}
