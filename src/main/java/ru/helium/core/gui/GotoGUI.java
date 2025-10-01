package ru.helium.core.gui;

import ru.helium.core.HeliumCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class GotoGUI {

    private final HeliumCore plugin;
    private final NamespacedKey gotoIdKey;
    private final NamespacedKey gotoNavKey;

    private final LegacyComponentSerializer legacySerializer = LegacyComponentSerializer.legacyAmpersand();

    public GotoGUI(HeliumCore plugin) {
        this.plugin = plugin;
        this.gotoIdKey = new NamespacedKey(plugin, "goto_id");
        this.gotoNavKey = new NamespacedKey(plugin, "goto_nav");
    }

    public void open(Player player, int page) {
        FileConfiguration config = plugin.getConfig();
        ConfigurationSection points = config.getConfigurationSection("goto.points");

        if (points == null) {
            player.sendMessage(Component.text("Нет доступных точек для телепортации.", NamedTextColor.RED));
            return;
        }

        List<String> keys = new ArrayList<>(points.getKeys(false));
        keys.removeIf(key -> {
            ConfigurationSection section = points.getConfigurationSection(key);
            return section == null || !section.getBoolean("enabled", true);
        });

        int itemsPerPage = 45;
        int maxPage = (keys.size() - 1) / itemsPerPage;

        if (page < 0) page = 0;
        if (page > maxPage) page = maxPage;

        Inventory gui = Bukkit.createInventory(player, 54, legacySerializer.deserialize("Телепортация"));

        int startIndex = page * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, keys.size());

        for (int i = startIndex; i < endIndex; i++) {
            String key = keys.get(i);
            ConfigurationSection section = points.getConfigurationSection(key);
            if (section == null) continue;

            String iconName = section.getString("icon");
            if (iconName == null || iconName.equalsIgnoreCase("AIR") || iconName.equalsIgnoreCase("NONE")) continue;

            Material material = Material.matchMaterial(iconName);
            if (material == null) continue;

            int slot = section.getInt("slot", -1);
            if (slot < 0 || slot >= 54) {
                slot = i - startIndex;
            }

            String name = section.getString("name", key);
            List<String> lore = section.getStringList("lore");

            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            if (meta == null) continue;

            meta.displayName(Component.text(name).color(NamedTextColor.YELLOW));

            List<Component> loreComponents = new ArrayList<>();
            for (String line : lore) {
                loreComponents.add(legacySerializer.deserialize(line));
            }
            meta.lore(loreComponents);

            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            meta.getPersistentDataContainer().set(gotoIdKey, PersistentDataType.STRING, key);

            item.setItemMeta(meta);
            gui.setItem(slot, item);
        }

        if (page > 0) {
            gui.setItem(45, createNavItem("← Пред.", "prev"));
        }
        if (page < maxPage) {
            gui.setItem(53, createNavItem("След. →", "next"));
        }

        player.openInventory(gui);
    }

    private ItemStack createNavItem(String name, String id) {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name).color(NamedTextColor.GREEN));
            meta.getPersistentDataContainer().set(gotoNavKey, PersistentDataType.STRING, id);
            item.setItemMeta(meta);
        }
        return item;
    }
}