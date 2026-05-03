package com.serverrelics.relics;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.inventory.InventoryType;

import java.util.EnumSet;
import java.util.Set;

/**
 * Holds all restriction settings for a relic.
 * Loaded from config and used by listeners to enforce rules.
 */
public class RelicRestrictions {

    // Storage restrictions
    private boolean noChestStorage;
    private boolean noEnderChest;
    private boolean noShulkerBox;
    private boolean noItemFrame;
    private boolean noArmorStand;
    private boolean noBarrel;
    private boolean noHopper;
    private boolean noDropper;
    private boolean noDispenser;

    // Drop/transfer restrictions
    private boolean noDrop;
    private boolean noThrow;
    private boolean noTrade;

    // Combat restrictions
    private boolean forcePvp;

    // Death behavior
    private boolean bypassGraves;
    private boolean alwaysDropOnDeath;
    private boolean dropOnLogout;

    // Item behavior
    private boolean neverDespawn;
    private boolean indestructible;

    // Offline expiration
    private boolean offlineExpirationEnabled;
    private int offlineExpirationDays;
    private int offlineDropRadius;

    // Pickup cooldown for previous holder (seconds)
    private int previousHolderPickupCooldown;

    // Cached set of blocked inventory types
    private Set<InventoryType> blockedInventoryTypes;

    /**
     * Load restrictions from a config section
     */
    public static RelicRestrictions fromConfig(ConfigurationSection section) {
        RelicRestrictions r = new RelicRestrictions();

        if (section == null) {
            // Return defaults (all false)
            return r;
        }

        // Storage
        r.noChestStorage = section.getBoolean("no-chest-storage", false);
        r.noEnderChest = section.getBoolean("no-ender-chest", false);
        r.noShulkerBox = section.getBoolean("no-shulker-box", false);
        r.noItemFrame = section.getBoolean("no-item-frame", false);
        r.noArmorStand = section.getBoolean("no-armor-stand", false);
        r.noBarrel = section.getBoolean("no-barrel", false);
        r.noHopper = section.getBoolean("no-hopper", false);
        r.noDropper = section.getBoolean("no-dropper", false);
        r.noDispenser = section.getBoolean("no-dispenser", false);

        // Drop/transfer
        r.noDrop = section.getBoolean("no-drop", false);
        r.noThrow = section.getBoolean("no-throw", false);
        r.noTrade = section.getBoolean("no-trade", false);

        // Combat
        r.forcePvp = section.getBoolean("force-pvp", false);

        // Death
        r.bypassGraves = section.getBoolean("bypass-graves", false);
        r.alwaysDropOnDeath = section.getBoolean("always-drop-on-death", false);
        r.dropOnLogout = section.getBoolean("drop-on-logout", false);

        // Item
        r.neverDespawn = section.getBoolean("never-despawn", false);
        r.indestructible = section.getBoolean("indestructible", false);

        // Offline expiration
        ConfigurationSection offlineSection = section.getConfigurationSection("offline-expiration");
        if (offlineSection != null) {
            r.offlineExpirationEnabled = offlineSection.getBoolean("enabled", false);
            r.offlineExpirationDays = offlineSection.getInt("days", 10);
            r.offlineDropRadius = offlineSection.getInt("drop-radius", 50);
        }

        // Previous holder pickup cooldown (in seconds, default 0 = no cooldown)
        r.previousHolderPickupCooldown = section.getInt("previous-holder-pickup-cooldown", 0);

        // Build blocked inventory types set
        r.buildBlockedInventoryTypes();

        return r;
    }

    /**
     * Create default restrictions (nothing blocked)
     */
    public static RelicRestrictions defaults() {
        RelicRestrictions r = new RelicRestrictions();
        r.buildBlockedInventoryTypes();
        return r;
    }

    private void buildBlockedInventoryTypes() {
        blockedInventoryTypes = EnumSet.noneOf(InventoryType.class);

        if (noChestStorage) {
            blockedInventoryTypes.add(InventoryType.CHEST);
        }
        if (noEnderChest) {
            blockedInventoryTypes.add(InventoryType.ENDER_CHEST);
        }
        if (noShulkerBox) {
            blockedInventoryTypes.add(InventoryType.SHULKER_BOX);
        }
        if (noBarrel) {
            blockedInventoryTypes.add(InventoryType.BARREL);
        }
        if (noHopper) {
            blockedInventoryTypes.add(InventoryType.HOPPER);
        }
        if (noDropper) {
            blockedInventoryTypes.add(InventoryType.DROPPER);
        }
        if (noDispenser) {
            blockedInventoryTypes.add(InventoryType.DISPENSER);
        }
    }

    /**
     * Check if a specific inventory type is blocked for this relic
     */
    public boolean isInventoryTypeBlocked(InventoryType type) {
        return blockedInventoryTypes.contains(type);
    }

    /**
     * Check if this relic blocks any storage at all
     */
    public boolean hasAnyStorageRestrictions() {
        return noChestStorage || noEnderChest || noShulkerBox || noItemFrame ||
               noArmorStand || noBarrel || noHopper || noDropper || noDispenser;
    }

    // Getters

    public boolean isNoChestStorage() {
        return noChestStorage;
    }

    public boolean isNoEnderChest() {
        return noEnderChest;
    }

    public boolean isNoShulkerBox() {
        return noShulkerBox;
    }

    public boolean isNoItemFrame() {
        return noItemFrame;
    }

    public boolean isNoArmorStand() {
        return noArmorStand;
    }

    public boolean isNoBarrel() {
        return noBarrel;
    }

    public boolean isNoHopper() {
        return noHopper;
    }

    public boolean isNoDropper() {
        return noDropper;
    }

    public boolean isNoDispenser() {
        return noDispenser;
    }

    public boolean isNoDrop() {
        return noDrop;
    }

    public boolean isNoThrow() {
        return noThrow;
    }

    public boolean isNoTrade() {
        return noTrade;
    }

    public boolean isForcePvp() {
        return forcePvp;
    }

    public boolean isBypassGraves() {
        return bypassGraves;
    }

    public boolean isAlwaysDropOnDeath() {
        return alwaysDropOnDeath;
    }

    public boolean isDropOnLogout() {
        return dropOnLogout;
    }

    public boolean isNeverDespawn() {
        return neverDespawn;
    }

    public boolean isIndestructible() {
        return indestructible;
    }

    public Set<InventoryType> getBlockedInventoryTypes() {
        return blockedInventoryTypes;
    }

    public boolean isOfflineExpirationEnabled() {
        return offlineExpirationEnabled;
    }

    public int getOfflineExpirationDays() {
        return offlineExpirationDays;
    }

    public int getOfflineDropRadius() {
        return offlineDropRadius;
    }

    public int getPreviousHolderPickupCooldown() {
        return previousHolderPickupCooldown;
    }
}
