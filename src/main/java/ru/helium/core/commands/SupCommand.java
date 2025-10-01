package ru.helium.core.commands;

import ru.helium.core.utils.TelegramSender;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class SupCommand implements CommandExecutor {

    private static final long COOLDOWN_MILLIS = 100_000; // 100 секунд
    private final Map<String, Long> cooldowns = new HashMap<>();

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {

        String playerName = sender.getName();
        long now = System.currentTimeMillis();

        if (cooldowns.containsKey(playerName)) {
            long lastUsed = cooldowns.get(playerName);
            long timeLeft = COOLDOWN_MILLIS - (now - lastUsed);
            if (timeLeft > 0) {
                sender.sendMessage(Component.text("Пожалуйста, подождите " + (timeLeft / 1000) + " сек. перед повторной отправкой.", NamedTextColor.RED));
                return true;
            }
        }

        if (args.length == 0) {
            sender.sendMessage(Component.text("Использование: /sup (/s, /report) <сообщение>", NamedTextColor.RED));
            return true;
        }

        String message = String.join(" ", args);

        Component formattedMessage = Component.text()
                .append(Component.text("[Репорт] ", NamedTextColor.RED))
                .append(Component.text("Сообщение от игрока ", NamedTextColor.GRAY))
                .append(Component.text(playerName, NamedTextColor.WHITE))
                .append(Component.text(": ", NamedTextColor.GRAY))
                .append(Component.text(message, NamedTextColor.WHITE))
                .build();

        Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.hasPermission("helium.mod") || p.hasPermission("helium.admin"))
                .forEach(p -> p.sendMessage(formattedMessage));

        Bukkit.getConsoleSender().sendMessage(formattedMessage);

        TelegramSender.sendSup(playerName, message);

        cooldowns.put(playerName, now);

        sender.sendMessage(Component.text("Сообщение отправлено стражам. Спасибо за обратную связь!", NamedTextColor.GREEN));
        return true;
    }
}