package com.serverrelics.listeners;

import com.serverrelics.ServerRelics;
import com.serverrelics.relics.Relic;
import org.bukkit.Location;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Iterator;

/**
 * Handles death events for relic holders.
 * - Ensures relics always drop on death (bypasses keepInventory and graves)
 * - Tracks death stats
 * - Broadcasts death messages
 */
public class DeathListener implements Listener {

    private final ServerRelics plugin;

    public DeathListener(ServerRelics plugin) {
        this.plugin = plugin;
    }

    /**
     * Handle relic drops on player death.
     * Uses LOWEST priority to run BEFORE other plugins (like AxGraves).
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        // Find any relics in the death drops
        Iterator<ItemStack> dropIterator = event.getDrops().iterator();
        while (dropIterator.hasNext()) {
            ItemStack item = dropIterator.next();
            if (!Relic.isAnyRelic(item)) continue;

            Relic relic = plugin.getRelicRegistry().getRelicFromItem(item);
            if (relic == null) continue;

            // Check if this relic should always drop
            if (relic.getRestrictions().isAlwaysDropOnDeath()) {
                // Remove from normal drops (so graves don't get it)
                dropIterator.remove();

                // Also remove from player's inventory/armor to prevent duplication
                // by grave plugins that read directly from inventory
                removeRelicFromPlayer(player, item);

                // Spawn the item manually
                Location dropLoc = getDropLocation(player);
                spawnRelicDrop(relic, item, dropLoc, player, "death");
            }
        }

        // Also check armor slots and inventory for relics that might not be in drops
        // (e.g., if keepInventory is on)
        checkAndDropRelicsFromInventory(player, event);
    }

    /**
     * Remove a relic item from a player's inventory and armor slots.
     * This prevents grave plugins from duplicating the item.
     */
    private void removeRelicFromPlayer(Player player, ItemStack relicItem) {
        // Check main inventory
        player.getInventory().remove(relicItem);

        // Check armor slots - need to handle separately since remove() doesn't check armor
        ItemStack[] armor = player.getInventory().getArmorContents();
        boolean armorChanged = false;
        for (int i = 0; i < armor.length; i++) {
            if (relicItem.equals(armor[i])) {
                armor[i] = null;
                armorChanged = true;
            }
        }
        if (armorChanged) {
            player.getInventory().setArmorContents(armor);
        }

        // Check offhand
        if (relicItem.equals(player.getInventory().getItemInOffHand())) {
            player.getInventory().setItemInOffHand(null);
        }
    }

    /**
     * Check if the player had relics that weren't in the drop list
     * (happens with keepInventory gamerule)
     */
    private void checkAndDropRelicsFromInventory(Player player, PlayerDeathEvent event) {
        boolean keepInventory = event.getKeepInventory();
        if (!keepInventory) return;

        // Check all inventory slots including armor
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || !Relic.isAnyRelic(item)) continue;

            Relic relic = plugin.getRelicRegistry().getRelicFromItem(item);
            if (relic == null) continue;

            if (relic.getRestrictions().isAlwaysDropOnDeath()) {
                // Remove from inventory
                player.getInventory().remove(item);

                // Spawn drop
                Location dropLoc = getDropLocation(player);
                spawnRelicDrop(relic, item, dropLoc, player, "death");
            }
        }

        // Check armor slots
        checkArmorSlots(player);
    }

    /**
     * Check armor slots for relics
     */
    private void checkArmorSlots(Player player) {
        ItemStack[] armor = player.getInventory().getArmorContents();
        boolean changed = false;

        for (int i = 0; i < armor.length; i++) {
            ItemStack item = armor[i];
            if (item == null || !Relic.isAnyRelic(item)) continue;

            Relic relic = plugin.getRelicRegistry().getRelicFromItem(item);
            if (relic == null) continue;

            if (relic.getRestrictions().isAlwaysDropOnDeath()) {
                // Remove from armor
                armor[i] = null;
                changed = true;

                // Spawn drop
                Location dropLoc = getDropLocation(player);
                spawnRelicDrop(relic, item, dropLoc, player, "death");
            }
        }

        if (changed) {
            player.getInventory().setArmorContents(armor);
        }
    }

    /**
     * Spawn a relic item drop with proper settings
     */
    private void spawnRelicDrop(Relic relic, ItemStack item, Location location, Player player, String reason) {
        String relicId = relic.getId();

        // Clear the holder
        plugin.getRelicManager().clearHolder(relicId);

        // Track death stat
        if (relic.isTrackDeaths()) {
            plugin.getStatsManager().incrementDeaths(player.getUniqueId(), relicId);
        }

        // Spawn the item
        Item droppedItem = location.getWorld().dropItem(location, item);

        // Set unlimited lifetime if configured
        if (relic.getRestrictions().isNeverDespawn()) {
            droppedItem.setUnlimitedLifetime(true);
        }

        // Make indestructible if configured
        if (relic.getRestrictions().isIndestructible()) {
            droppedItem.setInvulnerable(true);
        }

        // Track the dropped location
        plugin.getRelicManager().setDroppedLocation(relicId, location);
        plugin.getRelicManager().setDroppedItemEntity(relicId, droppedItem);

        // Update BlueMap marker
        if (relic.isBlueMapEnabled() && relic.isShowWhenDropped()) {
            plugin.getBlueMapHook().updateMarkerDropped(relic, location);
        }

        // Broadcast death drop message
        relic.onDeathDrop(player, location);

        plugin.debug("Relic " + relicId + " dropped on death at " + location);
    }

    /**
     * Get a safe drop location for the relic
     */
    private Location getDropLocation(Player player) {
        Location loc = player.getLocation();

        // If in void, use last safe location or world spawn
        if (loc.getY() < player.getWorld().getMinHeight()) {
            Location bed = player.getBedSpawnLocation();
            if (bed != null) {
                return bed;
            }
            return player.getWorld().getSpawnLocation();
        }

        return loc;
    }

    /**
     * Track kills while holding a relic
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // Check if the damage will kill the entity
        if (!(event.getEntity() instanceof Player victim)) return;
        if (victim.getHealth() - event.getFinalDamage() > 0) return;

        // Check if killer is a player
        Player killer = null;
        if (event.getDamager() instanceof Player) {
            killer = (Player) event.getDamager();
        }
        // Could also check for projectiles from players, etc.

        if (killer == null) return;

        // Check if killer holds any relic
        for (String relicId : plugin.getRelicManager().getRelicsHeldBy(killer)) {
            Relic relic = plugin.getRelicRegistry().getRelic(relicId);
            if (relic != null && relic.isTrackKills()) {
                plugin.getStatsManager().incrementKills(killer.getUniqueId(), relicId);
            }
        }
    }
}
