package ru.helium.core.listeners;

import ru.helium.core.HeliumCore;
import ru.helium.core.database.HeliumDatabase;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.skinsrestorer.api.SkinsRestorer;
import net.skinsrestorer.api.SkinsRestorerProvider;
import net.skinsrestorer.api.property.InputDataResult;
import net.skinsrestorer.api.property.SkinIdentifier;
import net.skinsrestorer.api.storage.PlayerStorage;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class SkinListener implements Listener {

    private final HeliumCore plugin;
    private final HeliumDatabase database;
    private final SkinsRestorer skinsRestorer;

    public SkinListener(HeliumCore plugin) {
        this.plugin = plugin;
        this.database = plugin.getDatabase();
        this.skinsRestorer = SkinsRestorerProvider.get();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        CompletableFuture.runAsync(() -> {
            try {
                String savedSkin = database.getCustomSkin(player.getUniqueId());
                if (savedSkin != null && !savedSkin.isEmpty()) {
                    PlayerStorage playerStorage = skinsRestorer.getPlayerStorage();
                    Optional<InputDataResult> skinDataOpt = skinsRestorer.getSkinStorage().findOrCreateSkinData(savedSkin);

                    if (skinDataOpt.isPresent()) {
                        SkinIdentifier identifier = skinDataOpt.get().getIdentifier();
                        playerStorage.setSkinIdOfPlayer(player.getUniqueId(), identifier);
                        skinsRestorer.getSkinApplier(Player.class).applySkin(player);
                    } else {
                        database.clearCustomSkin(player.getUniqueId());
                        player.sendMessage(Component.text("Ваш сохранённый скин больше не доступен и был удалён.").color(NamedTextColor.YELLOW));
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Ошибка при применении сохранённого скина для " + player.getName() + ": " + e.getMessage());
            }
        });
    }
} 