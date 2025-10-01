package ru.helium.core.commands;

import ru.helium.core.HeliumCore;
import ru.helium.core.utils.Rank;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;

public class HeliumCommands implements CommandExecutor {

    private final HeliumCore plugin;

    public HeliumCommands(HeliumCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {

        if (args.length == 0) {
            sender.sendMessage(Component.text("Использование: /helium <reload|restart|optime|give|alogs>", NamedTextColor.RED));
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                if (!sender.hasPermission("helium.admin") && !sender.isOp()) {
                    sender.sendMessage(Component.text("У вас нет прав на выполнение этой команды.", NamedTextColor.RED));
                    return true;
                }
                plugin.reloadConfig();
                sender.sendMessage(Component.text("Конфиг плагина перезагружен.", NamedTextColor.GREEN));
                plugin.getLogger().info("[HeliumCore] Конфигурация перезагружена через /helium reload.");
                return true;
            }
            case "restart" -> {
                if (!sender.hasPermission("helium.admin") && !sender.isOp()) {
                    sender.sendMessage(Component.text("У вас нет прав на выполнение этой команды.", NamedTextColor.RED));
                    return true;
                }
                sender.sendMessage(Component.text("Сервер перезапускается...", NamedTextColor.RED));
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.kick(Component.text("Сервер перезапускается...\nПерезайдите через минуту.", NamedTextColor.RED));
                }
                plugin.getServer().getScheduler().runTaskLater(plugin, plugin.getServer()::shutdown, 60L);
                return true;
            }
            case "optime" -> {
                if (!sender.hasPermission("helium.admin") && !sender.isOp()) {
                    sender.sendMessage(Component.text("У вас нет прав на выполнение этой команды.", NamedTextColor.RED));
                    return true;
                }
                plugin.getAntiLag().runManualOptimization();
                sender.sendMessage(Component.text("Оптимизация произведена вручную.", NamedTextColor.GREEN));
                return true;
            }
            case "give" -> {
                if (!sender.isOp()) {
                    sender.sendMessage(Component.text("Только OP может выдавать роли.", NamedTextColor.RED));
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Использование: /helium give <игрок> <роль>", NamedTextColor.RED));
                    return true;
                }

                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage(Component.text("Игрок не найден.", NamedTextColor.RED));
                    return true;
                }

                Rank rank;
                try {
                    rank = Rank.valueOf(args[2].toUpperCase());
                } catch (IllegalArgumentException e) {
                    sender.sendMessage(Component.text("Роль не найдена. Возможные роли: игрок, страж, админ", NamedTextColor.RED));
                    return true;
                }

                try {
                    plugin.getDatabase().setRank(target.getUniqueId(), rank);
                    plugin.getRankManager().updatePlayerRank(target);
                    target.recalculatePermissions();
                    target.updateCommands();

                    sender.sendMessage(Component.text("Роль \"" + rank.getRussianName() + "\" выдана игроку " + target.getName(), NamedTextColor.GREEN));
                    target.sendMessage(Component.text("Вам выдана роль: " + rank.getRussianName(), NamedTextColor.GREEN));
                } catch (Exception e) {
                    sender.sendMessage(Component.text("Ошибка при сохранении.", NamedTextColor.RED));
                }
                return true;
            }
            case "alogs" -> {
                if (!sender.isOp()) {
                    sender.sendMessage(Component.text("Команда доступна только операторам.", NamedTextColor.RED));
                    return true;
                }

                File logFile = new File(plugin.getDataFolder(), "logs/modlogs.txt");

                if (args.length >= 2 && args[1].equalsIgnoreCase("clear")) {
                    if (logFile.exists()) {
                        if (logFile.delete()) {
                            sender.sendMessage(Component.text("Лог модерации очищен.", NamedTextColor.GREEN));
                        } else {
                            sender.sendMessage(Component.text("Не удалось очистить лог.", NamedTextColor.RED));
                        }
                    } else {
                        sender.sendMessage(Component.text("Лог пуст или отсутствует.", NamedTextColor.GRAY));
                    }
                    return true;
                }

                int page = 1;
                if (args.length >= 2) {
                    try {
                        page = Integer.parseInt(args[1]);
                        if (page < 1) page = 1;
                    } catch (NumberFormatException ignored) {
                    }
                }

                if (!logFile.exists()) {
                    sender.sendMessage(Component.text("Лог пуст или отсутствует.", NamedTextColor.GRAY));
                    return true;
                }

                try {
                    if (!logFile.exists()) {
                        sender.sendMessage(Component.text("Файл логов не найден.", NamedTextColor.RED));
                        return true;
                    }

                    List<String> lines = Files.readAllLines(logFile.toPath());
                    if (lines.isEmpty()) {
                        sender.sendMessage(Component.text("Лог пуст.", NamedTextColor.GRAY));
                        return true;
                    }

                    int pageSize = 6;
                    int totalPages = (lines.size() + pageSize - 1) / pageSize;

                    if (page < 1) page = 1;
                    if (page > totalPages) page = totalPages;

                    int from = (page - 1) * pageSize;
                    int to = Math.min(from + pageSize, lines.size());

                    sender.sendMessage(Component.text("====== Логи модерации (стр. " + page + "/" + totalPages + ") ======", NamedTextColor.GREEN));
                    for (int i = from; i < to; i++) {
                        sender.sendMessage(Component.text(lines.get(i), NamedTextColor.GRAY));
                    }

                    Component nav = Component.empty();

                    if (page > 1) {
                        nav = nav.append(
                                Component.text("[Назад]", NamedTextColor.AQUA)
                                        .clickEvent(ClickEvent.runCommand("/helium alogs " + (page - 1)))
                        );
                    } else {
                        nav = nav.append(Component.text("[Назад]", NamedTextColor.DARK_GRAY));
                    }

                    nav = nav.append(Component.space());

                    if (page < totalPages) {
                        nav = nav.append(
                                Component.text("[Далее]", NamedTextColor.AQUA)
                                        .clickEvent(ClickEvent.runCommand("/helium alogs " + (page + 1)))
                        );
                    } else {
                        nav = nav.append(Component.text("[Далее]", NamedTextColor.DARK_GRAY));
                    }

                    sender.sendMessage(nav);

                } catch (IOException e) {
                    sender.sendMessage(Component.text("Ошибка чтения логов.", NamedTextColor.RED));
                    e.printStackTrace();
                }

                return true;
            }
            default -> {
                sender.sendMessage(Component.text("Неизвестная команда. Используйте /helium <reload|restart|optime|give|alogs>", NamedTextColor.RED));
                return true;
            }
        }
    }
}