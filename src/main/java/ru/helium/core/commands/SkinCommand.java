package ru.helium.core.commands;

import ru.helium.core.HeliumCore;
import ru.helium.core.database.HeliumDatabase;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.skinsrestorer.api.SkinsRestorer;
import net.skinsrestorer.api.SkinsRestorerProvider;
import net.skinsrestorer.api.property.InputDataResult;
import net.skinsrestorer.api.property.SkinIdentifier;
import net.skinsrestorer.api.storage.PlayerStorage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class SkinCommand implements CommandExecutor {

    private final SkinsRestorer skinsRestorer;
    private final HeliumDatabase database;

    public SkinCommand(HeliumCore plugin) {
        this.skinsRestorer = SkinsRestorerProvider.get();
        this.database = plugin.getDatabase();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Команда доступна только игрокам.").color(NamedTextColor.RED));
            return true;
        }

        if (args.length < 1) {
            showUsage(player);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "set" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("Использование: /skin set <ник или URL>").color(NamedTextColor.RED));
                    return true;
                }
                String skinInput = args[1];
                Player target = player;

                if (args.length >= 3 && (player.isOp() || player.hasPermission("helium.admin"))) {
                    target = Bukkit.getPlayer(args[2]);
                    if (target == null) {
                        player.sendMessage(Component.text("Игрок не найден или оффлайн.").color(NamedTextColor.RED));
                        return true;
                    }
                } else if (args.length >= 3) {
                    player.sendMessage(Component.text("У вас нет прав на выполнение этой команды.").color(NamedTextColor.RED));
                    return true;
                }

                setSkin(player, target, skinInput);
            }
            case "clear" -> {
                Player target = player;

                if (args.length >= 2 && (player.isOp() || player.hasPermission("helium.admin"))) {
                    target = Bukkit.getPlayer(args[1]);
                    if (target == null) {
                        player.sendMessage(Component.text("Игрок не найден или оффлайн.").color(NamedTextColor.RED));
                        return true;
                    }
                } else if (args.length >= 2) {
                    player.sendMessage(Component.text("У вас нет прав на выполнение этой команды.").color(NamedTextColor.RED));
                    return true;
                }

                clearSkin(player, target);
            }
            case "info" -> {
                Player target = player;

                if (args.length >= 2 && (player.isOp() || player.hasPermission("helium.admin"))) {
                    target = Bukkit.getPlayer(args[1]);
                    if (target == null) {
                        player.sendMessage(Component.text("Игрок не найден или оффлайн.").color(NamedTextColor.RED));
                        return true;
                    }
                } else if (args.length >= 2) {
                    player.sendMessage(Component.text("У вас нет прав на выполнение этой команды.").color(NamedTextColor.RED));
                    return true;
                }

                showSkinInfo(player, target);
            }
            default -> showUsage(player);
        }

        return true;
    }

    private void showUsage(Player player) {
        player.sendMessage(Component.text("Использование команды /skin:", NamedTextColor.YELLOW));
        player.sendMessage(Component.text(" /skin set <ник или URL> [игрок] — установить скин", NamedTextColor.GRAY));
        player.sendMessage(Component.text(" /skin clear [игрок] — сбросить скин", NamedTextColor.GRAY));
        player.sendMessage(Component.text(" /skin info [игрок] — информация о скине", NamedTextColor.GRAY));
    }

    private void showSkinInfo(Player sender, Player target) {
        CompletableFuture.runAsync(() -> {
            try {
                String savedSkin = database.getCustomSkin(target.getUniqueId());
                
                if (sender.equals(target)) {
                    if (savedSkin != null && !savedSkin.isEmpty()) {
                        sender.sendMessage(Component.text("Ваш сохранённый скин: " + savedSkin).color(NamedTextColor.GREEN));
                    } else {
                        sender.sendMessage(Component.text("У вас нет сохранённого скина.").color(NamedTextColor.GRAY));
                    }
                } else {
                    if (savedSkin != null && !savedSkin.isEmpty()) {
                        sender.sendMessage(Component.text("Сохранённый скин игрока " + target.getName() + ": " + savedSkin).color(NamedTextColor.GREEN));
                    } else {
                        sender.sendMessage(Component.text("У игрока " + target.getName() + " нет сохранённого скина.").color(NamedTextColor.GRAY));
                    }
                }
            } catch (Exception e) {
                sender.sendMessage(Component.text("Ошибка при получении информации о скине.").color(NamedTextColor.RED));
                e.printStackTrace();
            }
        });
    }

    private void setSkin(Player sender, Player target, String input) {
        CompletableFuture.runAsync(() -> {
            try {
                PlayerStorage playerStorage = skinsRestorer.getPlayerStorage();
                Optional<InputDataResult> skinDataOpt = skinsRestorer.getSkinStorage().findOrCreateSkinData(input);

                if (skinDataOpt.isEmpty()) {
                    sender.sendMessage(Component.text("Скин не найден или ошибка получения данных.")
                            .color(NamedTextColor.RED));
                    return;
                }

                SkinIdentifier identifier = skinDataOpt.get().getIdentifier();
                UUID targetUUID = target.getUniqueId();

                playerStorage.setSkinIdOfPlayer(targetUUID, identifier);
                skinsRestorer.getSkinApplier(Player.class).applySkin(target);

                database.setCustomSkin(targetUUID, input);

                if (sender.equals(target)) {
                    sender.sendMessage(Component.text("Скин успешно установлен и сохранён!").color(NamedTextColor.GREEN));
                } else {
                    sender.sendMessage(Component.text("Скин установлен для игрока " + target.getName() + " и сохранён!")
                            .color(NamedTextColor.GREEN));
                    target.sendMessage(Component.text("Ваш скин был изменён администратором.").color(NamedTextColor.YELLOW));
                }
            } catch (Exception e) {
                sender.sendMessage(Component.text("Ошибка при установке скина.").color(NamedTextColor.RED));
                e.printStackTrace();
            }
        });
    }

    private void clearSkin(Player sender, Player target) {
        CompletableFuture.runAsync(() -> {
            try {
                PlayerStorage playerStorage = skinsRestorer.getPlayerStorage();
                playerStorage.setSkinIdOfPlayer(target.getUniqueId(), null);
                skinsRestorer.getSkinApplier(Player.class).applySkin(target);

                database.clearCustomSkin(target.getUniqueId());

                if (sender.equals(target)) {
                    sender.sendMessage(Component.text("Скин успешно сброшен и удалён из базы данных.").color(NamedTextColor.GREEN));
                } else {
                    sender.sendMessage(Component.text("Скин сброшен у игрока " + target.getName() + " и удалён из базы данных.").color(NamedTextColor.GREEN));
                    target.sendMessage(Component.text("Ваш скин был сброшен администратором.").color(NamedTextColor.YELLOW));
                }
            } catch (Exception e) {
                sender.sendMessage(Component.text("Ошибка при сбросе скина.").color(NamedTextColor.RED));
                e.printStackTrace();
            }
        });
    }
}