package ru.helium.core.utils;

import ru.helium.core.HeliumCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RankManager {

    private final HeliumCore plugin;
    private final ScoreboardManager scoreboardManager;
    private final Scoreboard scoreboard;
    private final Map<Rank, Team> rankTeams = new HashMap<>();
    private final LegacyComponentSerializer legacySerializer = LegacyComponentSerializer.legacySection();

    public RankManager(HeliumCore plugin) {
        this.plugin = plugin;
        this.scoreboardManager = Bukkit.getScoreboardManager();
        this.scoreboard = scoreboardManager.getNewScoreboard();

        setupTeams();
    }

    private void setupTeams() {
        for (Rank rank : Rank.values()) {
            Team team = scoreboard.getTeam(rank.name());
            if (team == null) {
                team = scoreboard.registerNewTeam(rank.name());
            }
            Component prefixComponent = legacySerializer.deserialize(rank.getPrefix());
            team.prefix(prefixComponent);
            rankTeams.put(rank, team);
        }
    }

    public void applyRankToPlayer(Player player) {
        try {
            UUID uuid = player.getUniqueId();
            Rank rank = plugin.getDatabase().getRank(uuid);

            for (Team team : rankTeams.values()) {
                team.removeEntry(player.getName());
            }

            Team team = rankTeams.get(rank);
            if (team != null) {
                team.addEntry(player.getName());
            }

            player.setScoreboard(scoreboard);

            updatePermissions(player, rank);

        } catch (SQLException e) {
            plugin.getLogger().severe("Ошибка при получении ранга игрока " + player.getName() + ": " + e.getMessage());
        }
    }

    private void updatePermissions(Player player, Rank rank) {
        PermissionAttachment attachment = player.addAttachment(plugin);

        switch (rank) {
            case ADMIN -> {
                attachment.setPermission("helium.admin", true);
                attachment.setPermission("helium.mod", true);
                attachment.setPermission("helium.default", true);
            }
            case MODERATOR -> {
                attachment.setPermission("helium.admin", false);
                attachment.setPermission("helium.mod", true);
                attachment.setPermission("helium.default", true);
            }
            default -> {
                attachment.setPermission("helium.admin", false);
                attachment.setPermission("helium.mod", false);
                attachment.setPermission("helium.default", true);
            }
        }
    }

    public void onPlayerJoin(Player player) {
        applyRankToPlayer(player);
    }

    public void updatePlayerRank(Player player) {
        applyRankToPlayer(player);
    }

    public Rank getRank(UUID uuid) {
        try {
            return plugin.getDatabase().getRank(uuid);
        } catch (SQLException e) {
            plugin.getLogger().severe("Не удалось получить ранг для " + uuid + ": " + e.getMessage());
            return Rank.PLAYER;
        }
    }
}