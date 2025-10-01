package ru.helium.core.commands;

import ru.helium.core.HeliumCore;
import ru.helium.core.database.HeliumDatabase;
import ru.helium.core.gui.FriendGUI;
import ru.helium.core.utils.FriendInfo;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class FriendCommand implements CommandExecutor {

    private final HeliumCore plugin;
    private final HeliumDatabase database;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final Map<UUID, Long> teleportCooldowns = new HashMap<>();
    private static final long COOLDOWN_MILLIS = 15 * 1000L; // 15 секунд

    public FriendCommand(HeliumCore plugin, HeliumDatabase database) {
        this.plugin = plugin;
        this.database = database;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Команду может выполнить только игрок.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("list") || args[0].equalsIgnoreCase("friends") || args[0].equalsIgnoreCase("f")) {
            FriendGUI.open(player, plugin);
            return true;
        }

        if (args.length >= 1) {
            String sub = args[0].toLowerCase();

            if (sub.equals("help")) {
                sendHelp(player);
                return true;
            }

            if (args.length < 2) {
                player.sendMessage(Component.text("Использование: /friend list | add <игрок> | remove <игрок> | accept <игрок> | decline <игрок> | tp <игрок>", NamedTextColor.GRAY));
                return true;
            }

            String targetName = args[1];
            OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
            UUID playerUUID = player.getUniqueId();
            UUID targetUUID = target.getUniqueId();

            if (sub.equals("add") || sub.equals("fadd")) {
                if (!target.isOnline() && !target.hasPlayedBefore()) {
                    player.sendMessage(Component.text("Игрок не существует или никогда не заходил на сервер.", NamedTextColor.RED));
                    return true;
                }
                if (!target.isOnline()) {
                    player.sendMessage(Component.text("Игрок не в сети.", NamedTextColor.RED));
                    return true;
                }
                if (targetUUID.equals(playerUUID)) {
                    player.sendMessage(Component.text("Нельзя добавить самого себя.", NamedTextColor.RED));
                    return true;
                }
                if (plugin.getFriendRequestManager().hasRequest(target.getPlayer(), player)) {
                    player.sendMessage(Component.text("Вы уже отправили заявку этому игроку.", NamedTextColor.RED));
                    return true;
                }

                plugin.getFriendRequestManager().sendRequest(player, target.getPlayer());
                player.sendMessage(Component.text("Заявка в друзья отправлена игроку " + targetName, NamedTextColor.GREEN));
                return true;
            }

            if (sub.equals("remove") || sub.equals("fremove")) {
                try {
                    List<FriendInfo> friends = database.getFriends(playerUUID);
                    boolean removed = friends.removeIf(f -> f.getUuid().equals(targetUUID));

                    if (removed) {
                        database.setFriends(playerUUID, friends);
                        player.sendMessage(Component.text("Игрок " + targetName + " удалён из друзей.", NamedTextColor.GREEN));
                    } else {
                        player.sendMessage(Component.text("Игрок " + targetName + " не найден в друзьях.", NamedTextColor.RED));
                    }
                } catch (SQLException e) {
                    player.sendMessage(Component.text("Ошибка базы данных.", NamedTextColor.RED));
                    plugin.getLogger().severe("Ошибка удаления друга: " + e.getMessage());
                }
                return true;
            }

            if (sub.equals("accept") || sub.equals("faccept")) {
                boolean accepted = plugin.getFriendRequestManager().acceptRequest(player, target.getPlayer());
                if (accepted) {
                    try {
                        List<FriendInfo> friendsPlayer = database.getFriends(playerUUID);
                        List<FriendInfo> friendsTarget = database.getFriends(targetUUID);

                        long addedAt = System.currentTimeMillis() + java.util.TimeZone.getTimeZone("Europe/Moscow").getOffset(System.currentTimeMillis());
                        friendsPlayer.add(new FriendInfo(targetUUID, addedAt));
                        friendsTarget.add(new FriendInfo(playerUUID, addedAt));

                        database.setFriends(playerUUID, friendsPlayer);
                        database.setFriends(targetUUID, friendsTarget);
                    } catch (SQLException e) {
                        player.sendMessage(Component.text("Ошибка при добавлении друга в базу данных.", NamedTextColor.RED));
                        plugin.getLogger().severe("Ошибка добавления друга: " + e.getMessage());
                        return true;
                    }
                    player.sendMessage(Component.text("Вы приняли заявку от " + targetName, NamedTextColor.GREEN));

                    Player targetPlayer = target.getPlayer();
                    if (targetPlayer != null && targetPlayer.isOnline()) {
                        targetPlayer.sendMessage(Component.text(player.getName() + " принял вашу заявку в друзья.", NamedTextColor.GRAY));
                    }
                } else {
                    player.sendMessage(Component.text("У вас нет заявки от " + targetName, NamedTextColor.RED));
                }
                return true;
            }

            if (sub.equals("decline") || sub.equals("fdecline")) {
                boolean declined = plugin.getFriendRequestManager().declineRequest(player, target.getPlayer());
                if (declined) {
                    player.sendMessage(Component.text("Вы отклонили заявку от " + targetName, NamedTextColor.YELLOW));

                    Player targetPlayer = target.getPlayer();
                    if (targetPlayer != null && targetPlayer.isOnline()) {
                        targetPlayer.sendMessage(Component.text(player.getName() + " отклонил вашу заявку в друзья.", NamedTextColor.GRAY));
                    }
                } else {
                    player.sendMessage(Component.text("У вас нет заявки от " + targetName, NamedTextColor.RED));
                }
                return true;
            }

            if (sub.equals("tp")) {
                try {
                    List<FriendInfo> friends = database.getFriends(playerUUID);
                    boolean isFriend = friends.stream()
                            .anyMatch(f -> f.getUuid().equals(targetUUID));

                    if (!isFriend) {
                        player.sendMessage(Component.text("Этот игрок не в вашем списке друзей.", NamedTextColor.RED));
                        return true;
                    }
                } catch (SQLException e) {
                    player.sendMessage(Component.text("Ошибка базы данных.", NamedTextColor.RED));
                    plugin.getLogger().severe("Ошибка получения друзей: " + e.getMessage());
                    return true;
                }

                if (!target.isOnline()) {
                    player.sendMessage(Component.text("Друг сейчас оффлайн.", NamedTextColor.RED));
                    return true;
                }

                long now = System.currentTimeMillis();
                Long lastTp = teleportCooldowns.get(playerUUID);
                if (lastTp != null && now - lastTp < COOLDOWN_MILLIS) {
                    long secondsLeft = (COOLDOWN_MILLIS - (now - lastTp)) / 1000;
                    player.sendMessage(Component.text("Подождите " + secondsLeft + " секунд перед следующим телепортом.", NamedTextColor.RED));
                    return true;
                }

                player.teleport(target.getPlayer().getLocation());
                player.sendMessage(Component.text("Вы телепортировались к другу " + targetName, NamedTextColor.GREEN));
                teleportCooldowns.put(playerUUID, now);
                return true;
            }
        }

        player.sendMessage(Component.text("Использование: /friend list | add <игрок> | remove <игрок> | accept <игрок> | decline <игрок> | tp <игрок> | help", NamedTextColor.GRAY));
        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage(Component.text("≡ Команды /friend:", NamedTextColor.YELLOW));
        send(player, "/friend list", "Открыть список друзей");
        send(player, "/friend add <игрок>", "Отправить заявку в друзья");
        send(player, "/friend remove <игрок>", "Удалить друга");
        send(player, "/friend accept <игрок>", "Принять заявку в друзья");
        send(player, "/friend decline <игрок>", "Отклонить заявку в друзья");
        send(player, "/friend tp <игрок>", "Телепортироваться к другу");
    }

    private void send(Player player, String cmd, String desc) {
        TextComponent line = Component.text()
                .append(Component.text(" " + cmd, NamedTextColor.WHITE))
                .append(Component.text(" — ", NamedTextColor.DARK_GRAY))
                .append(Component.text(desc, NamedTextColor.GRAY))
                .build();
        player.sendMessage(line);
    }
}