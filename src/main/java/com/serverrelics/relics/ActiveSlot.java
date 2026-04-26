package com.serverrelics.relics;

import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Defines where a relic must be located to be considered "active"
 * and apply its effects to the holder.
 */
public enum ActiveSlot {
    /**
     * Relic is active anywhere in inventory (including armor slots)
     */
    ANY,

    /**
     * Relic must be in the helmet slot
     */
    HEAD,

    /**
     * Relic must be in the chestplate slot
     */
    CHEST,

    /**
     * Relic must be in the leggings slot
     */
    LEGS,

    /**
     * Relic must be in the boots slot
     */
    FEET,

    /**
     * Relic must be in the main hand
     */
    MAINHAND,

    /**
     * Relic must be in the off hand
     */
    OFFHAND,

    /**
     * Relic must be in the hotbar (slots 0-8)
     */
    HOTBAR,

    /**
     * Relic must be in main inventory (not armor, not hotbar)
     */
    INVENTORY;

    /**
     * Check if the given item is in the correct slot for this ActiveSlot type
     *
     * @param player The player to check
     * @param item The item to find
     * @return true if the item is in a valid active slot
     */
    public boolean isInActiveSlot(Player player, ItemStack item) {
        if (item == null) return false;

        PlayerInventory inv = player.getInventory();

        switch (this) {
            case ANY:
                return inv.contains(item) ||
                       isSameItem(inv.getHelmet(), item) ||
                       isSameItem(inv.getChestplate(), item) ||
                       isSameItem(inv.getLeggings(), item) ||
                       isSameItem(inv.getBoots(), item) ||
                       isSameItem(inv.getItemInOffHand(), item);

            case HEAD:
                return isSameItem(inv.getHelmet(), item);

            case CHEST:
                return isSameItem(inv.getChestplate(), item);

            case LEGS:
                return isSameItem(inv.getLeggings(), item);

            case FEET:
                return isSameItem(inv.getBoots(), item);

            case MAINHAND:
                return isSameItem(inv.getItemInMainHand(), item);

            case OFFHAND:
                return isSameItem(inv.getItemInOffHand(), item);

            case HOTBAR:
                for (int i = 0; i <= 8; i++) {
                    if (isSameItem(inv.getItem(i), item)) {
                        return true;
                    }
                }
                return false;

            case INVENTORY:
                for (int i = 9; i <= 35; i++) {
                    if (isSameItem(inv.getItem(i), item)) {
                        return true;
                    }
                }
                return false;

            default:
                return false;
        }
    }

    /**
     * Get the equipment slot this corresponds to, if applicable
     */
    public EquipmentSlot toEquipmentSlot() {
        switch (this) {
            case HEAD: return EquipmentSlot.HEAD;
            case CHEST: return EquipmentSlot.CHEST;
            case LEGS: return EquipmentSlot.LEGS;
            case FEET: return EquipmentSlot.FEET;
            case MAINHAND: return EquipmentSlot.HAND;
            case OFFHAND: return EquipmentSlot.OFF_HAND;
            default: return null;
        }
    }

    /**
     * Check if this slot type is an armor slot
     */
    public boolean isArmorSlot() {
        return this == HEAD || this == CHEST || this == LEGS || this == FEET;
    }

    private boolean isSameItem(ItemStack a, ItemStack b) {
        if (a == null || b == null) return false;
        return a.isSimilar(b) || a == b;
    }

    /**
     * Parse from config string
     */
    public static ActiveSlot fromString(String str) {
        if (str == null) return ANY;
        try {
            return valueOf(str.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ANY;
        }
    }
}
