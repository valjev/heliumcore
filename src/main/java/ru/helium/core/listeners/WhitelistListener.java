package ru.helium.core.listeners;

import ru.helium.core.database.WhitelistDatabase;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import java.util.UUID;

public class WhitelistListener implements Listener {

    private final WhitelistDatabase db;

    public WhitelistListener(WhitelistDatabase db) {
        this.db = db;
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        UUID uuid = event.getUniqueId();
        if (!db.isWhitelisted(uuid)) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_WHITELIST,
                    LegacyComponentSerializer.legacyAmpersand().deserialize("§cОй! Без верификации не пускаем :(\n§7Загляни в Telegram-бота: §b@HeliumGameBot"));
        }
    }
}