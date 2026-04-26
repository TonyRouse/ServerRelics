package com.serverrelics.listeners;

import com.serverrelics.ServerRelics;
import com.serverrelics.relics.Relic;
import org.bukkit.Location;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Handles player join/quit events for relic holders.
 * - Drops relics when holders log out (if configured)
 * - Re-applies effects when holders log in
 * - Updates player name cache
 */
public class JoinQuitListener implements Listener {

    private final ServerRelics plugin;

    public JoinQuitListener(ServerRelics plugin) {
        this.plugin = plugin;
    }

    /**
     * Handle player joining - re-apply effects if they hold a relic
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Update player name in database
        try {
            plugin.getStatsManager().updatePlayerName(player.getUniqueId(), player.getName());
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to update player name: " + e.getMessage());
        }

        // Check if this player is a relic holder
        List<String> heldRelics = plugin.getRelicManager().getRelicsHeldBy(player);

        for (String relicId : heldRelics) {
            Relic relic = plugin.getRelicRegistry().getRelic(relicId);
            if (relic == null) continue;

            // Clear offline status - they're back!
            plugin.getRelicManager().clearHolderOffline(relicId);

            // Verify they still have the relic in inventory
            ItemStack relicItem = findRelicInInventory(player, relic);

            if (relicItem != null) {
                // Re-apply effects
                relic.applyEffects(player);

                // Update BlueMap
                if (relic.isBlueMapEnabled()) {
                    plugin.getBlueMapHook().updateMarker(relic, player);
                }

                plugin.debug("Re-applied effects to " + player.getName() + " for " + relicId);
            } else {
                // They don't have the relic anymore (maybe inventory was modified offline?)
                plugin.getRelicManager().clearHolder(relicId);
                plugin.getLogger().warning("Player " + player.getName() + " was holder of " +
                    relicId + " but no longer has it in inventory.");
            }
        }
    }

    /**
     * Handle player quitting - drop relics if configured
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Check if this player holds any relics
        List<String> heldRelics = plugin.getRelicManager().getRelicsHeldBy(player);

        for (String relicId : heldRelics) {
            Relic relic = plugin.getRelicRegistry().getRelic(relicId);
            if (relic == null) continue;

            // Check if relic should drop on logout
            if (relic.getRestrictions().isDropOnLogout()) {
                // Find and remove the relic from inventory
                ItemStack relicItem = findAndRemoveRelicFromInventory(player, relic);

                if (relicItem != null) {
                    // Drop the relic
                    Location dropLoc = player.getLocation();
                    Item droppedItem = dropLoc.getWorld().dropItem(dropLoc, relicItem);

                    // Configure the dropped item
                    if (relic.getRestrictions().isNeverDespawn()) {
                        droppedItem.setUnlimitedLifetime(true);
                    }
                    if (relic.getRestrictions().isIndestructible()) {
                        droppedItem.setInvulnerable(true);
                    }

                    // Clear holder
                    plugin.getRelicManager().clearHolder(relicId);

                    // Track dropped location
                    plugin.getRelicManager().setDroppedLocation(relicId, dropLoc);
                    plugin.getRelicManager().setDroppedItemEntity(relicId, droppedItem);

                    // Update BlueMap
                    if (relic.isBlueMapEnabled() && relic.isShowWhenDropped()) {
                        plugin.getBlueMapHook().updateMarkerDropped(relic, dropLoc);
                    }

                    // Broadcast
                    relic.onLogoutDrop(player, dropLoc);

                    plugin.debug("Player " + player.getName() + " logged out, dropped " + relicId);
                }
            } else {
                // Player keeps the relic but goes offline
                // Just remove effects (they'll be re-applied on join)
                relic.removeEffects(player);

                // Track offline status for expiration
                plugin.getRelicManager().setHolderOffline(relicId, player.getLocation());

                // Update BlueMap to show "offline" or last known location
                if (relic.isBlueMapEnabled()) {
                    plugin.getBlueMapHook().updateMarkerOffline(relic, player.getLocation());
                }

                plugin.debug("Player " + player.getName() + " went offline with " + relicId);
            }
        }
    }

    /**
     * Find a relic in player's inventory
     */
    private ItemStack findRelicInInventory(Player player, Relic relic) {
        // Check main inventory
        for (ItemStack item : player.getInventory().getContents()) {
            if (relic.isThisRelic(item)) {
                return item;
            }
        }
        // Check armor slots
        for (ItemStack item : player.getInventory().getArmorContents()) {
            if (relic.isThisRelic(item)) {
                return item;
            }
        }
        // Check off-hand
        if (relic.isThisRelic(player.getInventory().getItemInOffHand())) {
            return player.getInventory().getItemInOffHand();
        }
        return null;
    }

    /**
     * Find and remove a relic from player's inventory
     */
    private ItemStack findAndRemoveRelicFromInventory(Player player, Relic relic) {
        // Check main inventory
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (relic.isThisRelic(contents[i])) {
                ItemStack item = contents[i].clone();
                player.getInventory().setItem(i, null);
                return item;
            }
        }

        // Check armor slots
        ItemStack helmet = player.getInventory().getHelmet();
        if (relic.isThisRelic(helmet)) {
            ItemStack item = helmet.clone();
            player.getInventory().setHelmet(null);
            return item;
        }

        ItemStack chest = player.getInventory().getChestplate();
        if (relic.isThisRelic(chest)) {
            ItemStack item = chest.clone();
            player.getInventory().setChestplate(null);
            return item;
        }

        ItemStack legs = player.getInventory().getLeggings();
        if (relic.isThisRelic(legs)) {
            ItemStack item = legs.clone();
            player.getInventory().setLeggings(null);
            return item;
        }

        ItemStack boots = player.getInventory().getBoots();
        if (relic.isThisRelic(boots)) {
            ItemStack item = boots.clone();
            player.getInventory().setBoots(null);
            return item;
        }

        // Check off-hand
        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (relic.isThisRelic(offHand)) {
            ItemStack item = offHand.clone();
            player.getInventory().setItemInOffHand(null);
            return item;
        }

        return null;
    }
}
