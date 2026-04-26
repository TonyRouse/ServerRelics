package com.serverrelics.tasks;

import com.serverrelics.ServerRelics;
import com.serverrelics.relics.Relic;
import com.serverrelics.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Periodically checks for offline holders who have exceeded the expiration time.
 * When a holder is offline too long, the relic is dropped at a random location
 * near their last known position and an announcement is broadcast.
 */
public class OfflineExpirationTask extends BukkitRunnable {

    private final ServerRelics plugin;

    public OfflineExpirationTask(ServerRelics plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        for (String relicId : plugin.getRelicRegistry().getRelicIds()) {
            checkExpiration(relicId);
        }
    }

    private void checkExpiration(String relicId) {
        Relic relic = plugin.getRelicRegistry().getRelic(relicId);
        if (relic == null) return;

        // Check if offline expiration is enabled for this relic
        if (!relic.getRestrictions().isOfflineExpirationEnabled()) return;

        // Check if holder is offline
        if (!plugin.getRelicManager().isHolderOffline(relicId)) return;

        // Check if expired
        int expirationDays = relic.getRestrictions().getOfflineExpirationDays();
        if (!plugin.getRelicManager().isHolderExpired(relicId, expirationDays)) return;

        // Get holder info before dropping
        String holderName = plugin.getRelicManager().getOfflineHolderName(relicId);
        if (holderName == null) holderName = "Unknown";

        // Drop the relic
        int radius = relic.getRestrictions().getOfflineDropRadius();
        Item droppedItem = plugin.getRelicManager().dropRelicFromOfflineHolder(relicId, radius);

        if (droppedItem != null) {
            Location dropLoc = droppedItem.getLocation();

            // Update BlueMap
            if (relic.isBlueMapEnabled() && relic.isShowWhenDropped()) {
                plugin.getBlueMapHook().updateMarkerDropped(relic, dropLoc);
            }

            // Broadcast
            String message = relic.getBroadcastMessage("on-offline-expired");
            if (message != null && !message.isEmpty()) {
                String locationStr = String.format("%s, %d, %d, %d",
                    dropLoc.getWorld().getName(),
                    dropLoc.getBlockX(), dropLoc.getBlockY(), dropLoc.getBlockZ());

                message = message
                    .replace("{player}", holderName)
                    .replace("{location}", locationStr);

                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.hasPermission("serverrelics.notify")) {
                        p.sendMessage(TextUtil.colorize(message));
                    }
                }
            }

            // Save state
            plugin.getRelicManager().saveState();

            plugin.getLogger().info("Relic " + relicId + " dropped from expired offline holder " +
                holderName + " at " + dropLoc);
        }
    }
}
