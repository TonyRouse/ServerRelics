package com.serverrelics.relics;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.inventory.InventoryType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RelicRestrictions configuration loading and behavior
 */
class RelicRestrictionsTest {

    @Test
    void defaults_allRestrictionsDisabled() {
        RelicRestrictions r = RelicRestrictions.defaults();

        assertFalse(r.isNoChestStorage());
        assertFalse(r.isNoEnderChest());
        assertFalse(r.isNoShulkerBox());
        assertFalse(r.isNoItemFrame());
        assertFalse(r.isNoArmorStand());
        assertFalse(r.isNoDrop());
        assertFalse(r.isForcePvp());
        assertFalse(r.isBypassGraves());
        assertFalse(r.isAlwaysDropOnDeath());
        assertFalse(r.isNeverDespawn());
    }

    @Test
    void fromConfig_loadsAllSettings() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("no-chest-storage", true);
        config.set("no-ender-chest", true);
        config.set("no-shulker-box", true);
        config.set("no-item-frame", true);
        config.set("no-armor-stand", true);
        config.set("no-barrel", true);
        config.set("no-hopper", true);
        config.set("no-dropper", true);
        config.set("no-dispenser", true);
        config.set("no-drop", true);
        config.set("no-throw", true);
        config.set("no-trade", true);
        config.set("force-pvp", true);
        config.set("bypass-graves", true);
        config.set("always-drop-on-death", true);
        config.set("drop-on-logout", true);
        config.set("never-despawn", true);
        config.set("indestructible", true);

        RelicRestrictions r = RelicRestrictions.fromConfig(config);

        assertTrue(r.isNoChestStorage());
        assertTrue(r.isNoEnderChest());
        assertTrue(r.isNoShulkerBox());
        assertTrue(r.isNoItemFrame());
        assertTrue(r.isNoArmorStand());
        assertTrue(r.isNoBarrel());
        assertTrue(r.isNoHopper());
        assertTrue(r.isNoDropper());
        assertTrue(r.isNoDispenser());
        assertTrue(r.isNoDrop());
        assertTrue(r.isNoThrow());
        assertTrue(r.isNoTrade());
        assertTrue(r.isForcePvp());
        assertTrue(r.isBypassGraves());
        assertTrue(r.isAlwaysDropOnDeath());
        assertTrue(r.isDropOnLogout());
        assertTrue(r.isNeverDespawn());
        assertTrue(r.isIndestructible());
    }

    @Test
    void fromConfig_handlesNullSection() {
        RelicRestrictions r = RelicRestrictions.fromConfig(null);
        assertNotNull(r);
        assertFalse(r.isNoChestStorage());
    }

    @Test
    void fromConfig_defaultsToFalse() {
        YamlConfiguration config = new YamlConfiguration();
        // Empty config - nothing set

        RelicRestrictions r = RelicRestrictions.fromConfig(config);

        assertFalse(r.isNoChestStorage());
        assertFalse(r.isForcePvp());
    }

    @Test
    void isInventoryTypeBlocked_blocksConfiguredTypes() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("no-chest-storage", true);
        config.set("no-ender-chest", true);
        config.set("no-barrel", true);

        RelicRestrictions r = RelicRestrictions.fromConfig(config);

        assertTrue(r.isInventoryTypeBlocked(InventoryType.CHEST));
        assertTrue(r.isInventoryTypeBlocked(InventoryType.ENDER_CHEST));
        assertTrue(r.isInventoryTypeBlocked(InventoryType.BARREL));
        assertFalse(r.isInventoryTypeBlocked(InventoryType.HOPPER));
        assertFalse(r.isInventoryTypeBlocked(InventoryType.PLAYER));
    }

    @Test
    void hasAnyStorageRestrictions_detectsRestrictions() {
        RelicRestrictions none = RelicRestrictions.defaults();
        assertFalse(none.hasAnyStorageRestrictions());

        YamlConfiguration config = new YamlConfiguration();
        config.set("no-chest-storage", true);
        RelicRestrictions some = RelicRestrictions.fromConfig(config);
        assertTrue(some.hasAnyStorageRestrictions());
    }

    @Test
    void getBlockedInventoryTypes_returnsCorrectSet() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("no-chest-storage", true);
        config.set("no-hopper", true);

        RelicRestrictions r = RelicRestrictions.fromConfig(config);

        var blocked = r.getBlockedInventoryTypes();
        assertEquals(2, blocked.size());
        assertTrue(blocked.contains(InventoryType.CHEST));
        assertTrue(blocked.contains(InventoryType.HOPPER));
    }
}
