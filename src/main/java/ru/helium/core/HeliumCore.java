package ru.helium.core;

import ru.helium.core.auth.AuthManager;
import ru.helium.core.commands.*;
import ru.helium.core.database.HeliumDatabase;
import ru.helium.core.database.WhitelistDatabase;
import ru.helium.core.gui.FriendGUI;
import ru.helium.core.gui.TablistManager;
import ru.helium.core.listeners.*;
import ru.helium.core.utils.*;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.Date;
import java.util.UUID;

public class HeliumCore extends JavaPlugin {

    private WhitelistDatabase whitelistDatabase;
    private HeliumDatabase heliumDatabase;
    private FriendRequestManager friendRequestManager;
    private HeliumAntiLag antiLag;
    private RankManager rankManager;
    private TablistManager tablistManager;
    private NamespacedKey mjolnirKey;
    private AuthManager authManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        checkAndRotateLogs();
        this.rankManager = new RankManager(this);
        this.authManager = new AuthManager(this, rankManager);

        this.rankManager = new RankManager(this);
        this.tablistManager = new TablistManager(this);
        this.mjolnirKey = new NamespacedKey(this, "mjolnir");

        this.whitelistDatabase = new WhitelistDatabase(getDataFolder() + "/whitelist.db");
        this.heliumDatabase = new HeliumDatabase(this);

        this.friendRequestManager = new FriendRequestManager();
        this.antiLag = new HeliumAntiLag(this);

        antiLag.start();

        logAsciiArt();
        logInfo("Helium Core " + getDescription().getVersion());

        syncBans();

        registerCoreCommands();
        registerModerationCommands();
        registerFriendCommands();
        registerCoinCommands();

        FriendGUI.init();

        Bukkit.getOnlinePlayers().forEach(rankManager::onPlayerJoin);

