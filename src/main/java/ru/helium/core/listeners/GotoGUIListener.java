package ru.helium.core.listeners;

import ru.helium.core.HeliumCore;
import ru.helium.core.database.HeliumDatabase;
import ru.helium.core.gui.GotoGUI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.sql.SQLException;
import java.util.Random;

public class GotoGUIListener implements Listener {

    private final HeliumCore plugin;
    private final HeliumDatabase heliumDatabase;
    private final NamespacedKey idKey;
    private final NamespacedKey navKey;
    private final GotoGUI gotoGUI;

    public GotoGUIListener(HeliumCore plugin, HeliumDatabase heliumDatabase) {
        this.plugin = plugin;
        this.heliumDatabase = heliumDatabase;
        this.idKey = new NamespacedKey(plugin, "goto_id");
        this.navKey = new NamespacedKey(plugin, "goto_nav");
        this.gotoGUI = new GotoGUI(plugin);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        if (!title.startsWith("Телепортация")) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;

        if (meta.getPersistentDataContainer().has(idKey, PersistentDataType.STRING)) {
            String id = meta.getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
            if (id == null) return;

            if ("home".equalsIgnoreCase(id)) {
                try {
                    Location homeLocation = heliumDatabase.getHome(player.getUniqueId());
                    if (homeLocation == null) {
                        player.sendMessage(Component.text("Дом не установлен. Используйте /sethome", NamedTextColor.RED));
                        return;
                    }

                    teleportPlayer(player, homeLocation, Component.text("Вы телепортированы домой!").color(NamedTextColor.GRAY));
                } catch (SQLException e) {
                    plugin.getLogger().severe("Ошибка при получении дома из базы: " + e.getMessage());
                    player.sendMessage(Component.text("Ошибка при получении дома.", NamedTextColor.RED));
                }
                return;
            }

            if ("world".equalsIgnoreCase(id)) {
                World world = Bukkit.getWorld("survival");
                if (world != null) {
                    Random random = new Random();
                    int radius = 1000;
                    int x = random.nextInt(radius * 2) - radius;
                    int z = random.nextInt(radius * 2) - radius;
                    int y = world.getHighestBlockYAt(x, z) + 1;

                    Location location = new Location(world, x + 0.5, y, z + 0.5);
                    teleportPlayer(player, location, Component.text("Вы телепортированы в случайную точку мира.").color(NamedTextColor.GRAY));
                } else {
                    player.sendMessage(Component.text("Мир не найден.", NamedTextColor.RED));
                }
                return;
            }

            ConfigurationSection section = plugin.getConfig().getConfigurationSection("goto.points." + id);
            if (section != null) {
                String worldName = section.getString("world");
                if (worldName == null) {
                    player.sendMessage(Component.text("Мир для точки телепортации не найден.", NamedTextColor.RED));
                    return;
                }

                World world = Bukkit.getWorld(worldName);
                if (world == null) {
                    player.sendMessage(Component.text("Мир для точки телепортации не найден.", NamedTextColor.RED));
                    return;
                }

                Location loc = new Location(world,
                        section.getDouble("x"),
                        section.getDouble("y"),
                        section.getDouble("z"),
                        (float) section.getDouble("yaw"),
                        (float) section.getDouble("pitch"));

                String name = section.getString("name");
                if (name == null) name = id;

                teleportPlayer(player, loc,
                        Component.text("Телепортация к точке ").color(NamedTextColor.GRAY)
                                .append(Component.text(name).color(NamedTextColor.WHITE)));
            }

        } else if (meta.getPersistentDataContainer().has(navKey, PersistentDataType.STRING)) {
            String nav = meta.getPersistentDataContainer().get(navKey, PersistentDataType.STRING);
            if (nav == null) return;

            int currentPage = 0;
            try {
                if (title.contains("Стр. ")) {
                    String pageStr = title.substring(title.indexOf("Стр. ") + 5);
                    currentPage = Integer.parseInt(pageStr) - 1;
                }
            } catch (NumberFormatException ignored) {}

            if ("prev".equals(nav)) gotoGUI.open(player, currentPage - 1);
            if ("next".equals(nav)) gotoGUI.open(player, currentPage + 1);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        if (title.startsWith("Телепортация")) {
            event.setCancelled(true);
        }
    }

    private void teleportPlayer(Player player, Location location, Component message) {
        player.closeInventory();
        player.teleport(location);
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.0f);
        player.sendMessage(message);
    }
}