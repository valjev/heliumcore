package ru.helium.core.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.util.Map;

public class HelpCommand implements CommandExecutor {

    public static void overrideHelpCommand(PluginCommand customHelpCommand) {
        try {
            Field commandMapField = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            SimpleCommandMap commandMap = (SimpleCommandMap) commandMapField.get(Bukkit.getServer());

            Field knownCommandsField = SimpleCommandMap.class.getDeclaredField("knownCommands");
            knownCommandsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Command> knownCommands = (Map<String, Command>) knownCommandsField.get(commandMap);

            knownCommands.remove("help");
            knownCommands.remove("minecraft:help");
            knownCommands.put("help", customHelpCommand);
            knownCommands.put("helium:help", customHelpCommand);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Команда доступна только игрокам.");
            return true;
        }

        player.sendMessage(Component.text("≡ Доступные команды:", NamedTextColor.YELLOW));
        send(player, "/home (/h)", "Телепорт домой");
        send(player, "/sethome", "Установить точку дома");
        send(player, "/goto (/g)", "Открыть меню телепортации");
        send(player, "/sup (/s, /report)", "Связаться с модерацией (стражи)");
        send(player, "/tpa <никнейм>", "Отправить запрос на телепорт");
        send(player, "/lobby (/l)", "Вернуться в лобби");
        send(player, "/friend help (/f help)", "Система друзей");
        send(player, "/skin set/clear/random", "Установка скина");
        send(player, "/coin help (/c, /money)", "Игровая валюта");

        return true;
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