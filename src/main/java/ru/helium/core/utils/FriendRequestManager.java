package ru.helium.core.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;

public class FriendRequestManager {

    private final Map<UUID, Set<UUID>> requests = new HashMap<>();
    private final Map<String, Long> requestTimestamps = new HashMap<>();
    private static final long EXPIRE_AFTER_MILLIS = 2 * 60 * 1000; // 2 минуты

    public void sendRequest(Player from, Player to) {
        UUID toUUID = to.getUniqueId();
        UUID fromUUID = from.getUniqueId();
        String key = requestKey(fromUUID, toUUID);

        requests.computeIfAbsent(toUUID, k -> new HashSet<>()).add(fromUUID);
        requestTimestamps.put(key, System.currentTimeMillis());

        TextComponent message = Component.text("Игрок ")
                .append(Component.text(from.getName(), NamedTextColor.AQUA))
                .append(Component.text(" хочет добавить вас в друзья.\n"))
                .append(Component.text("[Принять]", NamedTextColor.GREEN)
                        .clickEvent(ClickEvent.runCommand("/friend accept " + from.getName())))
                .append(Component.text(" "))
                .append(Component.text("[Отклонить]", NamedTextColor.RED)
                        .clickEvent(ClickEvent.runCommand("/friend decline " + from.getName())));

        to.sendMessage(message);
    }

    public boolean hasRequest(Player to, Player from) {
        UUID toUUID = to.getUniqueId();
        UUID fromUUID = from.getUniqueId();
        String key = requestKey(fromUUID, toUUID);

        Set<UUID> fromSet = requests.get(toUUID);
        if (fromSet == null || !fromSet.contains(fromUUID)) return false;

        Long sentAt = requestTimestamps.get(key);
        if (sentAt != null && System.currentTimeMillis() - sentAt > EXPIRE_AFTER_MILLIS) {
            fromSet.remove(fromUUID);
            requestTimestamps.remove(key);
            if (fromSet.isEmpty()) requests.remove(toUUID);

            Player fromPlayer = Bukkit.getPlayer(fromUUID);
            if (fromPlayer != null && fromPlayer.isOnline()) {
                fromPlayer.sendMessage(Component.text("Ваша заявка к " + to.getName() + " истекла.", NamedTextColor.GRAY));
            }
            return false;
        }
        return true;
    }

    public boolean acceptRequest(Player to, Player from) {
        if (!hasRequest(to, from)) {
            to.sendMessage(Component.text("У вас нет действительной заявки от " + from.getName(), NamedTextColor.RED));
            return false;
        }

        UUID toUUID = to.getUniqueId();
        UUID fromUUID = from.getUniqueId();
        requests.get(toUUID).remove(fromUUID);
        requestTimestamps.remove(requestKey(fromUUID, toUUID));
        if (requests.get(toUUID).isEmpty()) requests.remove(toUUID);
        return true;
    }

    public boolean declineRequest(Player to, Player from) {
        if (!hasRequest(to, from)) {
            to.sendMessage(Component.text("У вас нет действительной заявки от " + from.getName(), NamedTextColor.RED));
            return false;
        }

        UUID toUUID = to.getUniqueId();
        UUID fromUUID = from.getUniqueId();
        requests.get(toUUID).remove(fromUUID);
        requestTimestamps.remove(requestKey(fromUUID, toUUID));
        if (requests.get(toUUID).isEmpty()) requests.remove(toUUID);
        return true;
    }

    public void cancelAllRequests(Player to) {
        UUID toUUID = to.getUniqueId();
        Set<UUID> set = requests.remove(toUUID);
        if (set != null) {
            for (UUID fromUUID : set) {
                requestTimestamps.remove(requestKey(fromUUID, toUUID));
            }
        }
    }

    private String requestKey(UUID from, UUID to) {
        return from.toString() + ":" + to.toString();
    }
}