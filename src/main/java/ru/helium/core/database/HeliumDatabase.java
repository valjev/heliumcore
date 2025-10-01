package ru.helium.core.database;

import ru.helium.core.HeliumCore;
import ru.helium.core.utils.FriendInfo;
import ru.helium.core.utils.Rank;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;

public class HeliumDatabase {

    private final HeliumCore plugin;
    private Connection connection;
    private final Gson gson = new Gson();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private volatile boolean isShutdown = false;

    public HeliumDatabase(HeliumCore plugin) {
        this.plugin = plugin;
        initializeConnection();
        startConnectionMonitor();
    }

    private void initializeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
            connection = DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/helium?useSSL=false&serverTimezone=UTC&autoReconnect=true&failOverReadOnly=false&maxReconnects=10&initialTimeout=10&connectTimeout=10000&socketTimeout=30000",
                "helium_user",
                "helium_password"
            );
            connection.setAutoCommit(false);
            createTable();
        } catch (SQLException e) {
            plugin.getLogger().severe("Ошибка подключения к MySQL: " + e.getMessage());
        }
    }

    private void startConnectionMonitor() {
        scheduler.scheduleAtFixedRate(() -> {
            if (isShutdown) return;
            try {
                if (connection == null || connection.isClosed()) {
                    initializeConnection();
                } else {
                    try (Statement stmt = connection.createStatement()) {
                        stmt.execute("SELECT 1");
                    }
                }
            } catch (SQLException e) {
                initializeConnection();
            }
        }, 30, 30, TimeUnit.SECONDS);
    }

    private void createTable() throws SQLException {
        try (Statement stmt = getConnection().createStatement()) {
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS helium_data (
                    uuid VARCHAR(36) PRIMARY KEY,
                    balance INT NOT NULL DEFAULT 0,
                    world VARCHAR(64),
                    x DOUBLE,
                    y DOUBLE,
                    z DOUBLE,
                    yaw FLOAT,
                    pitch FLOAT,
                    friends TEXT,
                    ban_reason TEXT,
                    ban_until BIGINT,
                    ban_by VARCHAR(64),
                    mute_reason TEXT,
                    mute_until BIGINT,
                    rank VARCHAR(32) DEFAULT 'PLAYER',
                    messages_enabled BOOLEAN NOT NULL DEFAULT TRUE,
                    telegram_token BIGINT,
                    nickname VARCHAR(255),
                    auth_ip VARCHAR(45)
                )
            """);
        }
        
        try (Statement stmt = getConnection().createStatement()) {
            stmt.executeUpdate("ALTER TABLE helium_data MODIFY COLUMN nickname VARCHAR(255)");
            stmt.executeUpdate("ALTER TABLE helium_data ADD COLUMN IF NOT EXISTS last_world VARCHAR(64)");
            stmt.executeUpdate("ALTER TABLE helium_data ADD COLUMN IF NOT EXISTS last_x DOUBLE");
            stmt.executeUpdate("ALTER TABLE helium_data ADD COLUMN IF NOT EXISTS last_y DOUBLE");
            stmt.executeUpdate("ALTER TABLE helium_data ADD COLUMN IF NOT EXISTS last_z DOUBLE");
            stmt.executeUpdate("ALTER TABLE helium_data ADD COLUMN IF NOT EXISTS last_yaw FLOAT");
            stmt.executeUpdate("ALTER TABLE helium_data ADD COLUMN IF NOT EXISTS last_pitch FLOAT");
            stmt.executeUpdate("ALTER TABLE helium_data ADD COLUMN IF NOT EXISTS message_volume FLOAT DEFAULT 1.0");
            stmt.executeUpdate("ALTER TABLE helium_data ADD COLUMN IF NOT EXISTS custom_skin VARCHAR(255)");
        } catch (SQLException e) {
        }
        
        connection.commit();
    }

    public Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            initializeConnection();
        }
        return connection;
    }

    private Connection getConnectionWithRetry() throws SQLException {
        int maxRetries = 3;
        for (int i = 0; i < maxRetries; i++) {
            try {
                return getConnection();
            } catch (SQLException e) {
                if (i == maxRetries - 1) throw e;
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new SQLException("Прервано ожидание переподключения", ie);
                }
                initializeConnection();
            }
        }
        throw new SQLException("Не удалось подключиться к БД после " + maxRetries + " попыток");
    }

    private void insertEmpty(UUID uuid) throws SQLException {
        try (PreparedStatement ps = getConnectionWithRetry().prepareStatement(
                "INSERT IGNORE INTO helium_data(uuid) VALUES (?)")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
            connection.commit();
        } catch (SQLException e) {
            if (e.getMessage().contains("Connection reset") || e.getMessage().contains("Communications link failure")) {
                initializeConnection();
                try (PreparedStatement ps2 = getConnectionWithRetry().prepareStatement(
                        "INSERT IGNORE INTO helium_data(uuid) VALUES (?)")) {
                    ps2.setString(1, uuid.toString());
                    ps2.executeUpdate();
                    connection.commit();
                }
            } else {
                throw e;
            }
        }
    }

    public void setAuthIp(UUID uuid, String ip) throws SQLException {
        insertEmpty(uuid);
        try (PreparedStatement ps = getConnectionWithRetry().prepareStatement(
                "UPDATE helium_data SET auth_ip = ? WHERE uuid = ?")) {
            ps.setString(1, ip);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
            connection.commit();
        } catch (SQLException e) {
            if (e.getMessage().contains("Connection reset") || e.getMessage().contains("Communications link failure")) {
                initializeConnection();
                try (PreparedStatement ps2 = getConnectionWithRetry().prepareStatement(
                        "UPDATE helium_data SET auth_ip = ? WHERE uuid = ?")) {
                    ps2.setString(1, ip);
                    ps2.setString(2, uuid.toString());
                    ps2.executeUpdate();
                    connection.commit();
                }
            } else {
                throw e;
            }
        }
    }

    public String getAuthIp(UUID uuid) throws SQLException {
        try (PreparedStatement ps = getConnectionWithRetry().prepareStatement(
                "SELECT auth_ip FROM helium_data WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString(1);
            }
        } catch (SQLException e) {
            if (e.getMessage().contains("Connection reset") || e.getMessage().contains("Communications link failure")) {
                initializeConnection();
                try (PreparedStatement ps2 = getConnectionWithRetry().prepareStatement(
                        "SELECT auth_ip FROM helium_data WHERE uuid = ?")) {
                    ps2.setString(1, uuid.toString());
                    try (ResultSet rs2 = ps2.executeQuery()) {
                        if (rs2.next()) return rs2.getString(1);
                    }
                }
            } else {
                throw e;
            }
        }
        return null;
    }

    public void savePlayerLocation(UUID uuid, String world, double x, double y, double z, float yaw, float pitch) throws SQLException {
        try (PreparedStatement ps = getConnectionWithRetry().prepareStatement(
                "UPDATE helium_data SET last_world = ?, last_x = ?, last_y = ?, last_z = ?, last_yaw = ?, last_pitch = ? WHERE uuid = ?")) {
            ps.setString(1, world);
            ps.setDouble(2, x);
            ps.setDouble(3, y);
            ps.setDouble(4, z);
            ps.setFloat(5, yaw);
            ps.setFloat(6, pitch);
            ps.setString(7, uuid.toString());
            ps.executeUpdate();
            connection.commit();
        } catch (SQLException e) {
            if (e.getMessage().contains("Connection reset") || e.getMessage().contains("Communications link failure")) {
                initializeConnection();
                try (PreparedStatement ps2 = getConnectionWithRetry().prepareStatement(
                        "UPDATE helium_data SET last_world = ?, last_x = ?, last_y = ?, last_z = ?, last_yaw = ?, last_pitch = ? WHERE uuid = ?")) {
                    ps2.setString(1, world);
                    ps2.setDouble(2, x);
                    ps2.setDouble(3, y);
                    ps2.setDouble(4, z);
                    ps2.setFloat(5, yaw);
                    ps2.setFloat(6, pitch);
                    ps2.setString(7, uuid.toString());
                    ps2.executeUpdate();
                    connection.commit();
                }
            } else {
                throw e;
            }
        }
    }

    public Location getPlayerLocation(UUID uuid) throws SQLException {
        try (PreparedStatement ps = getConnectionWithRetry().prepareStatement(
                "SELECT last_world, last_x, last_y, last_z, last_yaw, last_pitch FROM helium_data WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String worldName = rs.getString("last_world");
                    if (worldName != null) {
                        org.bukkit.World world = org.bukkit.Bukkit.getWorld(worldName);
                        if (world != null) {
                            return new org.bukkit.Location(world, rs.getDouble("last_x"), rs.getDouble("last_y"), rs.getDouble("last_z"), rs.getFloat("last_yaw"), rs.getFloat("last_pitch"));
                        }
                    }
                }
            }
        } catch (SQLException e) {
            if (e.getMessage().contains("Connection reset") || e.getMessage().contains("Communications link failure")) {
                initializeConnection();
                try (PreparedStatement ps2 = getConnectionWithRetry().prepareStatement(
                        "SELECT last_world, last_x, last_y, last_z, last_yaw, last_pitch FROM helium_data WHERE uuid = ?")) {
                    ps2.setString(1, uuid.toString());
                    try (ResultSet rs2 = ps2.executeQuery()) {
                        if (rs2.next()) {
                            String worldName = rs2.getString("last_world");
                            if (worldName != null) {
                                org.bukkit.World world = org.bukkit.Bukkit.getWorld(worldName);
                                if (world != null) {
                                    return new org.bukkit.Location(world, rs2.getDouble("last_x"), rs2.getDouble("last_y"), rs2.getDouble("last_z"), rs2.getFloat("last_yaw"), rs2.getFloat("last_pitch"));
                                }
                            }
                        }
                    }
                }
            } else {
                throw e;
            }
        }
        return null;
    }

    public void setTelegramId(UUID uuid, long telegramId) throws SQLException {
        insertEmpty(uuid);
        try (PreparedStatement ps = getConnection().prepareStatement(
                "UPDATE helium_data SET telegram_token = ? WHERE uuid = ?")) {
            ps.setLong(1, telegramId);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
            connection.commit();
        }
    }

    public Long getTelegramId(UUID uuid) throws SQLException {
        try (PreparedStatement ps = getConnection().prepareStatement(
                "SELECT telegram_token FROM helium_data WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong(1);
            }
        }
        return null;
    }

    public void setNickname(UUID uuid, String nickname) throws SQLException {
        insertEmpty(uuid);
        try (PreparedStatement ps = getConnection().prepareStatement(
                "UPDATE helium_data SET nickname = ? WHERE uuid = ?")) {
            ps.setString(1, nickname);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
            connection.commit();
        }
    }

    public String getNickname(UUID uuid) throws SQLException {
        try (PreparedStatement ps = getConnection().prepareStatement(
                "SELECT nickname FROM helium_data WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString(1);
            }
        }
        return null;
    }

    public Long getTelegramIdFromBotDatabase(String nickname) {
        try {
            Connection botConn = DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/helium_bot?useSSL=false&serverTimezone=UTC",
                "helium_user",
                "helium_password"
            );
            try (PreparedStatement ps = botConn.prepareStatement(
                    "SELECT chat_id FROM tokens WHERE nickname = ?")) {
                ps.setString(1, nickname);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getLong(1);
                }
            }
            botConn.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public void removeTelegramLinkByNickname(String nickname) throws SQLException {
        try (PreparedStatement ps = getConnection().prepareStatement(
                "UPDATE helium_data SET telegram_token = NULL WHERE nickname = ?")) {
            ps.setString(1, nickname);
            ps.executeUpdate();
            connection.commit();
        }
    }

    public int getBalance(UUID uuid) throws SQLException {
        try (PreparedStatement ps = getConnection().prepareStatement(
                "SELECT balance FROM helium_data WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        insertEmpty(uuid);
        return 0;
    }

    public void setBalance(UUID uuid, int balance) throws SQLException {
        try (PreparedStatement ps = getConnection().prepareStatement(
                "INSERT INTO helium_data(uuid, balance) VALUES (?, ?) " +
                        "ON DUPLICATE KEY UPDATE balance = VALUES(balance)")) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, balance);
            ps.executeUpdate();
        }
    }

    public Location getHome(UUID uuid) throws SQLException {
        try (PreparedStatement ps = getConnection().prepareStatement(
                "SELECT world, x, y, z, yaw, pitch FROM helium_data WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getString(1) != null) {
                    World world = Bukkit.getWorld(rs.getString(1));
                    if (world == null) return null;
                    return new Location(
                            world, rs.getDouble(2), rs.getDouble(3),
                            rs.getDouble(4), rs.getFloat(5), rs.getFloat(6));
                }
            }
        }
        return null;
    }

    public void setHome(UUID uuid, Location loc) throws SQLException {
        try (PreparedStatement ps = getConnection().prepareStatement(
                "INSERT INTO helium_data(uuid, world, x, y, z, yaw, pitch) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE " +
                        "world=VALUES(world), x=VALUES(x), y=VALUES(y), z=VALUES(z), yaw=VALUES(yaw), pitch=VALUES(pitch)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, loc.getWorld().getName());
            ps.setDouble(3, loc.getX());
            ps.setDouble(4, loc.getY());
            ps.setDouble(5, loc.getZ());
            ps.setFloat(6, loc.getYaw());
            ps.setFloat(7, loc.getPitch());
            ps.executeUpdate();
        }
    }

    public List<FriendInfo> getFriends(UUID uuid) throws SQLException {
        try (PreparedStatement ps = getConnection().prepareStatement(
                "SELECT friends FROM helium_data WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String json = rs.getString(1);
                    if (json != null && !json.isEmpty()) {
                        Type listType = new TypeToken<List<FriendInfo>>() {}.getType();
                        return gson.fromJson(json, listType);
                    }
                }
            }
        }
        return new ArrayList<>();
    }

    public void setFriends(UUID uuid, List<FriendInfo> friends) throws SQLException {
        String json = gson.toJson(friends);
        try (PreparedStatement ps = getConnection().prepareStatement(
                "INSERT INTO helium_data(uuid, friends) VALUES (?, ?) " +
                        "ON DUPLICATE KEY UPDATE friends=VALUES(friends)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, json);
            ps.executeUpdate();
        }
    }

    public boolean isMessagesEnabled(UUID uuid) {
        try (PreparedStatement ps = getConnection().prepareStatement(
                "SELECT messages_enabled FROM helium_data WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        } catch (SQLException e) {
            return true;
        }
    }

    public float getMessageVolume(UUID uuid) {
        try (PreparedStatement ps = getConnection().prepareStatement(
                "SELECT message_volume FROM helium_data WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getFloat(1) : 1.0f;
            }
        } catch (SQLException e) {
            return 1.0f;
        }
    }

    public void setMessageVolume(UUID uuid, float volume) {
        try (PreparedStatement ps = getConnection().prepareStatement(
                "INSERT INTO helium_data(uuid, message_volume) VALUES (?, ?) " +
                        "ON DUPLICATE KEY UPDATE message_volume = VALUES(message_volume)")) {
            ps.setString(1, uuid.toString());
            ps.setFloat(2, volume);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    public void setMessagesEnabled(UUID uuid, boolean enabled) {
        try (PreparedStatement ps = getConnection().prepareStatement(
                "INSERT INTO helium_data(uuid, messages_enabled) VALUES (?, ?) " +
                        "ON DUPLICATE KEY UPDATE messages_enabled = VALUES(messages_enabled)")) {
            ps.setString(1, uuid.toString());
            ps.setBoolean(2, enabled);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    public void setBan(UUID uuid, String reason, long until, String by) throws SQLException {
        try (PreparedStatement ps = getConnection().prepareStatement(
                "INSERT INTO helium_data(uuid, ban_reason, ban_until, ban_by) VALUES (?, ?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE ban_reason=VALUES(ban_reason), ban_until=VALUES(ban_until), ban_by=VALUES(ban_by)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, reason);
            ps.setLong(3, until);
            ps.setString(4, by);
            ps.executeUpdate();
        }
    }

    public boolean isBanned(UUID uuid) throws SQLException {
        try (PreparedStatement ps = getConnection().prepareStatement(
                "SELECT ban_until FROM helium_data WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getLong(1) > System.currentTimeMillis();
            }
        }
    }

    public record BanInfo(String reason, long until, String by) {}

    public BanInfo getBan(UUID uuid) throws SQLException {
        try (PreparedStatement ps = getConnection().prepareStatement(
                "SELECT ban_reason, ban_until, ban_by FROM helium_data WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long until = rs.getLong(2);
                    if (until == 0 || until > System.currentTimeMillis()) {
                        return new BanInfo(rs.getString(1), until, rs.getString(3));
                    }
                }
            }
        }
        return null;
    }

    public void removeBan(UUID uuid) throws SQLException {
        try (PreparedStatement ps = getConnection().prepareStatement(
                "UPDATE helium_data SET ban_reason=NULL, ban_until=NULL, ban_by=NULL WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        }
    }

    public List<UUID> getAllBanned() throws SQLException {
        List<UUID> list = new ArrayList<>();
        try (PreparedStatement ps = getConnection().prepareStatement(
                "SELECT uuid FROM helium_data WHERE ban_until IS NOT NULL AND (ban_until = 0 OR ban_until > ?)")) {
            ps.setLong(1, System.currentTimeMillis());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(UUID.fromString(rs.getString(1)));
                }
            }
        }
        return list;
    }

    public void setMute(UUID uuid, String reason, long until) throws SQLException {
        try (PreparedStatement ps = getConnection().prepareStatement(
                "INSERT INTO helium_data(uuid, mute_reason, mute_until) VALUES (?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE mute_reason=VALUES(mute_reason), mute_until=VALUES(mute_until)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, reason);
            ps.setLong(3, until);
            ps.executeUpdate();
        }
    }

    public boolean isMuted(UUID uuid) throws SQLException {
        try (PreparedStatement ps = getConnection().prepareStatement(
                "SELECT mute_until FROM helium_data WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getLong(1) > System.currentTimeMillis();
            }
        }
    }

    public void removeMute(UUID uuid) throws SQLException {
        try (PreparedStatement ps = getConnection().prepareStatement(
                "UPDATE helium_data SET mute_reason=NULL, mute_until=NULL WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        }
    }

    public Rank getRank(UUID uuid) throws SQLException {
        try (PreparedStatement ps = getConnection().prepareStatement(
                "SELECT rank FROM helium_data WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Rank.valueOf(rs.getString(1)) : Rank.PLAYER;
            }
        }
    }

    public void setRank(UUID uuid, Rank rank) throws SQLException {
        try (PreparedStatement ps = getConnection().prepareStatement(
                "INSERT INTO helium_data(uuid, rank) VALUES (?, ?) " +
                        "ON DUPLICATE KEY UPDATE rank=VALUES(rank)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, rank.name());
            ps.executeUpdate();
        }
    }

    public void setCustomSkin(UUID uuid, String skinValue) throws SQLException {
        insertEmpty(uuid);
        try (PreparedStatement ps = getConnectionWithRetry().prepareStatement(
                "UPDATE helium_data SET custom_skin = ? WHERE uuid = ?")) {
            ps.setString(1, skinValue);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
            connection.commit();
        } catch (SQLException e) {
            if (e.getMessage().contains("Connection reset") || e.getMessage().contains("Communications link failure")) {
                initializeConnection();
                try (PreparedStatement ps2 = getConnectionWithRetry().prepareStatement(
                        "UPDATE helium_data SET custom_skin = ? WHERE uuid = ?")) {
                    ps2.setString(1, skinValue);
                    ps2.setString(2, uuid.toString());
                    ps2.executeUpdate();
                    connection.commit();
                }
            } else {
                throw e;
            }
        }
    }

    public String getCustomSkin(UUID uuid) throws SQLException {
        try (PreparedStatement ps = getConnectionWithRetry().prepareStatement(
                "SELECT custom_skin FROM helium_data WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString(1);
            }
        } catch (SQLException e) {
            if (e.getMessage().contains("Connection reset") || e.getMessage().contains("Communications link failure")) {
                initializeConnection();
                try (PreparedStatement ps2 = getConnectionWithRetry().prepareStatement(
                        "SELECT custom_skin FROM helium_data WHERE uuid = ?")) {
                    ps2.setString(1, uuid.toString());
                    try (ResultSet rs2 = ps2.executeQuery()) {
                        if (rs2.next()) return rs2.getString(1);
                    }
                }
            } else {
                throw e;
            }
        }
        return null;
    }

    public void clearCustomSkin(UUID uuid) throws SQLException {
        try (PreparedStatement ps = getConnectionWithRetry().prepareStatement(
                "UPDATE helium_data SET custom_skin = NULL WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
            connection.commit();
        } catch (SQLException e) {
            if (e.getMessage().contains("Connection reset") || e.getMessage().contains("Communications link failure")) {
                initializeConnection();
                try (PreparedStatement ps2 = getConnectionWithRetry().prepareStatement(
                        "UPDATE helium_data SET custom_skin = NULL WHERE uuid = ?")) {
                    ps2.setString(1, uuid.toString());
                    ps2.executeUpdate();
                    connection.commit();
                }
            } else {
                throw e;
            }
        }
    }

    public void close() {
        isShutdown = true;
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Ошибка закрытия соединения с БД: " + e.getMessage());
        }
    }
}