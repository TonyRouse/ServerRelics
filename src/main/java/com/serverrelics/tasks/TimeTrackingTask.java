package com.serverrelics.tasks;

import com.serverrelics.ServerRelics;
import com.serverrelics.relics.Relic;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
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
     * Update the lore on a relic item in a player's inventory.
     * Only updates every 60 seconds to avoid visual "bouncing" from frequent meta updates.
     */
    private void updateRelicLore(Player player, Relic relic, UUID holderUuid, long reignTime) {
        // Only update lore every 60 seconds to reduce visual bouncing
        // The lore shows time in minutes/hours/days format, so per-second updates aren't needed
        if (reignTime % 60 != 0 && reignTime > 0) {
            return;
        }

        ItemStack item = findRelicItem(player, relic);
        if (item != null) {
            relic.updateItemLore(item, holderUuid, reignTime);
        }
    }

    /**
     * Find a relic item in a player's inventory, armor, offhand, or cursor.
     */
    private ItemStack findRelicItem(Player player, Relic relic) {
        // Check cursor first - item might be being moved between slots
        var cursor = player.getItemOnCursor();
        if (relic.isThisRelic(cursor)) {
            return cursor;
        }

        // Check all inventory slots
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            var item = player.getInventory().getItem(i);
            if (relic.isThisRelic(item)) {
                return item;
            }
        }

        // Check armor slots
        var helmet = player.getInventory().getHelmet();
        if (relic.isThisRelic(helmet)) {
            return helmet;
        }

        var chest = player.getInventory().getChestplate();
        if (relic.isThisRelic(chest)) {
            return chest;
        }

        var legs = player.getInventory().getLeggings();
        if (relic.isThisRelic(legs)) {
            return legs;
        }

        var boots = player.getInventory().getBoots();
        if (relic.isThisRelic(boots)) {
            return boots;
        }

        // Check off-hand
        var offHand = player.getInventory().getItemInOffHand();
        if (relic.isThisRelic(offHand)) {
            return offHand;
        }

        return null;
    }
}
