package com.serverrelics.tasks;

import com.serverrelics.ServerRelics;
import com.serverrelics.relics.Relic;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;

/**
 * Task that runs every second to:
 * - Increment time held for all online relic holders
 * - Update relic lore with current reign time
 */
public class TimeTrackingTask extends BukkitRunnable {

    private final ServerRelics plugin;

    public TimeTrackingTask(ServerRelics plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        // Increment hold times in stats
        plugin.getStatsManager().incrementHoldTimes();

        // Update relic lore for each holder
        for (Relic relic : plugin.getRelicRegistry().getEnabledRelics()) {
            String relicId = relic.getId();
            UUID holderUuid = plugin.getRelicManager().getHolder(relicId);

            if (holderUuid == null) continue;

            Player holder = Bukkit.getPlayer(holderUuid);
            if (holder == null || !holder.isOnline()) continue;

            // Update the lore on the actual item to show current reign time
            long reignTime = plugin.getRelicManager().getCurrentReignTime(relicId);

            // Find and update the relic item in their inventory
            updateRelicLore(holder, relic, holderUuid, reignTime);
        }
    }

    /**
     * Update the lore on a relic item in a player's inventory
     */
    private void updateRelicLore(Player player, Relic relic, UUID holderUuid, long reignTime) {
        // Check all inventory slots
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            var item = player.getInventory().getItem(i);
            if (relic.isThisRelic(item)) {
                relic.updateItemLore(item, holderUuid, reignTime);
                return;
            }
        }

        // Check armor slots
        var helmet = player.getInventory().getHelmet();
        if (relic.isThisRelic(helmet)) {
            relic.updateItemLore(helmet, holderUuid, reignTime);
            return;
        }

        var chest = player.getInventory().getChestplate();
        if (relic.isThisRelic(chest)) {
            relic.updateItemLore(chest, holderUuid, reignTime);
            return;
        }

        var legs = player.getInventory().getLeggings();
        if (relic.isThisRelic(legs)) {
            relic.updateItemLore(legs, holderUuid, reignTime);
            return;
        }

        var boots = player.getInventory().getBoots();
        if (relic.isThisRelic(boots)) {
            relic.updateItemLore(boots, holderUuid, reignTime);
            return;
        }

        // Check off-hand
        var offHand = player.getInventory().getItemInOffHand();
        if (relic.isThisRelic(offHand)) {
            relic.updateItemLore(offHand, holderUuid, reignTime);
        }
    }
}
