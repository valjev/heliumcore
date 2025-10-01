package ru.helium.core.commands;

import ru.helium.core.HeliumCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DecimalFormat;

public class StatusCommand implements CommandExecutor {

    private final HeliumCore plugin;
    private final DecimalFormat tpsFormat = new DecimalFormat("#.##");

    private final String TELEGRAM_API_URL = "http://127.0.0.1:5000/sup";
    private final String SITE_URL = "https://heliumgame.ru";

    public StatusCommand(HeliumCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        double tps = Bukkit.getServer().getTPS()[0];
        String version = plugin.getDescription().getVersion();

        StatusCheckResult api = checkUrlWithPing(TELEGRAM_API_URL);
        StatusCheckResult site = checkUrlWithPing(SITE_URL);

        NamedTextColor tpsColor = tps >= 18 ? NamedTextColor.GREEN :
                tps >= 15 ? NamedTextColor.YELLOW :
                        NamedTextColor.RED;

        Component msg = Component.empty()
                .append(Component.text("» TPS сервера: ", NamedTextColor.GRAY))
                .append(Component.text(tpsFormat.format(tps), tpsColor)).append(Component.newline())

                .append(Component.text("» Статус API: ", NamedTextColor.GRAY))
                .append(Component.text(api.online ? "Доступен" : "Недоступен", api.online ? NamedTextColor.GREEN : NamedTextColor.RED))
                .append(Component.text(" (", NamedTextColor.DARK_GRAY))
                .append(Component.text(codeColor(api.code) + api.code + ""))
                .append(Component.text(", "))
                .append(Component.text(pingColor(api.ping) + api.ping + "мс"))
                .append(Component.text(")", NamedTextColor.DARK_GRAY)).append(Component.newline())

                .append(Component.text("» Статус сайта: ", NamedTextColor.GRAY))
                .append(Component.text(site.online ? "Доступен" : "Недоступен", site.online ? NamedTextColor.GREEN : NamedTextColor.RED))
                .append(Component.text(" (", NamedTextColor.DARK_GRAY))
                .append(Component.text(codeColor(site.code) + site.code + ""))
                .append(Component.text(", "))
                .append(Component.text(pingColor(site.ping) + site.ping + "мс"))
                .append(Component.text(")", NamedTextColor.DARK_GRAY)).append(Component.newline())

                .append(Component.text("» Ядро: ", NamedTextColor.GRAY))
                .append(Component.text("HeliumCore", NamedTextColor.AQUA))
                .append(Component.text(" " + version + " ", NamedTextColor.WHITE))
                .append(Component.text("(" + Bukkit.getBukkitVersion() + ")", NamedTextColor.WHITE));

        sender.sendMessage(msg);
        return true;
    }

    private StatusCheckResult checkUrlWithPing(String urlStr) {
        long start = System.nanoTime();
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(urlStr).openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(1000);
            connection.setReadTimeout(1000);
            int code = connection.getResponseCode();
            long ping = (System.nanoTime() - start) / 1_000_000;
            return new StatusCheckResult(code >= 200 && code < 400, code, ping);
        } catch (IOException e) {
            return new StatusCheckResult(false, -1, -1);
        }
    }

    private String codeColor(int code) {
        if (code == -1) return "§7";
        if (code >= 200 && code < 300) return "§a";
        if (code >= 300 && code < 400) return "§e";
        return "§c";
    }

    private String pingColor(long ping) {
        if (ping == -1) return "§7";
        if (ping < 150) return "§a";
        if (ping < 500) return "§e";
        return "§c";
    }

    private static class StatusCheckResult {
        final boolean online;
        final int code;
        final long ping;

        StatusCheckResult(boolean online, int code, long ping) {
            this.online = online;
            this.code = code;
            this.ping = ping;
        }
    }
}