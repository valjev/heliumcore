package ru.helium.core.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

public class CommandCooldown implements Listener {

    private static final Map<String, Integer> commandCooldowns = new HashMap<>() {{
        put("goto", 3);
        put("g", 3);
        put("lobby", 15);
        put("home", 15);
        put("sethome", 15);
        put("tpa", 15);
        put("status", 15);
        put("ping", 15);
        put("f", 3);
        put("friend", 3);
        put("friends", 3);
        put("help", 3);
        put("skin", 3);
        put("coin", 3);
        put("money", 3);
        put("c", 3);
        put("say", 3);
        put("msg", 3);
    }};

    private static final Map<String, Long> cooldowns = new HashMap<>();

    @EventHandler
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (player.isOp() || player.hasPermission("helium.admin")) return;

        String message = event.getMessage().toLowerCase();
        if (!message.startsWith("/")) return;

        String commandName = message.substring(1).split(" ")[0];
        if (!commandCooldowns.containsKey(commandName)) return;

        int cooldownSeconds = commandCooldowns.get(commandName);
        String key = player.getUniqueId() + ":" + commandName;
        long now = System.currentTimeMillis();

        if (cooldowns.containsKey(key)) {
            long lastUse = cooldowns.get(key);
            long elapsed = (now - lastUse) / 1000;

            if (elapsed < cooldownSeconds) {
                long remaining = cooldownSeconds - elapsed;
                player.sendMessage(
                        Component.text("Подождите ")
                                .append(Component.text(remaining).color(NamedTextColor.YELLOW))
                                .append(Component.text(" сек. перед повторным использованием команды."))
                                .color(NamedTextColor.RED)
                );
                event.setCancelled(true);
                return;
            }
        }

        cooldowns.put(key, now);
    }
}