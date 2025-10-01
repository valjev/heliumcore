package ru.helium.core.commands;

import ru.helium.core.database.HeliumDatabase;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.util.UUID;

public class HomeCommands implements CommandExecutor {

    private final HeliumDatabase heliumDatabase;

    public HomeCommands(HeliumDatabase heliumDatabase) {
        this.heliumDatabase = heliumDatabase;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Команда доступна только игрокам.", NamedTextColor.RED));
            return true;
        }

        UUID uuid = player.getUniqueId();

        if (label.equalsIgnoreCase("home")) {
            try {
                Location home = heliumDatabase.getHome(uuid);
                if (home == null) {
                    player.sendMessage(Component.text("Дом не установлен. Используйте команду /sethome", NamedTextColor.RED));
                    return true;
                }
                player.teleport(home);
                player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.0f);
                player.sendMessage(Component.text("Вы телепортированы домой!", NamedTextColor.GRAY));
            } catch (SQLException e) {
                player.sendMessage(Component.text("Ошибка при получении дома из базы данных.", NamedTextColor.RED));
                e.printStackTrace();
            }
            return true;
        }

        if (label.equalsIgnoreCase("sethome")) {
            if (args.length == 0) {
                try {
                    heliumDatabase.setHome(uuid, player.getLocation());
                    player.sendMessage(Component.text("Дом установлен!", NamedTextColor.GREEN));
                } catch (SQLException e) {
                    player.sendMessage(Component.text("Ошибка при сохранении дома в базе данных.", NamedTextColor.RED));
                    e.printStackTrace();
                }
                return true;
            }

            // Проверка прав администратора на установку дома другим игрокам
            if (!sender.hasPermission("helium.admin")) {
                sender.sendMessage(Component.text("У вас нет прав на установку дома для других игроков.", NamedTextColor.RED));
                return true;
            }

            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage(Component.text("Игрок " + args[0] + " не найден или не в сети.", NamedTextColor.RED));
                return true;
            }

            try {
                heliumDatabase.setHome(target.getUniqueId(), player.getLocation());
                sender.sendMessage(Component.text("Дом для " + target.getName() + " установлен.", NamedTextColor.GREEN));
                target.sendMessage(Component.text("Администратор установил вам новый дом.", NamedTextColor.GRAY));
            } catch (SQLException e) {
                sender.sendMessage(Component.text("Ошибка при сохранении дома в базе данных.", NamedTextColor.RED));
                e.printStackTrace();
            }

            return true;
        }

        return false;
    }
}