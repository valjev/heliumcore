package ru.helium.core.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.plugin.Plugin;

import java.util.*;

public class TPACommand implements CommandExecutor {

    private final Map<UUID, PendingRequest> requests = new HashMap<>();
    private final Plugin plugin;

    public TPACommand(Plugin plugin) {
        this.plugin = plugin;
        startExpiryChecker();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Команда доступна только игрокам.");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(Component.text("Использование: /tpa <игрок>", NamedTextColor.GRAY));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "accept" -> acceptTPA(player);
            case "decline" -> declineTPA(player);
            default -> sendTPA(player, args[0]);
        }

        return true;
    }

    private void sendTPA(Player sender, String targetName) {
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null || !target.isOnline()) {
            sender.sendMessage(Component.text("Игрок не найден.", NamedTextColor.RED));
            return;
        }

        if (target == sender) {
            sender.sendMessage(Component.text("Нельзя отправить запрос самому себе.", NamedTextColor.RED));
            return;
        }

        requests.put(target.getUniqueId(), new PendingRequest(sender.getUniqueId(), System.currentTimeMillis() + 30_000));

        Component message = Component.text("Игрок ", NamedTextColor.WHITE)
                .append(Component.text(sender.getName(), NamedTextColor.AQUA))
                .append(Component.text(" хочет телепортироваться к вам. \n", NamedTextColor.WHITE))
                .append(Component.text("[Принять]", NamedTextColor.GREEN)
                        .clickEvent(ClickEvent.runCommand("/tpa accept")))
                .append(Component.space())
                .append(Component.text("[Отклонить]", NamedTextColor.RED)
                        .clickEvent(ClickEvent.runCommand("/tpa decline")));

        target.sendMessage(message);
        sender.sendMessage(Component.text("Запрос отправлен игроку " + target.getName(), NamedTextColor.GREEN));
    }

    private void acceptTPA(Player target) {
        PendingRequest request = requests.remove(target.getUniqueId());
        if (request == null) {
            target.sendMessage(Component.text("Нет активных запросов на телепортацию.", NamedTextColor.GRAY));
            return;
        }

        Player sender = Bukkit.getPlayer(request.sender());
        if (sender == null || !sender.isOnline()) {
            target.sendMessage(Component.text("Игрок не в сети.", NamedTextColor.RED));
            return;
        }

        sender.teleport(target.getLocation());
        sender.sendMessage(Component.text("Вы телепортированы к " + target.getName(), NamedTextColor.GRAY));
        target.sendMessage(Component.text("Вы приняли запрос от " + sender.getName(), NamedTextColor.GRAY));
    }

    private void declineTPA(Player target) {
        PendingRequest request = requests.remove(target.getUniqueId());
        if (request == null) {
            target.sendMessage(Component.text("Нет активных запросов на телепортацию.", NamedTextColor.GRAY));
            return;
        }

        Player sender = Bukkit.getPlayer(request.sender());
        if (sender != null && sender.isOnline()) {
            sender.sendMessage(Component.text("Игрок " + target.getName() + " отклонил ваш запрос.", NamedTextColor.RED));
        }

        target.sendMessage(Component.text("Вы отклонили запрос.", NamedTextColor.GRAY));
    }

    private void startExpiryChecker() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                Iterator<Map.Entry<UUID, PendingRequest>> iterator = requests.entrySet().iterator();

                while (iterator.hasNext()) {
                    Map.Entry<UUID, PendingRequest> entry = iterator.next();
                    if (entry.getValue().expiresAt() < now) {
                        Player sender = Bukkit.getPlayer(entry.getValue().sender());
                        Player target = Bukkit.getPlayer(entry.getKey());

                        if (sender != null && sender.isOnline()) {
                            sender.sendMessage(Component.text("Запрос на телепортацию истёк.", NamedTextColor.GRAY));
                        }

                        if (target != null && target.isOnline()) {
                            target.sendMessage(Component.text("Запрос от " + (sender != null ? sender.getName() : "игрока") + " истёк.", NamedTextColor.GRAY));
                        }

                        iterator.remove();
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private record PendingRequest(UUID sender, long expiresAt) {
    }
}