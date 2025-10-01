package ru.helium.core.commands;

import ru.helium.core.database.WhitelistDatabase;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.UUID;

public class WhitelistCommand implements CommandExecutor {

    private final WhitelistDatabase db;
    public WhitelistCommand(WhitelistDatabase db) {
        this.db = db;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender.isOp() || sender.hasPermission("helium.admin") || sender instanceof RemoteConsoleCommandSender)) {
            sender.sendMessage(Component.text("У вас нет прав на использование этой команды.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(Component.text("Список аргументов:", NamedTextColor.WHITE));
            sender.sendMessage(Component.text("/hwl add <игрок> — добавить", NamedTextColor.GRAY));
            sender.sendMessage(Component.text("/hwl remove <игрок> — удалить", NamedTextColor.GRAY));
            return true;
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("add") || args[0].equalsIgnoreCase("remove"))) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
            UUID uuid = target.getUniqueId();

            if (args[0].equalsIgnoreCase("add")) {
                if (db.add(uuid)) {
                    sender.sendMessage(Component.text("Игрок добавлен в вайтлист.", NamedTextColor.GREEN));
                } else {
                    sender.sendMessage(Component.text("Игрок уже в вайтлисте.", NamedTextColor.DARK_GRAY));
                }
            } else {
                if (db.remove(uuid)) {
                    sender.sendMessage(Component.text("Игрок удалён из вайтлиста.", NamedTextColor.RED));

                    Player online = Bukkit.getPlayer(uuid);
                    if (online != null && online.isOnline()) {
                        online.kick(Component.text("Ой! Без верификации не пускаем :(")
                                .color(NamedTextColor.RED)
                                .append(Component.newline())
                                .append(Component.text("Загляни в Telegram-бота: ", NamedTextColor.GRAY))
                                .append(Component.text("@HeliumGameBot", NamedTextColor.AQUA))
                        );
                    }

                } else {
                    sender.sendMessage(Component.text("Игрока не было в вайтлисте.", NamedTextColor.RED));
                }
            }
            return true;
        }

        sender.sendMessage(Component.text("Неизвестный аргумент. Используйте /hwl, /hwl <add|remove>", NamedTextColor.RED));
        return true;
    }
}