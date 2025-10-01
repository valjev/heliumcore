package ru.helium.core.commands;

import ru.helium.core.HeliumCore;
import ru.helium.core.gui.GotoGUI;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class GotoCommand implements CommandExecutor {

    private final GotoGUI gotoGUI;

    public GotoCommand(HeliumCore plugin) {
        this.gotoGUI = new GotoGUI(plugin);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Команда доступна только игрокам.");
            return true;
        }

        gotoGUI.open(player, 0);
        return true;
    }
}