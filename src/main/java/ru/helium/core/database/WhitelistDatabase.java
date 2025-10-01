package ru.helium.core.database;

import org.bukkit.Bukkit;

import java.sql.*;
import java.util.UUID;

public class WhitelistDatabase {

    private final String url;
    private Connection connection;

    public WhitelistDatabase(String filePath) {
        this.url = "jdbc:sqlite:" + filePath;
        init();
    }

    private void init() {
        try {
            connection = DriverManager.getConnection(url);
            try (Statement stmt = connection.createStatement()) {
                stmt.executeUpdate("CREATE TABLE IF NOT EXISTS whitelist (uuid TEXT PRIMARY KEY)");
            }
        } catch (SQLException e) {
            Bukkit.getLogger().severe("Не удалось подключиться к базе whitelist: " + e.getMessage());
        }
    }

    public boolean isWhitelisted(UUID uuid) {
        try (PreparedStatement stmt = connection.prepareStatement("SELECT uuid FROM whitelist WHERE uuid = ?")) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            return false;
        }
    }

    public boolean add(UUID uuid) {
        if (isWhitelisted(uuid)) return false;
        try (PreparedStatement stmt = connection.prepareStatement("INSERT INTO whitelist(uuid) VALUES(?)")) {
            stmt.setString(1, uuid.toString());
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    public boolean remove(UUID uuid) {
        try (PreparedStatement stmt = connection.prepareStatement("DELETE FROM whitelist WHERE uuid = ?")) {
            stmt.setString(1, uuid.toString());
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }
}