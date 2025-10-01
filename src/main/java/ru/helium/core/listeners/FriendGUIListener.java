package ru.helium.core.listeners;

import ru.helium.core.HeliumCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class FriendGUIListener implements Listener {

    private final HeliumCore plugin;
    private final Map<UUID, Long> teleportCooldowns = new HashMap<>();
    private static final long COOLDOWN_MILLIS = 15 * 1000L;

    public FriendGUIListener(HeliumCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        if (!title.contains("Ваши друзья")) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() != Material.PLAYER_HEAD) return;

        SkullMeta meta = (SkullMeta) clicked.getItemMeta();
        if (meta == null || meta.getOwningPlayer() == null) return;

        Player friend = meta.getOwningPlayer().getPlayer();
        if (friend == null || !friend.isOnline()) {
            player.sendMessage(Component.text("Друг сейчас оффлайн.", NamedTextColor.RED));
            return;
        }

        UUID playerUUID = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long lastTeleport = teleportCooldowns.get(playerUUID);
        if (lastTeleport != null && now - lastTeleport < COOLDOWN_MILLIS) {
            long secondsLeft = (COOLDOWN_MILLIS - (now - lastTeleport)) / 1000;
            player.sendMessage(Component.text("Подождите " + secondsLeft + " секунд перед следующим телепортом.", NamedTextColor.RED));
            return;
        }

        player.closeInventory();
        player.teleport(friend.getLocation());
        player.sendMessage(Component.text("Вы телепортировались к другу " + friend.getName(), NamedTextColor.GREEN));
        teleportCooldowns.put(playerUUID, now);
    }
}