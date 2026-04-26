package com.serverrelics.listeners;

import com.serverrelics.ServerRelics;
import com.serverrelics.relics.Relic;
import com.serverrelics.util.TextUtil;
import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Handles item-related events for relics.
 * - Prevents despawning
 * - Handles pickup events
 * - Handles drop events
 * - Makes items indestructible
 */
public class ItemListener implements Listener {

    private final ServerRelics plugin;

    public ItemListener(ServerRelics plugin) {
        this.plugin = plugin;
    }

    /**
     * When a relic item spawns (dropped), set unlimited lifetime
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        Item item = event.getEntity();
        ItemStack itemStack = item.getItemStack();

        if (!Relic.isAnyRelic(itemStack)) return;

        Relic relic = plugin.getRelicRegistry().getRelicFromItem(itemStack);
        if (relic == null) return;

        // Set unlimited lifetime
        if (relic.getRestrictions().isNeverDespawn()) {
            item.setUnlimitedLifetime(true);
            plugin.debug("Set unlimited lifetime for dropped " + relic.getId());
        }

        // Make indestructible
        if (relic.getRestrictions().isIndestructible()) {
            item.setInvulnerable(true);
        }
    }

    /**
     * Prevent relic items from despawning (backup check)
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemDespawn(ItemDespawnEvent event) {
        ItemStack itemStack = event.getEntity().getItemStack();

        if (!Relic.isAnyRelic(itemStack)) return;

        Relic relic = plugin.getRelicRegistry().getRelicFromItem(itemStack);
        if (relic != null && relic.getRestrictions().isNeverDespawn()) {
            event.setCancelled(true);
            // Re-set unlimited lifetime in case it was lost
            event.getEntity().setUnlimitedLifetime(true);
            plugin.debug("Prevented despawn of " + relic.getId());
        }
    }

    /**
     * Block relic pickup for creative mode players
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreativePickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        ItemStack itemStack = event.getItem().getItemStack();
        if (!Relic.isAnyRelic(itemStack)) return;

        // Block creative mode players
        if (player.getGameMode() == GameMode.CREATIVE) {
            event.setCancelled(true);
            player.sendMessage(TextUtil.colorize("&cYou cannot pick up relics in creative mode!"));
            return;
        }

        // Check if inventory has space (need at least one empty slot)
        if (player.getInventory().firstEmpty() == -1) {
            event.setCancelled(true);
            player.sendMessage(TextUtil.colorize("&cYour inventory is full! Make room to pick up the relic."));
        }
    }

    /**
     * Handle player picking up a relic
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerPickupItem(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        Item itemEntity = event.getItem();
        ItemStack itemStack = itemEntity.getItemStack();

        if (!Relic.isAnyRelic(itemStack)) return;

        Relic relic = plugin.getRelicRegistry().getRelicFromItem(itemStack);
        if (relic == null) return;

        String relicId = relic.getId();

        // Clear dropped location tracking
        plugin.getRelicManager().clearDroppedLocation(relicId);

        // Check if relic should be active immediately or requires specific slot
        boolean shouldActivate = relic.getActiveSlot().isInActiveSlot(player, itemStack);

        // For relics that activate in ANY slot, picking up counts
        if (relic.getActiveSlot() == com.serverrelics.relics.ActiveSlot.ANY) {
            shouldActivate = true;
        }

        if (shouldActivate) {
            // Set as holder (this applies effects and broadcasts)
            plugin.getRelicManager().setHolder(relicId, player.getUniqueId());
        } else {
            // Just track that they have it, but don't apply effects yet
            // The player needs to equip it to the correct slot
            // For now, we'll still set them as holder for tracking purposes
            plugin.getRelicManager().setHolder(relicId, player.getUniqueId());
        }

        // Update BlueMap
        if (relic.isBlueMapEnabled()) {
            plugin.getBlueMapHook().updateMarker(relic, player);
        }

        plugin.debug("Player " + player.getName() + " picked up " + relicId);
    }

    /**
     * Prevent non-players from picking up relics
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player) return; // Let players pick up

        ItemStack itemStack = event.getItem().getItemStack();
        if (Relic.isAnyRelic(itemStack)) {
            event.setCancelled(true);
        }
    }

    /**
     * Handle player dropping a relic
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        Item itemEntity = event.getItemDrop();
        ItemStack itemStack = itemEntity.getItemStack();

        plugin.debug("PlayerDropItemEvent fired for " + player.getName() + ", item: " + itemStack.getType());

        if (!Relic.isAnyRelic(itemStack)) {
            plugin.debug("Item is not a relic, skipping");
            return;
        }

        Relic relic = plugin.getRelicRegistry().getRelicFromItem(itemStack);
        if (relic == null) {
            plugin.debug("Could not get relic from item (relic is null)");
            return;
        }

        // Check if dropping is blocked
        if (relic.getRestrictions().isNoDrop()) {
            event.setCancelled(true);
            String message = plugin.getConfigManager().getMessage("cannot-drop")
                .replace("{relic}", TextUtil.stripColor(relic.getDisplayName()));
            player.sendMessage(TextUtil.colorize(message));
            return;
        }

        String relicId = relic.getId();

        // Clear holder (they no longer have it)
        plugin.getRelicManager().clearHolder(relicId);

        // Track dropped location
        plugin.getRelicManager().setDroppedLocation(relicId, itemEntity.getLocation());
        plugin.getRelicManager().setDroppedItemEntity(relicId, itemEntity);

        // Set unlimited lifetime
        if (relic.getRestrictions().isNeverDespawn()) {
            itemEntity.setUnlimitedLifetime(true);
        }

        // Make indestructible
        if (relic.getRestrictions().isIndestructible()) {
            itemEntity.setInvulnerable(true);
        }

        // Update BlueMap marker
        if (relic.isBlueMapEnabled() && relic.isShowWhenDropped()) {
            plugin.getBlueMapHook().updateMarkerDropped(relic, itemEntity.getLocation());
        }

        plugin.debug("Player " + player.getName() + " dropped " + relicId);
    }

    /**
     * Prevent relic items from being destroyed by damage (fire, lava, explosions, etc.)
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemDamage(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Item item)) return;

        ItemStack itemStack = item.getItemStack();
        if (!Relic.isAnyRelic(itemStack)) return;

        Relic relic = plugin.getRelicRegistry().getRelicFromItem(itemStack);
        if (relic != null && relic.getRestrictions().isIndestructible()) {
            event.setCancelled(true);
        }
    }
}
