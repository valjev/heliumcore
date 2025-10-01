package ru.helium.core.listeners;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.persistence.PersistentDataType;

public class MjolnirListener implements Listener {

    private final NamespacedKey key;

    public MjolnirListener(NamespacedKey key) {
        this.key = key;
    }

    @EventHandler
    public void onTridentHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Trident trident)) return;
        if (!(trident.getShooter() instanceof Player player)) return;

        if (!trident.getItemStack().hasItemMeta()) return;
        if (!trident.getItemStack().getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE)) return;

        Location loc = trident.getLocation();

        trident.getWorld().strikeLightning(loc);

        for (Entity entity : trident.getWorld().getNearbyEntities(loc, 2, 2, 2)) {
            if (entity instanceof LivingEntity living && !entity.equals(player)) {
                living.damage(9999999.0, player);
                break;
            }
        }
    }
}