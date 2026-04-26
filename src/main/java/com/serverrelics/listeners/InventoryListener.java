package com.serverrelics.listeners;

import com.serverrelics.ServerRelics;
import com.serverrelics.relics.ActiveSlot;
import com.serverrelics.relics.Relic;
import com.serverrelics.relics.RelicRestrictions;
import com.serverrelics.util.TextUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Villager;

/**
 * Handles inventory-related restrictions for relics.
 * Blocks storage in containers, item frames, armor stands, etc.
 */
public class InventoryListener implements Listener {

    private final ServerRelics plugin;

    public InventoryListener(ServerRelics plugin) {
        this.plugin = plugin;
    }

    /**
     * Block clicking relics into restricted containers
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Check if player has bypass permission
        if (player.hasPermission("serverrelics.bypass.storage")) return;

        Inventory topInventory = event.getView().getTopInventory();
        InventoryType topType = topInventory.getType();

        // Quick exit if top inventory is player's own crafting/inventory
        if (topType == InventoryType.CRAFTING || topType == InventoryType.CREATIVE) {
            return;
        }

        ItemStack clickedItem = event.getCurrentItem();
        ItemStack cursorItem = event.getCursor();
        Inventory clickedInventory = event.getClickedInventory();
        InventoryAction action = event.getAction();

        plugin.debug("=== InventoryClick Event ===");
        plugin.debug("Top inv type: " + topType + ", Clicked inv: " +
            (clickedInventory != null ? clickedInventory.getType() : "null"));
        plugin.debug("Action: " + action + ", Shift: " + event.isShiftClick());
        plugin.debug("Clicked item relic: " + isRelic(clickedItem) + ", Cursor relic: " + isRelic(cursorItem));

        // Find any relic involved in this click
        ItemStack relicItem = null;
        Relic relic = null;

        if (isRelic(clickedItem)) {
            relicItem = clickedItem;
            relic = plugin.getRelicRegistry().getRelicFromItem(relicItem);
        }
        if (relic == null && isRelic(cursorItem)) {
            relicItem = cursorItem;
            relic = plugin.getRelicRegistry().getRelicFromItem(relicItem);
        }

        // Also check hotbar slot for number key swaps
        if (relic == null && event.getHotbarButton() >= 0) {
            ItemStack hotbarItem = player.getInventory().getItem(event.getHotbarButton());
            if (isRelic(hotbarItem)) {
                relicItem = hotbarItem;
                relic = plugin.getRelicRegistry().getRelicFromItem(relicItem);
            }
        }

        if (relic == null) {
            plugin.debug("No relic involved, allowing");
            return;
        }

        plugin.debug("Relic found: " + relic.getId());

        RelicRestrictions restrictions = relic.getRestrictions();

        // Check if this inventory type is blocked
        if (!restrictions.isInventoryTypeBlocked(topType)) {
            plugin.debug("Inventory type " + topType + " not blocked, allowing");
            handleSlotChange(event, player, relic);
            return;
        }

        plugin.debug("Inventory type " + topType + " IS BLOCKED");

        // Now check if the action would place the relic in the blocked container
        boolean shouldBlock = false;
        String reason = "";

        // CASE 1: Shift-click moves item to the other inventory
        if (event.isShiftClick() && clickedInventory != null) {
            // If clicking in player inventory, shift-click goes to top (blocked) inventory
            if (clickedInventory.getType() == InventoryType.PLAYER && isRelic(clickedItem)) {
                shouldBlock = true;
                reason = "shift-click from player inv to container";
            }
        }

        // CASE 2: Placing item from cursor into container
        if (!shouldBlock && isRelic(cursorItem) && clickedInventory != null) {
            // Check if clicked slot is in the top (blocked) inventory
            int rawSlot = event.getRawSlot();
            int topSize = topInventory.getSize();
            if (rawSlot < topSize) {
                shouldBlock = true;
                reason = "placing from cursor into container (raw slot " + rawSlot + " < " + topSize + ")";
            }
        }

        // CASE 3: Number key swap puts hotbar item into clicked slot
        if (!shouldBlock && (action == InventoryAction.HOTBAR_SWAP || action == InventoryAction.HOTBAR_MOVE_AND_READD)) {
            int hotbarSlot = event.getHotbarButton();
            if (hotbarSlot >= 0) {
                ItemStack hotbarItem = player.getInventory().getItem(hotbarSlot);
                if (isRelic(hotbarItem)) {
                    int rawSlot = event.getRawSlot();
                    int topSize = topInventory.getSize();
                    if (rawSlot < topSize) {
                        shouldBlock = true;
                        reason = "number key swap into container";
                    }
                }
            }
        }

        // CASE 4: Double-click to collect could pull from blocked inv (less common, but check)
        if (!shouldBlock && action == InventoryAction.COLLECT_TO_CURSOR && isRelic(cursorItem)) {
            // This is collecting matching items to cursor - generally fine
            // But we should prevent the relic from being split weirdly
        }

        if (shouldBlock) {
            plugin.debug("BLOCKING: " + reason);
            event.setCancelled(true);
            sendCannotStoreMessage(player, relic);
            return;
        }

        plugin.debug("No blocking condition matched, allowing");

        // Handle slot changes for active slot tracking
        handleSlotChange(event, player, relic);
    }

    /**
     * Block dragging relics into restricted containers
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (player.hasPermission("serverrelics.bypass.storage")) return;

        ItemStack draggedItem = event.getOldCursor();
        if (!isRelic(draggedItem)) return;

        Relic relic = plugin.getRelicRegistry().getRelicFromItem(draggedItem);
        if (relic == null) return;

        RelicRestrictions restrictions = relic.getRestrictions();
        Inventory topInventory = event.getView().getTopInventory();

        if (restrictions.isInventoryTypeBlocked(topInventory.getType())) {
            // Check if any of the dragged slots are in the blocked inventory
            int topSize = topInventory.getSize();
            for (int slot : event.getRawSlots()) {
                if (slot < topSize) {
                    event.setCancelled(true);
                    sendCannotStoreMessage(player, relic);
                    return;
                }
            }
        }
    }

    /**
     * Block putting relics in item frames
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemFrameInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof ItemFrame)) return;

        Player player = event.getPlayer();
        if (player.hasPermission("serverrelics.bypass.storage")) return;

        ItemStack item = player.getInventory().getItemInMainHand();
        if (event.getHand() == EquipmentSlot.OFF_HAND) {
            item = player.getInventory().getItemInOffHand();
        }

        if (!isRelic(item)) return;

        Relic relic = plugin.getRelicRegistry().getRelicFromItem(item);
        if (relic != null && relic.getRestrictions().isNoItemFrame()) {
            event.setCancelled(true);
            sendCannotStoreMessage(player, relic);
        }
    }

    /**
     * Block putting relics on armor stands
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onArmorStandInteract(PlayerInteractAtEntityEvent event) {
        if (!(event.getRightClicked() instanceof ArmorStand)) return;

        Player player = event.getPlayer();
        if (player.hasPermission("serverrelics.bypass.storage")) return;

        ItemStack item = player.getInventory().getItemInMainHand();
        if (event.getHand() == EquipmentSlot.OFF_HAND) {
            item = player.getInventory().getItemInOffHand();
        }

        if (!isRelic(item)) return;

        Relic relic = plugin.getRelicRegistry().getRelicFromItem(item);
        if (relic != null && relic.getRestrictions().isNoArmorStand()) {
            event.setCancelled(true);
            sendCannotStoreMessage(player, relic);
        }
    }

    /**
     * Block trading relics with villagers
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVillagerTrade(InventoryClickEvent event) {
        if (event.getInventory().getType() != InventoryType.MERCHANT) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (player.hasPermission("serverrelics.bypass.storage")) return;

        ItemStack item = event.getCurrentItem();
        ItemStack cursor = event.getCursor();

        ItemStack relicItem = isRelic(item) ? item : (isRelic(cursor) ? cursor : null);
        if (relicItem == null) return;

        Relic relic = plugin.getRelicRegistry().getRelicFromItem(relicItem);
        if (relic != null && relic.getRestrictions().isNoTrade()) {
            event.setCancelled(true);
            sendCannotStoreMessage(player, relic);
        }
    }

    /**
     * Block hoppers from picking up relics from the ground
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHopperPickup(InventoryPickupItemEvent event) {
        ItemStack item = event.getItem().getItemStack();
        if (!isRelic(item)) return;

        Relic relic = plugin.getRelicRegistry().getRelicFromItem(item);
        if (relic == null) return;

        // Block if hopper storage is restricted
        if (relic.getRestrictions().isNoHopper()) {
            event.setCancelled(true);
            plugin.debug("Blocked hopper from picking up " + relic.getId());
        }
    }

    /**
     * Block hoppers/droppers from moving relics between inventories
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        ItemStack item = event.getItem();
        if (!isRelic(item)) return;

        Relic relic = plugin.getRelicRegistry().getRelicFromItem(item);
        if (relic == null) return;

        RelicRestrictions restrictions = relic.getRestrictions();
        InventoryType sourceType = event.getSource().getType();
        InventoryType destType = event.getDestination().getType();

        // Block if either source or destination is a restricted type
        // This prevents hopper chains from moving relics around
        if (restrictions.isNoHopper() &&
            (sourceType == InventoryType.HOPPER || destType == InventoryType.HOPPER)) {
            event.setCancelled(true);
            plugin.debug("Blocked hopper transfer of " + relic.getId());
            return;
        }

        if (restrictions.isNoDropper() &&
            (sourceType == InventoryType.DROPPER || destType == InventoryType.DROPPER)) {
            event.setCancelled(true);
            plugin.debug("Blocked dropper transfer of " + relic.getId());
            return;
        }

        // Also block if destination is any restricted container type
        if (restrictions.isInventoryTypeBlocked(destType)) {
            event.setCancelled(true);
            plugin.debug("Blocked transfer of " + relic.getId() + " to " + destType);
        }
    }

    /**
     * Handle armor equip/unequip for relics with specific active slots
     */
    private void handleSlotChange(InventoryClickEvent event, Player player, Relic relic) {
        ActiveSlot activeSlot = relic.getActiveSlot();
        if (activeSlot == ActiveSlot.ANY) return;

        // This is a simplified check - full implementation would need to track
        // the exact slot changes and apply/remove effects accordingly
        // For now, we schedule a check after the click
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            checkRelicActivation(player, relic);
        }, 1L);
    }

    /**
     * Check if a relic should be active and apply/remove effects
     */
    private void checkRelicActivation(Player player, Relic relic) {
        String relicId = relic.getId();

        // Find the relic in player's inventory
        ItemStack relicItem = findRelicInInventory(player, relic);
        if (relicItem == null) return;

        boolean shouldBeActive = relic.getActiveSlot().isInActiveSlot(player, relicItem);
        boolean isCurrentHolder = player.getUniqueId().equals(plugin.getRelicManager().getHolder(relicId));

        if (shouldBeActive && !isCurrentHolder) {
            // Relic just became active
            plugin.getRelicManager().setHolder(relicId, player.getUniqueId());
        } else if (!shouldBeActive && isCurrentHolder) {
            // Relic was moved out of active slot
            // For now, we keep them as holder but could remove effects
            // This depends on desired behavior
        }
    }

    /**
     * Find a specific relic in a player's inventory
     */
    private ItemStack findRelicInInventory(Player player, Relic relic) {
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
     * Check if an item is any relic
     */
    private boolean isRelic(ItemStack item) {
        return item != null && Relic.isAnyRelic(item);
    }

    /**
     * Send the "cannot store" message to a player
     */
    private void sendCannotStoreMessage(Player player, Relic relic) {
        String message = plugin.getConfigManager().getMessage("cannot-store")
            .replace("{relic}", TextUtil.stripColor(relic.getDisplayName()));
        player.sendMessage(TextUtil.colorize(message));
    }
}
