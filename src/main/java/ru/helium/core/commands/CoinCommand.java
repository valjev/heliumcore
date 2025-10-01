package ru.helium.core.commands;

import ru.helium.core.database.HeliumDatabase;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;

public class CoinCommand implements CommandExecutor {

    private final HeliumDatabase database;
    private double rateUSD = 0.1;

    public CoinCommand(HeliumDatabase database) {
        this.database = database;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {

        if (args.length == 0 || args[0].equalsIgnoreCase("balance")) {
            showBalance(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("help")) {
            sendHelp(sender);
            return true;
        }

        if (sub.equals("economy")) {
            if (args.length >= 3 && args[1].equalsIgnoreCase("set")) {
                if (!sender.isOp()) {
                    sender.sendMessage(Component.text("Нет прав для установки курса экономики.", NamedTextColor.RED));
                    return true;
                }
                try {
                    double newRate = Double.parseDouble(args[2]);
                    if (newRate <= 0) {
                        sender.sendMessage(Component.text("Курс должен быть положительным числом.", NamedTextColor.RED));
                        return true;
                    }
                    rateUSD = newRate;
                    sender.sendMessage(Component.text("Курс HeliumCoin установлен: 1 HC = " + newRate + " USD", NamedTextColor.GREEN));
                } catch (NumberFormatException e) {
                    sender.sendMessage(Component.text("Неверный формат числа.", NamedTextColor.RED));
                }
                return true;
            }
            sender.sendMessage(Component.text("Использование: /coin economy set <курс>", NamedTextColor.YELLOW));
            return true;
        }

        if (sub.equals("give")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("Передавать монеты могут только игроки.", NamedTextColor.RED));
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage(Component.text("Использование: /coin give <ник> <кол-во>", NamedTextColor.YELLOW));
                return true;
            }
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(Component.text("Игрок не найден или оффлайн.", NamedTextColor.RED));
                return true;
            }
            int amount;
            try {
                amount = Integer.parseInt(args[2]);
                if (amount <= 0) {
                    sender.sendMessage(Component.text("Количество должно быть положительным.", NamedTextColor.RED));
                    return true;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("Неверный формат числа.", NamedTextColor.RED));
                return true;
            }

            try {
                int senderBalance = database.getBalance(player.getUniqueId());
                if (senderBalance < amount) {
                    sender.sendMessage(Component.text("У вас недостаточно HeliumCoin для передачи.", NamedTextColor.RED));
                    return true;
                }
                database.setBalance(player.getUniqueId(), senderBalance - amount);

                int targetBalance = database.getBalance(target.getUniqueId());
                database.setBalance(target.getUniqueId(), targetBalance + amount);

                sender.sendMessage(Component.text("Вы передали " + amount + " HC игроку " + target.getName(), NamedTextColor.GREEN));
                target.sendMessage(Component.text("Игрок " + player.getName() + " передал вам " + amount + " HC.", NamedTextColor.GREEN));
            } catch (SQLException e) {
                sender.sendMessage(Component.text("Ошибка при обработке базы данных.", NamedTextColor.RED));
                e.printStackTrace();
            }
            return true;
        }

        if (sub.equals("set") || sub.equals("take")) {
            if (!sender.isOp() && !(sender instanceof org.bukkit.command.ConsoleCommandSender)) {
                sender.sendMessage(Component.text("Недостаточно прав.", NamedTextColor.RED));
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage(Component.text("Использование: /coin " + sub + " <ник> <кол-во>", NamedTextColor.YELLOW));
                return true;
            }
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(Component.text("Игрок не найден или оффлайн.", NamedTextColor.RED));
                return true;
            }
            int amount;
            try {
                amount = Integer.parseInt(args[2]);
                if (amount < 0) {
                    sender.sendMessage(Component.text("Количество должно быть неотрицательным.", NamedTextColor.RED));
                    return true;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("Неверный формат числа.", NamedTextColor.RED));
                return true;
            }
            try {
                int currentBalance = database.getBalance(target.getUniqueId());
                if (sub.equals("set")) {
                    database.setBalance(target.getUniqueId(), amount);
                    sender.sendMessage(Component.text("Баланс игрока " + target.getName() + " установлен в " + amount + " HC.", NamedTextColor.GREEN));
                } else {
                    int newBalance = Math.max(0, currentBalance - amount);
                    database.setBalance(target.getUniqueId(), newBalance);
                    sender.sendMessage(Component.text("У игрока " + target.getName() + " снято " + amount + " HC. Текущий баланс: " + newBalance, NamedTextColor.GREEN));
                }
            } catch (SQLException e) {
                sender.sendMessage(Component.text("Ошибка при работе с базой данных.", NamedTextColor.RED));
                e.printStackTrace();
            }
            return true;
        }

        sender.sendMessage(Component.text("Неизвестная команда. Используйте /coin help", NamedTextColor.RED));
        return true;
    }

    private void showBalance(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Команду может выполнять только игрок.", NamedTextColor.RED));
            return;
        }
        try {
            int balance = database.getBalance(player.getUniqueId());
            double rub = balance * rateUSD * 80;
            double eur = balance * rateUSD * 0.9;
            player.sendMessage(Component.text("Баланс: " + balance + " HC", NamedTextColor.YELLOW));
            player.sendMessage(Component.text(String.format("≈ %.2f RUB, %.2f USD, %.2f EUR", rub, balance * rateUSD, eur), NamedTextColor.GRAY));
        } catch (SQLException e) {
            player.sendMessage(Component.text("Ошибка при чтении баланса.", NamedTextColor.RED));
            e.printStackTrace();
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("≡ Команды HeliumCoin (HC):", NamedTextColor.YELLOW));
        send(sender, "/coin (/c, /money)", "Показать баланс и конвертацию");
        send(sender, "/coin give <ник> <кол-во>", "Передать монеты другому игроку");
    }

    private void send(CommandSender sender, String cmd, String desc) {
        TextComponent line = Component.text()
                .append(Component.text(" " + cmd, NamedTextColor.WHITE))
                .append(Component.text(" - ", NamedTextColor.DARK_GRAY))
                .append(Component.text(desc, NamedTextColor.GRAY))
                .build();
        sender.sendMessage(line);
    }
}