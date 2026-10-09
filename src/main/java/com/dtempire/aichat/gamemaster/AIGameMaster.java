package com.dtempire.aichat.gamemaster;

import com.dtempire.aichat.DTEmpireAIChatPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Autonomous AI Event Director and Game Master. Handles dynamic server events. */
public class AIGameMaster {

    private final DTEmpireAIChatPlugin plugin;
    private final Random random = new Random();
    private BukkitTask eventTask;

    public AIGameMaster(DTEmpireAIChatPlugin plugin) {
        this.plugin = plugin;
        startEventScheduler();
    }

    public void startEventScheduler() {
        if (eventTask != null) eventTask.cancel();

        int intervalMinutes = plugin.getConfig().getInt("gamemaster.event-interval-minutes", 45);
        long ticks = Math.max(10, intervalMinutes) * 1200L;

        eventTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (Bukkit.getOnlinePlayers().isEmpty()) return;
                triggerRandomEvent();
            }
        }.runTaskTimer(plugin, ticks, ticks);
    }

    public void stopEventScheduler() {
        if (eventTask != null) {
            eventTask.cancel();
            eventTask = null;
        }
    }

    public void triggerRandomEvent() {
        int roll = random.nextInt(3);
        if (roll == 0) {
            triggerMeteorSupplyDrop(null);
        } else if (roll == 1) {
            triggerBloodMoon();
        } else {
            triggerGoldenHour();
        }
    }

    /** Spawns a meteor supply drop near a player or specific location. */
    public Location triggerMeteorSupplyDrop(Location targetLoc) {
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (players.isEmpty() && targetLoc == null) return null;

        Location origin = targetLoc != null ? targetLoc : players.get(random.nextInt(players.size())).getLocation();
        World world = origin.getWorld();
        if (world == null) return null;

        // Offset 100 to 250 blocks away
        int offsetX = (random.nextBoolean() ? 1 : -1) * (100 + random.nextInt(150));
        int offsetZ = (random.nextBoolean() ? 1 : -1) * (100 + random.nextInt(150));
        int dropX = origin.getBlockX() + offsetX;
        int dropZ = origin.getBlockZ() + offsetZ;
        int dropY = world.getHighestBlockYAt(dropX, dropZ) + 1;

        Location dropLoc = new Location(world, dropX, dropY, dropZ);
        Block block = dropLoc.getBlock();
        block.setType(Material.CHEST);

        if (block.getState() instanceof Chest chest) {
            Inventory inv = chest.getInventory();
            inv.addItem(new ItemStack(Material.DIAMOND, 4 + random.nextInt(6)));
            inv.addItem(new ItemStack(Material.GOLDEN_APPLE, 2 + random.nextInt(3)));
            inv.addItem(new ItemStack(Material.IRON_BLOCK, 2 + random.nextInt(4)));
            inv.addItem(new ItemStack(Material.EXPERIENCE_BOTTLE, 16));
            inv.addItem(new ItemStack(Material.ENDER_PEARL, 8));
            if (random.nextBoolean()) {
                inv.addItem(new ItemStack(Material.TOTEM_OF_UNDYING, 1));
            }
        }

        // Visual effects
        world.strikeLightningEffect(dropLoc);
        world.spawnParticle(Particle.FLAME, dropLoc.clone().add(0.5, 1, 0.5), 50, 0.5, 1.0, 0.5, 0.05);

        // Server announcement
        Bukkit.broadcastMessage(plugin.color("&8&m────────────────────────────────────────"));
        Bukkit.broadcastMessage(plugin.color("&6&l[GAME MASTER EVENT] &eCelestial Meteor Supply Drop!"));
        Bukkit.broadcastMessage(plugin.color("&fA meteor has struck near coordinates: &aX: " + dropX + " &7| &aY: " + dropY + " &7| &aZ: " + dropZ));
        Bukkit.broadcastMessage(plugin.color("&7Race to the crash site to secure the cosmic supplies!"));
        Bukkit.broadcastMessage(plugin.color("&8&m────────────────────────────────────────"));

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.7f, 1.2f);
        }

        return dropLoc;
    }

    /** Triggers a 10-minute Blood Moon event. */
    public void triggerBloodMoon() {
        for (World world : Bukkit.getWorlds()) {
            world.setTime(14000); // midnight
            world.setStorm(true);
            world.setThundering(true);
        }

        // Buff nearby monsters
        for (World world : Bukkit.getWorlds()) {
            for (org.bukkit.entity.Entity entity : world.getEntities()) {
                if (entity instanceof Monster monster) {
                    monster.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 600, 1));
                    monster.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 20 * 600, 0));
                }
            }
        }

        Bukkit.broadcastMessage(plugin.color("&8&m────────────────────────────────────────"));
        Bukkit.broadcastMessage(plugin.color("&4&l[GAME MASTER EVENT] &cThe Blood Moon Rises!"));
        Bukkit.broadcastMessage(plugin.color("&fDarkness has enveloped the realm. Monsters grow furious and swift!"));
        Bukkit.broadcastMessage(plugin.color("&eDefeating monsters during the Blood Moon yields double experience!"));
        Bukkit.broadcastMessage(plugin.color("&8&m────────────────────────────────────────"));

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendTitle(plugin.color("&4&lBLOOD MOON"), plugin.color("&cSurvive the nocturnal surge!"), 10, 80, 20);
            p.playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 0.8f);
        }
    }

    /** Triggers a 15-minute Golden Hour event. */
    public void triggerGoldenHour() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 20 * 900, 1));
            p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 20 * 900, 0));
            p.sendTitle(plugin.color("&6&lGOLDEN HOUR"), plugin.color("&eAbundance smiles upon the realm!"), 10, 80, 20);
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        }

        Bukkit.broadcastMessage(plugin.color("&8&m────────────────────────────────────────"));
        Bukkit.broadcastMessage(plugin.color("&6&l[GAME MASTER EVENT] &eGolden Hour Has Begun!"));
        Bukkit.broadcastMessage(plugin.color("&fAll active adventurers have been blessed with &aHaste II &fand &aRegeneration&f for 15 minutes!"));
        Bukkit.broadcastMessage(plugin.color("&7Go forth and build, mine, and conquer!"));
        Bukkit.broadcastMessage(plugin.color("&8&m────────────────────────────────────────"));
    }

    public void broadcastLore(String message) {
        Bukkit.broadcastMessage(plugin.color("&8[&bGame Master&8] &f" + message));
    }
}
