package ru.helium.core.commands;

import ru.helium.core.HeliumCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class SetLobbyCommand implements CommandExecutor {

    private final HeliumCore plugin;

    public SetLobbyCommand(HeliumCore plugin) {
        this.plugin = plugin;
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

        if (!player.isOp()) {
            player.sendMessage(Component.text("У вас нет прав для использования этой команды.", NamedTextColor.RED));
            return true;
        }

        Location loc = player.getLocation();
        if (loc.getWorld() == null) {
            player.sendMessage(Component.text("Не удалось определить мир.", NamedTextColor.RED));
            return true;
        }

        plugin.getConfig().set("lobby.world", loc.getWorld().getName());
        plugin.getConfig().set("lobby.x", loc.getX());
        plugin.getConfig().set("lobby.y", loc.getY());
        plugin.getConfig().set("lobby.z", loc.getZ());
        plugin.getConfig().set("lobby.yaw", loc.getYaw());
        plugin.getConfig().set("lobby.pitch", loc.getPitch());

        plugin.saveConfig();

        player.sendMessage(Component.text("Лобби успешно установлено!", NamedTextColor.GREEN));
        return true;
    }
}