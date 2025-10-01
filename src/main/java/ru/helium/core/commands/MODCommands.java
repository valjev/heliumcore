package ru.helium.core.commands;

import ru.helium.core.HeliumCore;
import ru.helium.core.database.HeliumDatabase;
import ru.helium.core.database.HeliumDatabase.BanInfo;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import java.io.*;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Scanner;
import java.util.TimeZone;

public class MODCommands implements Listener, CommandExecutor {

    private final HeliumCore plugin;
    private final HeliumDatabase database;

    public MODCommands(HeliumCore plugin, HeliumDatabase database) {
        this.plugin = plugin;
        this.database = database;
    }

    private String getSenderName(CommandSender sender) {
        if (sender instanceof ConsoleCommandSender) return "консоль";
        String name = sender.getName();
        if (name.equalsIgnoreCase("RCON")) return "консоль";
        return name;
    }

    private String getSenderRankInInstrumental(CommandSender sender) {
        if (sender instanceof ConsoleCommandSender) return "консолью";
        if (sender instanceof Player player) {
            if (player.hasPermission("helium.admin")) return "администратором";
            if (player.hasPermission("helium.mod")) return "стражем";
        }
        return "администратором";
    }

    private String getGameModeName(org.bukkit.GameMode gm) {
        return switch (gm) {
            case SURVIVAL -> "выживание";
            case CREATIVE -> "креатив";
            case ADVENTURE -> "приключение";
            case SPECTATOR -> "наблюдатель";
        };
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        boolean isConsole = sender instanceof ConsoleCommandSender;

        if (!sender.hasPermission("helium.admin") && !sender.hasPermission("helium.mod") && !sender.isOp() && !isConsole) {
            send(sender, "У вас нет прав для этой команды.", NamedTextColor.RED);
            return true;
        }

        String cmd = label.toLowerCase();
        try {
            switch (cmd) {
                case "ban" -> {
                    if (args.length < 3) {
                        send(sender, "Использование: /ban <игрок> <время> <причина>", NamedTextColor.RED);
                        return true;
                    }

                    OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
                    if (!target.hasPlayedBefore() && !target.isOnline()) {
                        send(sender, "Игрок не найден.", NamedTextColor.RED);
                        return true;
                    }

                    if (sender instanceof Player && ((Player) sender).getUniqueId().equals(target.getUniqueId())) {
                        send(sender, "Вы не можете заблокировать самого себя.", NamedTextColor.RED);
                        return true;
                    }

                    String time = args[1];
                    String reason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                    long durationMs = parseTime(time);
                    if (durationMs == -1) {
                        send(sender, "Неверный формат времени.", NamedTextColor.RED);
                        return true;
                    }

                    long until = durationMs == 0 ? 0 : System.currentTimeMillis() + durationMs;

                    String whoName = getSenderName(sender);
                    String whoRank = getSenderRankInInstrumental(sender);

                    database.setBan(target.getUniqueId(), reason, until, whoRank + " " + whoName);

                    if (target.getName() != null) {
                        Bukkit.getBanList(BanList.Type.NAME).addBan(
                                target.getName(),
                                reason,
                                until == 0 ? null : new Date(until),
                                whoName
                        );
                    }

                    logModAction(whoName, whoRank, "BAN", target.getName(), until, reason);

                    if (target.isOnline()) {
                        Player online = target.getPlayer();
                        if (online != null) {
                            TextComponent.Builder kickBuilder = Component.text();

                            kickBuilder.append(Component.text("Вы заблокированы!\n", NamedTextColor.RED));
                            kickBuilder.append(Component.text("Причина: ", NamedTextColor.GRAY));
                            kickBuilder.append(Component.text(reason + "\n", NamedTextColor.WHITE));

                            if (until != 0) {
                                kickBuilder.append(Component.text("До: ", NamedTextColor.GRAY));
                                kickBuilder.append(Component.text(format(until) + " МСК\n", NamedTextColor.WHITE));
                            } else {
                                kickBuilder.append(Component.text("До: Навсегда\n", NamedTextColor.WHITE));
                            }

                            kickBuilder.append(Component.text("Заблокировал: ", NamedTextColor.GRAY));
                            kickBuilder.append(Component.text(whoName, NamedTextColor.WHITE));

                            online.kick(kickBuilder.build());
                        }
                    }
                    broadcast("&7Игрок &a" + target.getName() + " &7заблокирован " + whoRank + " &a" + whoName + "&7, причина: &f" + reason);
                    return true;
                }

                case "unban" -> {
                    if (args.length < 1) {
                        send(sender, "Использование: /unban <игрок>", NamedTextColor.RED);
                        return true;
                    }

                    OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
                    if (!target.hasPlayedBefore() && !target.isOnline()) {
                        send(sender, "Игрок не найден.", NamedTextColor.RED);
                        return true;
                    }

                    BanInfo ban = database.getBan(target.getUniqueId());
                    if (ban != null && ban.by().equalsIgnoreCase("консоль") && !(sender instanceof ConsoleCommandSender)) {
                        send(sender, "Вы не можете снять бан, выданный консолью.", NamedTextColor.RED);
                        return true;
                    }

                    database.removeBan(target.getUniqueId());

                    if (target.getName() != null) {
                        Bukkit.getBanList(BanList.Type.NAME).pardon(target.getName());
                    }

                    logModAction(getSenderName(sender), getSenderRankInInstrumental(sender), "UNBAN", target.getName(), null, null);

                    broadcast("&7Игрок &a" + target.getName() + " &7разблокирован " + getSenderRankInInstrumental(sender) + " &a" + getSenderName(sender));
                    return true;
                }

                case "kick" -> {
                    if (args.length < 2) {
                        send(sender, "Использование: /kick <игрок> <причина>", NamedTextColor.RED);
                        return true;
                    }
                    Player target = Bukkit.getPlayerExact(args[0]);
                    if (target == null) {
                        send(sender, "Игрок не найден или оффлайн.", NamedTextColor.RED);
                        return true;
                    }
                    if (sender instanceof Player && ((Player) sender).getUniqueId().equals(target.getUniqueId())) {
                        send(sender, "Вы не можете кикнуть самого себя.", NamedTextColor.RED);
                        return true;
                    }
                    String reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                    Component kickMessage = Component.text("Вы были кикнуты!", NamedTextColor.RED)
                            .append(Component.newline())
                            .append(Component.text("Причина: ", NamedTextColor.GRAY))
                            .append(Component.text(reason, NamedTextColor.WHITE));
                    target.kick(kickMessage);

                    String whoName = getSenderName(sender);
                    String whoRank = getSenderRankInInstrumental(sender);
                    logModAction(whoName, whoRank, "KICK", target.getName(), null, reason);

                    broadcast("&7Игрок &a" + target.getName() + " &7кикнут " + whoRank + " &a" + whoName + "&7, причина: &f" + reason);
                    return true;
                }

                case "mute" -> {
                    if (args.length < 3) {
                        send(sender, "Использование: /mute <игрок> <время> <причина>", NamedTextColor.RED);
                        return true;
                    }
                    OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
                    if (!target.hasPlayedBefore() && !target.isOnline()) {
                        send(sender, "Игрок не найден.", NamedTextColor.RED);
                        return true;
                    }
                    if (sender instanceof Player && ((Player) sender).getUniqueId().equals(target.getUniqueId())) {
                        send(sender, "Вы не можете замутить самого себя.", NamedTextColor.RED);
                        return true;
                    }

                    String time = args[1];
                    String reason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                    long durationMs = parseTime(time);
                    if (durationMs == -1) {
                        send(sender, "Неверный формат времени.", NamedTextColor.RED);
                        return true;
                    }
                    long until = durationMs == 0 ? 0 : System.currentTimeMillis() + durationMs;

                    database.setMute(target.getUniqueId(), reason, until);

                    String whoName = getSenderName(sender);
                    String whoRank = getSenderRankInInstrumental(sender);
                    logModAction(whoName, whoRank, "MUTE", target.getName(), until, reason);

                    broadcast("&7Игрок &a" + target.getName() + " &7замучен " + whoRank + " &a" + whoName + "&7, причина: &f" + reason);
                    return true;
                }

                case "unmute" -> {
                    if (args.length < 1) {
                        send(sender, "Использование: /unmute <игрок>", NamedTextColor.RED);
                        return true;
                    }
                    OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
                    if (!target.hasPlayedBefore() && !target.isOnline()) {
                        send(sender, "Игрок не найден.", NamedTextColor.RED);
                        return true;
                    }

                    database.removeMute(target.getUniqueId());
                    logModAction(getSenderName(sender), getSenderRankInInstrumental(sender), "UNMUTE", target.getName(), null, null);

                    broadcast("&7Игрок &a" + target.getName() + " &7размучен " + getSenderRankInInstrumental(sender) + " &a" + getSenderName(sender));
                    return true;
                }

                case "clearchat", "cc" -> {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        for (int i = 0; i < 100; i++) {
                            p.sendMessage("");
                        }
                    }
                    String whoName = getSenderName(sender);
                    String whoRank = getSenderRankInInstrumental(sender);
                    broadcast("&7Чат был очищен " + whoRank + " &a" + whoName);
                    return true;
                }

                default -> {
                    send(sender, "Неизвестная команда.", NamedTextColor.RED);
                    return true;
                }

                case "gm", "gamemode" -> {
                    if (!(sender instanceof Player player)) {
                        send(sender, "Эта команда доступна только игрокам.", NamedTextColor.RED);
                        return true;
                    }

                    if (!sender.hasPermission("helium.mod") && !sender.hasPermission("helium.admin")) {
                        send(sender, "У вас нет прав использовать эту команду.", NamedTextColor.RED);
                        return true;
                    }

                    if (args.length < 1) {
                        send(sender, "Использование: /gm <0|1|2|3|s|c|a|sp> [игрок]", NamedTextColor.RED);
                        return true;
                    }

                    GameMode mode = switch (args[0].toLowerCase()) {
                        case "0", "s" -> GameMode.SURVIVAL;
                        case "1", "c" -> GameMode.CREATIVE;
                        case "2", "a" -> GameMode.ADVENTURE;
                        case "3", "sp" -> GameMode.SPECTATOR;
                        default -> null;
                    };

                    if (mode == null) {
                        send(sender, "Неверный режим игры.", NamedTextColor.RED);
                        return true;
                    }

                    Player target = player;

                    if (args.length >= 2) {
                        if (!sender.hasPermission("helium.admin")) {
                            send(sender, "Вы не можете менять режим игры у других игроков.", NamedTextColor.RED);
                            return true;
                        }
                        target = Bukkit.getPlayerExact(args[1]);
                        if (target == null) {
                            send(sender, "Игрок не найден.", NamedTextColor.RED);
                            return true;
                        }
                    }

                    target.setGameMode(mode);
                    send(sender, "Режим игры установлен на " + getGameModeName(mode) + (target == player ? "." : " для игрока " + target.getName() + "."), NamedTextColor.GREEN);
                    if (target != player) {
                        send(target, "Ваш режим игры изменён на " + getGameModeName(mode) + ".", NamedTextColor.GREEN);
                    }
                    return true;
                }

                case "fly" -> {
                    if (!(sender instanceof Player player)) {
                        send(sender, "Эта команда доступна только игрокам.", NamedTextColor.RED);
                        return true;
                    }

                    if (!sender.hasPermission("helium.mod") && !sender.hasPermission("helium.admin")) {
                        send(sender, "У вас нет прав использовать эту команду.", NamedTextColor.RED);
                        return true;
                    }

                    if (args.length >= 2 && args[0].equalsIgnoreCase("speed")) {
                        try {
                            float speed = Float.parseFloat(args[1]);
                            if (speed < 0.1f || speed > 1.0f) {
                                send(sender, "Скорость должна быть от 0.1 до 1.0.", NamedTextColor.RED);
                                return true;
                            }
                            player.setFlySpeed(speed);
                            send(sender, "Скорость полёта установлена на " + speed + ".", NamedTextColor.GREEN);
                        } catch (NumberFormatException e) {
                            send(sender, "Неверное значение скорости.", NamedTextColor.RED);
                        }
                        return true;
                    }

                    Player target = player;

                    if (args.length >= 1) {
                        if (!sender.hasPermission("helium.admin")) {
                            send(sender, "Вы не можете менять режим полёта у других игроков.", NamedTextColor.RED);
                            return true;
                        }
                        target = Bukkit.getPlayerExact(args[0]);
                        if (target == null) {
                            send(sender, "Игрок не найден.", NamedTextColor.RED);
                            return true;
                        }
                    }

                    boolean flight = !target.getAllowFlight();
                    target.setAllowFlight(flight);
                    target.setFlying(flight);

                    send(target, "Полёт " + (flight ? "включён" : "выключен") + ".", NamedTextColor.GREEN);
                    if (!target.equals(player)) {
                        send(player, "Вы " + (flight ? "включили" : "выключили") + " полёт для игрока " + target.getName() + ".", NamedTextColor.GREEN);
                    }
                    return true;
                }
            }
        } catch (SQLException e) {
            send(sender, "Ошибка базы данных.", NamedTextColor.RED);
            plugin.getLogger().severe(e.getMessage());
            return true;
        }
    }

    private void send(CommandSender sender, String msg, NamedTextColor color) {
        sender.sendMessage(Component.text(msg, color));
    }

    private void broadcast(String msg) {
        Component message = Component.text(msg.replace("&a", "§a").replace("&7", "§7").replace("&f", "§f"));
        Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(message));
    }

    private long parseTime(String time) {
        if (time == null || time.isEmpty()) return 0;
        char unit = time.charAt(time.length() - 1);
        String numStr = time.substring(0, time.length() - 1);
        long multiplier;
        switch (unit) {
            case 's' -> multiplier = 1000;
            case 'm' -> multiplier = 60_000;
            case 'h' -> multiplier = 3_600_000;
            case 'd' -> multiplier = 86_400_000;
            case 'y' -> multiplier = 31_536_000_000L;
            default -> {
                return -1;
            }
        }
        try {
            long val = Long.parseLong(numStr);
            if (val < 0) return -1;
            return val * multiplier;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private String format(long until) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        sdf.setTimeZone(TimeZone.getTimeZone("Europe/Moscow"));
        return sdf.format(new Date(until));
    }

    private void logModAction(String whoName, String whoRank, String action, String targetName, Long until, String reason) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        sdf.setTimeZone(TimeZone.getTimeZone("Europe/Moscow"));
        String timestamp = sdf.format(new Date());

        StringBuilder entry = new StringBuilder();
        entry.append("[").append(timestamp).append(" МСК] ")
                .append(whoName);

        String role = whoRank.equalsIgnoreCase("консолью") ? "(консоль)" :
                whoRank.equalsIgnoreCase("администратором") ? "(администратор)" :
                        whoRank.equalsIgnoreCase("стражем") ? "(страж)" : "(администратор)";

        entry.append(" ").append(role).append(" ");

        switch (action.toUpperCase()) {
            case "BAN" -> entry.append("заблокировал ");
            case "UNBAN" -> entry.append("разблокировал ");
            case "MUTE" -> entry.append("замутил ");
            case "UNMUTE" -> entry.append("размутил ");
            case "KICK" -> entry.append("кикнул ");
            default -> entry.append(action).append(" ");
        }

        entry.append(targetName);

        if (until != null && until > 0 && (action.equalsIgnoreCase("BAN") || action.equalsIgnoreCase("MUTE"))) {
            entry.append(", срок: ").append(format(until)).append(" МСК");
        }

        if (reason != null && !reason.isBlank() && !action.equalsIgnoreCase("UNBAN") && !action.equalsIgnoreCase("UNMUTE")) {
            entry.append(", причина: ").append(reason);
        }

        writeModLog(entry.toString());
    }

    private void writeModLog(String logEntry) {
        File logFile = new File(plugin.getDataFolder(), "logs/modlogs.txt");
        logFile.getParentFile().mkdirs();
        StringBuilder existingContent = new StringBuilder();
        if (logFile.exists()) {
            try (Scanner scanner = new Scanner(logFile)) {
                while (scanner.hasNextLine()) {
                    existingContent.append(scanner.nextLine()).append(System.lineSeparator());
                }
            } catch (IOException e) {
                plugin.getLogger().severe("Не удалось прочитать лог модерации: " + e.getMessage());
            }
        }
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(logFile))) {
            bw.write(logEntry);
            bw.newLine();
            bw.write(existingContent.toString());
        } catch (IOException e) {
            plugin.getLogger().severe("Не удалось записать лог модерации: " + e.getMessage());
        }
    }
}