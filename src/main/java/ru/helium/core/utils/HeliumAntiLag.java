package ru.helium.core.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class HeliumAntiLag implements Listener {

    private final JavaPlugin plugin;
    private final int maxMobsPerWorld = 50; // Лимит мобов в мире

    public HeliumAntiLag(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        plugin.getLogger().info("HeliumAntiLag инициализирован.");

        final long warningBeforeMs = 60 * 1000;
        final long intervalMs = 10 * 60 * 1000;

        final long[] lastRun = {System.currentTimeMillis()};
        final boolean[] warned = {false};

        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                long elapsed = now - lastRun[0];

                if (!warned[0] && elapsed >= (intervalMs - warningBeforeMs)) {
                    Component warn = Component.text("[Helium] ", NamedTextColor.AQUA)
                            .append(Component.text("Внимание! Очистка дропов через минуту.", NamedTextColor.RED));
                    Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(warn));
                    warned[0] = true;
                }

                if (elapsed >= intervalMs) {
                    runManualOptimization();
                    lastRun[0] = now;
                    warned[0] = false;
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        Chunk chunk = event.getLocation().getChunk();

        boolean hasPlayers = false;
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof Player) {
                hasPlayers = true;
                break;
            }
        }

        if (!hasPlayers) {
            event.setCancelled(true);
            return;
        }

        World world = event.getLocation().getWorld();
        if (world == null) return;

        int mobCount = 0;
        for (Entity entity : world.getEntities()) {
            if (entity instanceof Creature) {
                mobCount++;
                if (mobCount >= maxMobsPerWorld) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    public void runManualOptimization() {
        int removedItems = 0;
        int removedMobs = 0;
        int unloadedChunks = 0;

        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Item) {
                    entity.remove();
                    removedItems++;
                } else if (entity instanceof Monster) {
                    entity.remove();
                    removedMobs++;
                }
            }

            world.setViewDistance(6);

            for (Chunk chunk : world.getLoadedChunks()) {
                if (!chunk.isForceLoaded() && chunk.getEntities().length == 0) {
                    if (chunk.unload(true)) {
                        unloadedChunks++;
                    }
                }
            }
        }

        Component msg = Component.text("[Helium] ", NamedTextColor.AQUA)
                .append(Component.text("Очищено предметов: ", NamedTextColor.WHITE))
                .append(Component.text(String.valueOf(removedItems), NamedTextColor.GREEN))
                .append(Component.text(", мобов: ", NamedTextColor.WHITE))
                .append(Component.text(String.valueOf(removedMobs), NamedTextColor.GREEN))
                .append(Component.text(", выгружено чанков: ", NamedTextColor.WHITE))
                .append(Component.text(String.valueOf(unloadedChunks), NamedTextColor.GREEN));

        Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(msg));
    }
}