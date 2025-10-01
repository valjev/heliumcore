package ru.helium.core.gui;

import ru.helium.core.HeliumCore;
import ru.helium.core.utils.Rank;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import javax.swing.text.StyleContext;

public class TablistManager {

    private final HeliumCore plugin;

    public TablistManager(HeliumCore plugin) {
        this.plugin = plugin;
        startUpdating();
    }

    private void startUpdating() {
        new BukkitRunnable() {
            @Override
            public void run() {
                updateAllPlayersTab();
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void updateAllPlayersTab() {
        int online = Bukkit.getOnlinePlayers().size();
        int maxPlayers = Bukkit.getMaxPlayers();
        double tps = getTPS();
        String version = plugin.getPluginMeta().getVersion();

        for (Player player : Bukkit.getOnlinePlayers()) {
            Component header = buildHeader();
            Component footer = buildFooter(online, maxPlayers, tps, version);

            String prefix;
            try {
                Rank rank = plugin.getDatabase().getRank(player.getUniqueId());
                prefix = rank.getPrefix();
            } catch (Exception e) {
                prefix = "";
            }

            player.displayName(Component.text(prefix + player.getName()));
            player.playerListName(Component.text(prefix + player.getName()));
            player.sendPlayerListHeaderAndFooter(header, footer);
        }
    }

    private Component buildHeader() {
        return Component.text()
                .append(Component.text(" "))
                .append(Component.newline())
                .append(Component.text("HELIUM", NamedTextColor.YELLOW).decorate(TextDecoration.BOLD))
                .append(Component.newline())
                .append(Component.text("    Тестовый сервер   ", NamedTextColor.GRAY))
                .append(Component.newline())
                .append(Component.text(" "))
                .build();
    }

    private Component buildFooter(int online, int max, double tps, String version) {
        NamedTextColor tpsColor = getTPSColor(tps);
        return Component.text()
                .append(Component.text(" "))
                .append(Component.newline())
                .append(Component.text("Онлайн: ", NamedTextColor.GRAY))
                .append(Component.text(String.valueOf(online), NamedTextColor.WHITE))
                .append(Component.text("/", NamedTextColor.GRAY))
                .append(Component.text(String.valueOf(max), NamedTextColor.WHITE))
                .append(Component.newline())
                .append(Component.text("Версия: ", NamedTextColor.GRAY))
                .append(Component.text(version, NamedTextColor.WHITE))
                .append(Component.newline())
                .append(Component.text("TPS: ", NamedTextColor.GRAY))
                .append(Component.text(String.format("%.2f", tps), tpsColor))
                .append(Component.newline())
                .append(Component.text(" "))
                .build();
    }

    private double getTPS() {
        return Bukkit.getServer().getTPS()[0];
    }

    private NamedTextColor getTPSColor(double tps) {
        if (tps >= 18.0) return NamedTextColor.GREEN;
        if (tps >= 14.0) return NamedTextColor.YELLOW;
        return NamedTextColor.RED;
    }
}