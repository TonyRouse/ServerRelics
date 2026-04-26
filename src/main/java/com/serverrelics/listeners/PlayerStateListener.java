package com.serverrelics.listeners;

import com.serverrelics.ServerRelics;
import com.serverrelics.relics.Relic;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Handles player state changes that affect relic holding.
 * - Drops relic when player is banned
 * - Drops relic when player enters creative mode
 */
public class PlayerStateListener implements Listener {

    private final ServerRelics plugin;

    public PlayerStateListener(ServerRelics plugin) {
        this.plugin = plugin;
    }

    /**
     * Handle player being kicked/banned - drop relics
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerKick(PlayerKickEvent event) {
        Player player = event.getPlayer();

        // Check if this is a ban (kick reasons often contain "ban" for bans)
        // Also check if player is actually banned
        String reason = event.reason().toString().toLowerCase();
        boolean isBan = reason.contains("ban") ||
                        player.isBanned() ||
                        event.getCause() == PlayerKickEvent.Cause.BANNED;

        if (!isBan) return;

        List<String> heldRelics = plugin.getRelicManager().getRelicsHeldBy(player);
        if (heldRelics.isEmpty()) return;

        for (String relicId : heldRelics) {
            dropRelicFromPlayer(player, relicId, "banned");
        }
    }

    /**
     * Handle player switching to creative mode - drop relics
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        if (event.getNewGameMode() != GameMode.CREATIVE) return;

        Player player = event.getPlayer();
        List<String> heldRelics = plugin.getRelicManager().getRelicsHeldBy(player);
        if (heldRelics.isEmpty()) return;

        for (String relicId : heldRelics) {
            dropRelicFromPlayer(player, relicId, "creative mode");
        }
    }

    /**
     * Periodic check for players in creative mode holding relics
     * (in case they had it before this listener was added, or got creative via other means)
     */
    public void checkCreativeHolders() {
        for (String relicId : plugin.getRelicRegistry().getRelicIds()) {
            Player holder = plugin.getRelicManager().getHolderPlayer(relicId);
            if (holder != null && holder.getGameMode() == GameMode.CREATIVE) {
                dropRelicFromPlayer(holder, relicId, "creative mode");
            }
        }
    }

    /**
     * Drop a relic from a player
     */
    private void dropRelicFromPlayer(Player player, String relicId, String reason) {
        Relic relic = plugin.getRelicRegistry().getRelic(relicId);
        if (relic == null) return;

        // Find and remove relic from inventory
        ItemStack relicItem = findAndRemoveRelicFromInventory(player, relic);
        if (relicItem == null) {
            // Player doesn't actually have it in inventory, just clear tracking
            plugin.getRelicManager().clearHolder(relicId);
            return;
        }

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

        plugin.getLogger().info("Dropped " + relicId + " from " + player.getName() +
            " due to " + reason + " at " + dropLoc);
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