        registerListeners(
                new WhitelistListener(whitelistDatabase),
                new ChatListener(this, authManager),
                new CommandListener(this),
                new GotoGUIListener(this, heliumDatabase),
                new CommandCooldown(),
                new MjolnirListener(mjolnirKey),
                new FriendGUIListener(this),
                new SkinListener(this),
                new Listener() {
                    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
                    public void onPlayerLogin(org.bukkit.event.player.PlayerLoginEvent event) {
                        if (Bukkit.getBanList(BanList.Type.NAME).isBanned(event.getPlayer().getName())) {
                            String reason = "Без причины";
                            String source = "консоль";
                            Date expires = null;

                            BanList banList = Bukkit.getBanList(BanList.Type.NAME);
                            var banEntry = banList.getBanEntry(event.getPlayer().getName());
                            if (banEntry != null) {
                                if (banEntry.getReason() != null) reason = banEntry.getReason();
                                if (banEntry.getSource() != null) source = banEntry.getSource();
                                expires = banEntry.getExpiration();
                            }

                            String formatted = "§cВы заблокированы!\n"
                                    + "§7Причина: §f" + reason + "\n"
                                    + "§7До: §f" + (expires != null ? formatDate(expires.getTime()) + " МСК" : "Навсегда") + "\n"
                                    + "§7Заблокирован: §f" + source;

                            event.disallow(org.bukkit.event.player.PlayerLoginEvent.Result.KICK_BANNED, formatted);
                        }
                    }
                }
        );
    }

    @Override
    public void onDisable() {
        if (authManager != null) {
            authManager.stopHttpServer();
        }
        heliumDatabase.close();
        getLogger().info("HeliumCore успешно выгружен из сервера");
    }

    private void registerCoreCommands() {
        registerCommand("sup", new SupCommand());
        registerCommand("s", new SupCommand());
        registerCommand("sethome", new HomeCommands(heliumDatabase));
        registerCommand("home", new HomeCommands(heliumDatabase));
        registerCommand("lobby", new LobbyCommand(this));
        registerCommand("setlobby", new SetLobbyCommand(this));
        registerCommand("goto", new GotoCommand(this));
        registerCommand("g", new GotoCommand(this));
        registerCommand("helium", new HeliumCommands(this));
        registerCommand("help", new HelpCommand());
        registerCommand("hwl", new WhitelistCommand(whitelistDatabase));
        registerCommand("tpa", new TPACommand(this));
        registerCommand("status", new StatusCommand(this));
        registerCommand("skin", new SkinCommand(this));
        registerCommand("msg", new SocialCommands(this));
    }

    private void registerModerationCommands() {
        MODCommands modCommands = new MODCommands(this, heliumDatabase);
        registerCommand("ban", modCommands);
        registerCommand("unban", modCommands);
        registerCommand("kick", modCommands);
        registerCommand("mute", modCommands);
        registerCommand("unmute", modCommands);
        registerCommand("clearchat", modCommands);
        registerCommand("cc", modCommands);
        registerCommand("gm", modCommands);
        registerCommand("fly", modCommands);
        registerCommand("say", modCommands);

        registerListeners(modCommands);
    }

    private void registerFriendCommands() {
        FriendCommand friendCommand = new FriendCommand(this, heliumDatabase);
        for (String alias : new String[]{"friend", "friends", "f"}) {
            registerCommand(alias, friendCommand);
        }
    }

    private void registerCoinCommands() {
        CoinCommand coinCommand = new CoinCommand(heliumDatabase);
        for (String alias : new String[]{"coin", "c", "money"}) {
            registerCommand(alias, coinCommand);
        }
    }

    private void syncBans() {
        try {
            for (UUID uuid : heliumDatabase.getAllBanned()) {
                HeliumDatabase.BanInfo ban = heliumDatabase.getBan(uuid);
                if (ban != null) {
                    OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
                    if (player.getName() != null) {
                        Bukkit.getBanList(BanList.Type.NAME).addBan(
                                player.getName(),
                                ban.reason(),
                                ban.until() == 0 ? null : new Date(ban.until()),
                                ban.by()
                        );
                    }
                }
            }
        } catch (SQLException e) {
            getLogger().severe("Не удалось синхронизировать баны: " + e.getMessage());
        }
    }

    private String formatDate(long millis) {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        sdf.setTimeZone(java.util.TimeZone.getTimeZone("Europe/Moscow"));
        return sdf.format(new java.util.Date(millis));
    }

    private void registerCommand(String name, Object executor) {
        PluginCommand command = getCommand(name);
        if (command != null && executor instanceof org.bukkit.command.CommandExecutor) {
            command.setExecutor((org.bukkit.command.CommandExecutor) executor);
        }
    }

    private void registerListeners(Listener... listeners) {
        for (Listener listener : listeners) {
            Bukkit.getPluginManager().registerEvents(listener, this);
        }
    }

    private void logAsciiArt() {
        final String YELLOW = "\u001B[0;33m";
        final String RESET = "\u001B[0m";
        String[] art = {
                "                                                                                 ",
                "██╗  ██╗███████╗██╗     ██╗██╗   ██╗███╗   ███╗ ██████╗ ██████╗ ██████╗ ███████╗",
                "██║  ██║██╔════╝██║     ██║██║   ██║████╗ ████║██╔════╝██╔═══██╗██╔══██╗██╔════╝",
                "███████║█████╗  ██║     ██║██║   ██║██╔████╔██║██║     ██║   ██║██████╔╝█████╗  ",
                "██╔══██║██╔══╝  ██║     ██║██║   ██║██║╚██╔╝██║██║     ██║   ██║██╔══██╗██╔══╝  ",
                "██║  ██║███████╗███████╗██║╚██████╔╝██║ ╚═╝ ██║╚██████╗╚██████╔╝██║  ██║███████╗",
                "╚═╝  ╚═╝╚══════╝╚══════╝╚═╝ ╚═════╝ ╚═╝     ╚═╝ ╚═════╝ ╚═════╝ ╚═╝  ╚═╝╚══════╝",
                "                                                                                 "
        };
        for (String line : art) {
            getLogger().info(YELLOW + line + RESET);
        }
    }

    private void logInfo(String msg) {
        final String BRIGHT_YELLOW = "\u001B[93m";
        final String RESET = "\u001B[0m";
        getLogger().info(BRIGHT_YELLOW + msg + RESET);
    }

    public HeliumDatabase getDatabase() {
        return heliumDatabase;
    }

    public HeliumAntiLag getAntiLag() {
        return antiLag;
    }

    public FriendRequestManager getFriendRequestManager() {
        return friendRequestManager;
    }

    public RankManager getRankManager() {
        return rankManager;
    }

    public NamespacedKey getMjolnirKey() {
        return mjolnirKey;
    }

    public org.bukkit.Location getLobbyLocation() {
        String worldName = getConfig().getString("lobby.world", "lobby");
        double x = getConfig().getDouble("lobby.x", 735.5);
        double y = getConfig().getDouble("lobby.y", 98.0);
        double z = getConfig().getDouble("lobby.z", -183.5);
        float yaw = (float) getConfig().getDouble("lobby.yaw", 0.0);
        float pitch = (float) getConfig().getDouble("lobby.pitch", 0.0);
        
        org.bukkit.World world = Bukkit.getWorld(worldName);
        if (world == null) {
            world = Bukkit.getWorlds().get(0);
        }
        
        return new org.bukkit.Location(world, x, y, z, yaw, pitch);
    }

    public org.bukkit.Location getAuthLocation() {
        String worldName = getConfig().getString("auth.world", "lobby");
        double x = getConfig().getDouble("auth.x", 705.5514238996394);
        double y = getConfig().getDouble("auth.y", 115.0);
        double z = getConfig().getDouble("auth.z", -192.51732080126575);
        float yaw = (float) getConfig().getDouble("auth.yaw", -0.0014648438);
        float pitch = (float) getConfig().getDouble("auth.pitch", -4.6486955);
        
        org.bukkit.World world = Bukkit.getWorld(worldName);
        if (world == null) {
            world = Bukkit.getWorlds().get(0);
        }
        
        return new org.bukkit.Location(world, x, y, z, yaw, pitch);
    }

    public void checkAndRotateLogs() {
        File logFile = new File(getDataFolder(), "logs/modlogs.txt");
        File metaFile = new File(getDataFolder(), "logs/log_month.txt");

        String currentMonth = new java.text.SimpleDateFormat("yyyy-MM").format(new java.util.Date());
        String lastMonth = "";

        if (metaFile.exists()) {
            try {
                lastMonth = Files.readString(metaFile.toPath()).trim();
            } catch (IOException ignored) {}
        }

        if (!currentMonth.equals(lastMonth)) {
            if (logFile.exists()) logFile.delete();
            try {
                Files.createDirectories(metaFile.getParentFile().toPath());
                Files.writeString(metaFile.toPath(), currentMonth);
            } catch (IOException ignored) {}
        }
    }
}