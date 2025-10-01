package ru.helium.core.listeners;

import ru.helium.core.HeliumCore;
import ru.helium.core.utils.Rank;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

public class CommandListener implements Listener {

    private final HeliumCore plugin;

    public CommandListener(HeliumCore plugin) {
        this.plugin = plugin;
    }

    private final Set<String> userCommands = Set.of(
            "lobby", "home", "sethome", "tpa", "goto", "g", "sup",
            "s", "report", "friend", "friends", "f", "status", "ping", "help", "skin",
            "coin", "c", "money", "msg"
    );

    private final Set<String> modExtraCommands = Set.of(
            "kick", "mute", "unmute", "ban", "unban", "clearchat", "cc", "fly", "say"
    );

    @EventHandler
    public void onCommandSend(PlayerCommandSendEvent event) {
        Player player = event.getPlayer();
        Rank rank = getPlayerRank(player);

        if (player.isOp() || rank == Rank.ADMIN) return;

        Set<String> allowed = new HashSet<>(userCommands);
        if (rank == Rank.MODERATOR) {
            allowed.addAll(modExtraCommands);
        }

        event.getCommands().removeIf(cmd -> !allowed.contains(cmd.toLowerCase()));
    }

    @EventHandler
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        Rank rank = getPlayerRank(player);

        if (player.isOp() || rank == Rank.ADMIN) return;

        String message = event.getMessage().toLowerCase();
        if (!message.startsWith("/")) return;

        String commandInput = message.substring(1).split(" ")[0];
        String baseCommand = commandInput.contains(":")
                ? commandInput.split(":")[1]
                : commandInput;

        Set<String> allowed = new HashSet<>(userCommands);
        if (rank == Rank.MODERATOR) {
            allowed.addAll(modExtraCommands);
        }

        if (!allowed.contains(baseCommand)) {
            event.setCancelled(true);
            player.sendMessage("§f/" + baseCommand + " §cнеизвестная или недоступная команда.");
        }
    }

    private Rank getPlayerRank(Player player) {
        try {
            return plugin.getDatabase().getRank(player.getUniqueId());
        } catch (SQLException e) {
            return Rank.PLAYER;
        }
    }
}