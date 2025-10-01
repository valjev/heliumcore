package ru.helium.core.listeners;

import ru.helium.core.HeliumCore;
import ru.helium.core.auth.AuthManager;
import ru.helium.core.utils.Rank;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ChatListener implements Listener {

    private static final int LOCAL_CHAT_RADIUS = 50;
    private static final long SPAM_COOLDOWN_MS = 3 * 1000L;

    private final Map<UUID, Long> lastMessageTime = new HashMap<>();
    private final HeliumCore plugin;
    private final AuthManager authManager;
    private final LegacyComponentSerializer legacySerializer = LegacyComponentSerializer.legacySection();

    public ChatListener(HeliumCore plugin, AuthManager authManager) {
        this.plugin = plugin;
        this.authManager = authManager;
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player sender = event.getPlayer();

        if (authManager.isFrozen(sender.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        try {
            if (plugin.getDatabase().isMuted(sender.getUniqueId())) {
                event.setCancelled(true);
                sender.sendMessage(Component.text("Вы замьючены и не можете писать в чат.", NamedTextColor.RED));
                return;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Ошибка проверки мута: " + e.getMessage());
        }

        if (!sender.hasPermission("helium.admin") && !sender.isOp()) {
            long now = System.currentTimeMillis();
            Long last = lastMessageTime.get(sender.getUniqueId());
            if (last != null && now - last < SPAM_COOLDOWN_MS) {
                event.setCancelled(true);
                long secondsLeft = (SPAM_COOLDOWN_MS - (now - last)) / 1000;
                sender.sendMessage(Component.text("Пожалуйста, подождите " + secondsLeft + " секунд перед отправкой следующего сообщения.", NamedTextColor.RED));
                return;
            }
            lastMessageTime.put(sender.getUniqueId(), now);
        }

        String message = event.getMessage();

        Rank rank = Rank.PLAYER;
        try {
            rank = plugin.getDatabase().getRank(sender.getUniqueId());
        } catch (SQLException e) {
            plugin.getLogger().severe("Ошибка получения ранга для чата: " + e.getMessage());
        }

        Component prefixComponent = legacySerializer.deserialize(rank.getPrefix());

        event.setCancelled(true);

        if (message.startsWith("!")) {
            String globalMsg = message.substring(1).trim();

            Component formatted = Component.text("[Г] ", NamedTextColor.YELLOW)
                    .append(prefixComponent)
                    .append(Component.text(sender.getName(), NamedTextColor.WHITE))
                    .append(Component.text(": ", NamedTextColor.GRAY))
                    .append(Component.text(globalMsg, NamedTextColor.WHITE));

            Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(formatted));
        } else {
            Component formatted = Component.text("[Л] ", NamedTextColor.GREEN)
                    .append(prefixComponent)
                    .append(Component.text(sender.getName(), NamedTextColor.WHITE))
                    .append(Component.text(": ", NamedTextColor.GRAY))
                    .append(Component.text(message, NamedTextColor.WHITE));

            sender.getWorld().getPlayers().stream()
                    .filter(p -> p.getLocation().distanceSquared(sender.getLocation()) <= LOCAL_CHAT_RADIUS * LOCAL_CHAT_RADIUS)
                    .forEach(p -> p.sendMessage(formatted));
        }
    }
}