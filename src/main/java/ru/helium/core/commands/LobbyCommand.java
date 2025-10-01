package ru.helium.core.commands;

import ru.helium.core.HeliumCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class LobbyCommand implements CommandExecutor {

    private final HeliumCore plugin;
    public LobbyCommand(HeliumCore plugin) {
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

        String worldName = plugin.getConfig().getString("lobby.world");
        if (worldName == null) {
            player.sendMessage(Component.text("Мир для лобби не указан в config.yml.", NamedTextColor.RED));
            return true;
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            player.sendMessage(Component.text("Мир '" + worldName + "' не найден.", NamedTextColor.RED));
            return true;
        }

        double x = plugin.getConfig().getDouble("lobby.x", 0.5);
        double y = plugin.getConfig().getDouble("lobby.y", 64.0);
        double z = plugin.getConfig().getDouble("lobby.z", 0.5);
        float yaw = (float) plugin.getConfig().getDouble("lobby.yaw", 0.0);
        float pitch = (float) plugin.getConfig().getDouble("lobby.pitch", 0.0);

        Location location = new Location(world, x, y, z, yaw, pitch);
        player.teleport(location);
        player.playSound(location, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.0f);
        player.sendMessage(Component.text("Вы были телепортированы в лобби.", NamedTextColor.GRAY));
        return true;
    }
}