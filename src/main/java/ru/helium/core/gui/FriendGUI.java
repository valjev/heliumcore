package ru.helium.core.gui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import ru.helium.core.HeliumCore;
import ru.helium.core.database.HeliumDatabase;
import ru.helium.core.utils.FriendInfo;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.skinsrestorer.api.SkinsRestorer;
import net.skinsrestorer.api.SkinsRestorerProvider;
import net.skinsrestorer.api.property.SkinProperty;
import net.skinsrestorer.api.storage.PlayerStorage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class FriendGUI {

    private static final LegacyComponentSerializer serializer = LegacyComponentSerializer.legacySection();
    private static final ConcurrentHashMap<UUID, PlayerProfile> skinCache = new ConcurrentHashMap<>();
    private static SkinsRestorer skinRestorerApi;

    public static void init() {
        if (Bukkit.getPluginManager().getPlugin("SkinsRestorer") != null) {
            skinRestorerApi = SkinsRestorerProvider.get();
            Bukkit.getLogger().info("[FriendGUI] SkinsRestorer API найден и инициализирован.");
        } else {
            Bukkit.getLogger().info("[FriendGUI] SkinsRestorer не найден, будет использоваться Mojang API.");
        }
    }

    public static void open(Player viewer, HeliumCore plugin) {
        HeliumDatabase db = plugin.getDatabase();
        List<FriendInfo> friends;
        try {
            friends = db.getFriends(viewer.getUniqueId());
        } catch (SQLException e) {
            viewer.sendMessage(Component.text("Произошла ошибка при загрузке друзей.", NamedTextColor.RED));
            return;
        }

        if (friends.isEmpty()) {
            viewer.sendMessage(Component.text("У вас нет друзей.", NamedTextColor.RED));
            return;
        }

        List<FriendInfo> sortedFriends = friends.stream()
                .sorted(Comparator.comparing((FriendInfo f) -> {
                    OfflinePlayer p = Bukkit.getOfflinePlayer(f.getUuid());
                    return !p.isOnline();
                }).thenComparingLong(FriendInfo::getAddedAt))
                .collect(Collectors.toList());

        int size = ((sortedFriends.size() - 1) / 9 + 1) * 9;
        String title = serializer.serialize(Component.text("Ваши друзья"));
        Inventory inv = Bukkit.createInventory(null, size, title);

        for (FriendInfo friend : sortedFriends) {
            OfflinePlayer offlineFriend = Bukkit.getOfflinePlayer(friend.getUuid());
            boolean online = offlineFriend.isOnline();

            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            if (meta != null) {
                applySkin(meta, offlineFriend.getUniqueId());

                meta.setDisplayName(serializer.serialize(Component.text(
                        offlineFriend.getName() == null ? "Unknown" : offlineFriend.getName(),
                        NamedTextColor.WHITE)));

                Component onlineStatus = Component.text(online ? "Онлайн" : "Оффлайн",
                        online ? NamedTextColor.GREEN : NamedTextColor.GRAY);
                Component addedAt = Component.text("Добавлен: " + friend.getAddedAtMoscow(), NamedTextColor.DARK_GRAY);

                meta.lore(List.of(onlineStatus, addedAt));
                skull.setItemMeta(meta);
            }

            inv.addItem(skull);
        }

        viewer.openInventory(inv);
    }

    private static void applySkin(SkullMeta meta, UUID uuid) {
        if (skinCache.containsKey(uuid)) {
            meta.setPlayerProfile(skinCache.get(uuid));
            return;
        }

        if (skinRestorerApi != null) {
            try {
                PlayerStorage playerStorage = skinRestorerApi.getPlayerStorage();
                Optional<SkinProperty> skinPropertyOpt = playerStorage.getSkinForPlayer(uuid, null);
                if (skinPropertyOpt.isPresent()) {
                    SkinProperty skinProperty = skinPropertyOpt.get();
                    PlayerProfile profile = Bukkit.getServer().createProfile(uuid, null);
                    profile.setProperties(List.of(new ProfileProperty("textures", skinProperty.getValue(), skinProperty.getSignature())));
                    skinCache.put(uuid, profile);
                    meta.setPlayerProfile(profile);
                    return;
                }
            } catch (Exception e) {
                Bukkit.getLogger().warning("[FriendGUI] Ошибка получения скина через SkinsRestorer API: " + e.getMessage());
            }
        }

        try {
            PlayerProfile profile = fetchProfile(uuid);
            if (profile != null) {
                skinCache.put(uuid, profile);
                meta.setPlayerProfile(profile);
            }
        } catch (Exception e) {
            Bukkit.getLogger().warning("[FriendGUI] Не удалось загрузить скин для UUID: " + uuid + " — " + e.getMessage());
        }
    }

    private static PlayerProfile fetchProfile(UUID uuid) throws Exception {
        String urlStr = "https://sessionserver.mojang.com/session/minecraft/profile/" + uuid.toString().replace("-", "") + "?unsigned=false";
        URL url = new URL(urlStr);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(3000);
        connection.setReadTimeout(3000);
        connection.setRequestMethod("GET");

        if (connection.getResponseCode() == 200) {
            try (InputStreamReader reader = new InputStreamReader(connection.getInputStream())) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                String name = json.get("name").getAsString();

                PlayerProfile profile = Bukkit.getServer().createProfile(uuid, name);
                List<ProfileProperty> properties = new ArrayList<>();
                for (JsonElement element : json.getAsJsonArray("properties")) {
                    JsonObject prop = element.getAsJsonObject();
                    String propName = prop.get("name").getAsString();
                    String propValue = prop.get("value").getAsString();
                    String propSignature = prop.has("signature") ? prop.get("signature").getAsString() : null;
                    properties.add(new ProfileProperty(propName, propValue, propSignature));
                }
                profile.setProperties(properties);
                return profile;
            }
        }
        return null;
    }
}