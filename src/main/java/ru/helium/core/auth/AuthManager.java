package ru.helium.core.auth;

import ru.helium.core.HeliumCore;
import ru.helium.core.utils.RankManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.title.Title.Times;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;
import io.papermc.paper.event.player.AsyncChatEvent;

import java.sql.SQLException;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class AuthManager implements Listener {

    private final HeliumCore plugin;
    private final RankManager rankManager;
    public final Set<UUID> frozenPlayers = new HashSet<>();
    private final Map<UUID, Long> lastJoinTimes = new HashMap<>();
    private static final long REJOIN_COOLDOWN = 10000L;
    private AuthHttpServer httpServer;

    public AuthManager(HeliumCore plugin, RankManager rankManager) {
        this.plugin = plugin;
        this.rankManager = rankManager;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        try {
            httpServer = new AuthHttpServer(this);
            httpServer.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        startCleanupTask();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String nickname = player.getName();

        if (isRejoinTooFast(uuid) && !player.hasPermission("helium.admin") && !player.hasPermission("helium.mod")) {
            player.kick(Component.text("Слишком частые перезаходы!\n\nПодождите 10 секунд перед следующим входом.\nПри повторных нарушениях возможна блокировка.").color(NamedTextColor.RED));
            return;
        }
        
        lastJoinTimes.put(uuid, System.currentTimeMillis());

        String ip = player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : "";

        Long telegramId = null;
        String authIp = null;

        try {
            telegramId = plugin.getDatabase().getTelegramId(uuid);
            authIp = plugin.getDatabase().getAuthIp(uuid);
            
            plugin.getDatabase().setNickname(uuid, nickname);
        } catch (SQLException e) {
            e.printStackTrace();
            player.kick(Component.text("Ошибка доступа к базе данных.\nСвяжитесь с администрацией: https://t.me/HeliumGameBot").color(NamedTextColor.RED));
            return;
        }

        if (telegramId != null && ip.equals(authIp)) {
            frozenPlayers.remove(uuid);
            
            stopAllMusic(player);
            
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                player.setGameMode(GameMode.SURVIVAL);
                
                try {
                    org.bukkit.Location savedLocation = plugin.getDatabase().getPlayerLocation(uuid);
                    if (savedLocation != null) {
                        player.teleport(savedLocation);
                    } else {
                        player.teleport(plugin.getLobbyLocation());
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                    player.teleport(plugin.getLobbyLocation());
                }
            }, 1L);

            clearChat(player);
            showWelcome(player);
            event.joinMessage(Component.empty());
        } else {
            frozenPlayers.add(uuid);
            
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                player.setGameMode(GameMode.SPECTATOR);
                player.teleport(plugin.getAuthLocation());
                player.playSound(player.getLocation(), Sound.MUSIC_DISC_CREATOR, 0.5f, 1f);
                startAuthMusic(player);
            }, 1L);

            Title title = Title.title(
                    Component.text("⏳ Авторизация", NamedTextColor.RED),
                    Component.text("Чтобы войти, нажми на кнопку в чате", NamedTextColor.GRAY),
                    Times.times(Duration.ofMillis(500), Duration.ofSeconds(9999), Duration.ofMillis(500))
            );
            player.showTitle(title);

            sendAuthMessage(player, nickname);
            event.joinMessage(Component.empty());
            
            startAuthMessageTask(player, nickname);
        }

        rankManager.onPlayerJoin(player);
    }

    private void sendAuthMessage(Player player, String nickname) {
        String encryptedNickname = encryptNickname(nickname);
        Component clickable = Component.text("[Авторизоваться]", NamedTextColor.GREEN)
                .clickEvent(ClickEvent.openUrl("https://t.me/HeliumGameBot?start=" + encryptedNickname));

        player.sendMessage(Component.text(" "));
        player.sendMessage(Component.text("🔒 Для входа на сервер требуется авторизация", NamedTextColor.RED));
        player.sendMessage(Component.text("Нажми на кнопку: ", NamedTextColor.GRAY).append(clickable));
        player.sendMessage(Component.text(" "));
    }
    
    private String encryptNickname(String nickname) {
        try {
            String key = "Helium2024";
            StringBuilder encrypted = new StringBuilder();
            for (int i = 0; i < nickname.length(); i++) {
                char c = nickname.charAt(i);
                char k = key.charAt(i % key.length());
                encrypted.append((char) (c ^ k));
            }
            return Base64.getUrlEncoder().withoutPadding().encodeToString(encrypted.toString().getBytes("UTF-8"));
        } catch (Exception e) {
            return nickname;
        }
    }


    public void clearChat(Player player) {
        for (int i = 0; i < 100; i++) {
            player.sendMessage(Component.text(" "));
        }
    }

    public void showWelcome(Player player) {
        Bukkit.broadcast(Component.text(player.getName() + " пробудился в этом мире", NamedTextColor.GRAY));
        Title title = Title.title(
                Component.text("Добро пожаловать в ", NamedTextColor.WHITE)
                        .append(Component.text("HELIUM", NamedTextColor.YELLOW))
                        .append(Component.text("!", NamedTextColor.WHITE)),
                Component.text("Прибыл новый герой — встречайте " + player.getName() + "!", NamedTextColor.WHITE),
                Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(500))
        );
        player.showTitle(title);
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1f);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (isFrozen(event.getPlayer().getUniqueId())) {
            if (event.getFrom().getX() != event.getTo().getX() ||
                event.getFrom().getY() != event.getTo().getY() ||
                event.getFrom().getZ() != event.getTo().getZ()) {
                event.setTo(event.getFrom());
            }
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (isFrozen(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        if (isFrozen(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (isFrozen(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        
        if (!isFrozen(uuid)) {
            try {
                plugin.getDatabase().savePlayerLocation(
                    uuid,
                    player.getWorld().getName(),
                    player.getLocation().getX(),
                    player.getLocation().getY(),
                    player.getLocation().getZ(),
                    player.getLocation().getYaw(),
                    player.getLocation().getPitch()
                );
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        
        frozenPlayers.remove(uuid);
        event.quitMessage(Component.text(player.getName() + " покинул этот мир", NamedTextColor.GRAY));
    }

    public boolean isFrozen(UUID uuid) {
        return frozenPlayers.contains(uuid);
    }

    private boolean isRejoinTooFast(UUID uuid) {
        Long lastJoinTime = lastJoinTimes.get(uuid);
        return lastJoinTime != null && (System.currentTimeMillis() - lastJoinTime) < REJOIN_COOLDOWN;
    }

    public void confirmAuth(Player player) {
        UUID uuid = player.getUniqueId();
        String ip = player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : "";
        String nickname = player.getName();

        try {
            Long telegramId = plugin.getDatabase().getTelegramIdFromBotDatabase(nickname);
            if (telegramId != null) {
                plugin.getDatabase().setTelegramId(uuid, telegramId);
            }
            plugin.getDatabase().setAuthIp(uuid, ip);
            plugin.getDatabase().setNickname(uuid, nickname);
        } catch (SQLException e) {
            e.printStackTrace();
        }

        frozenPlayers.remove(uuid);
        
        stopAllMusic(player);
        
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                player.setGameMode(GameMode.SURVIVAL);
                
                try {
                    org.bukkit.Location savedLocation = plugin.getDatabase().getPlayerLocation(uuid);
                    if (savedLocation != null) {
                        player.teleport(savedLocation);
                    } else {
                        player.teleport(plugin.getLobbyLocation());
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                    player.teleport(plugin.getLobbyLocation());
                }
            }, 1L);

        clearChat(player);
        showWelcome(player);
    }

    public void reauthPlayer(Player player) {
        String nickname = player.getName();
        sendAuthMessage(player, nickname);
        player.sendMessage(Component.text("🔄 IP-адрес изменился. Требуется повторная авторизация.", NamedTextColor.YELLOW));
    }

    public HeliumCore getPlugin() {
        return plugin;
    }

    public void stopHttpServer() {
        if (httpServer != null) {
            httpServer.stop();
        }
    }
    
    private void stopAllMusic(Player player) {
        player.stopSound(Sound.MUSIC_DISC_CREATOR);
    }

    private void startAuthMusic(Player player) {
        Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                if (!player.isOnline() || !isFrozen(player.getUniqueId())) {
                    return;
                }
                player.playSound(player.getLocation(), Sound.MUSIC_DISC_CREATOR, 0.5f, 1f);
            }
        }, 20L * 100L, 20L * 100L);
    }

    private void startCleanupTask() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            long currentTime = System.currentTimeMillis();
            lastJoinTimes.entrySet().removeIf(entry -> (currentTime - entry.getValue()) > REJOIN_COOLDOWN * 2);
        }, 20L * 60L, 20L * 60L);
    }

    private void startAuthMessageTask(Player player, String nickname) {
        Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                if (!player.isOnline() || !isFrozen(player.getUniqueId())) {
                    return;
                }
                sendAuthMessage(player, nickname);
            }
        }, 200L, 200L);
    }
}