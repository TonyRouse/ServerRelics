package com.serverrelics.listeners;

import com.serverrelics.relics.Relic;
import com.serverrelics.relics.RelicRestrictions;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for inventory blocking logic
 */
class InventoryBlockingTest {

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void restrictionsFromConfig_blocksChest() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("no-chest-storage", true);
        config.set("no-shulker-box", true);
        config.set("no-ender-chest", true);

        RelicRestrictions restrictions = RelicRestrictions.fromConfig(config);

        assertTrue(restrictions.isNoChestStorage());
        assertTrue(restrictions.isNoShulkerBox());
        assertTrue(restrictions.isNoEnderChest());

        // Check inventory type blocking
        assertTrue(restrictions.isInventoryTypeBlocked(InventoryType.CHEST));
        assertTrue(restrictions.isInventoryTypeBlocked(InventoryType.SHULKER_BOX));
        assertTrue(restrictions.isInventoryTypeBlocked(InventoryType.ENDER_CHEST));

        // Player inventory should NOT be blocked
        assertFalse(restrictions.isInventoryTypeBlocked(InventoryType.PLAYER));
        assertFalse(restrictions.isInventoryTypeBlocked(InventoryType.CRAFTING));
    }

    @Test
    void restrictionsFromConfig_allStorageBlocked() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("no-chest-storage", true);
        config.set("no-ender-chest", true);
        config.set("no-shulker-box", true);
        config.set("no-barrel", true);
        config.set("no-hopper", true);
        config.set("no-dropper", true);
        config.set("no-dispenser", true);

        RelicRestrictions restrictions = RelicRestrictions.fromConfig(config);

        assertTrue(restrictions.isInventoryTypeBlocked(InventoryType.CHEST));
        assertTrue(restrictions.isInventoryTypeBlocked(InventoryType.ENDER_CHEST));
        assertTrue(restrictions.isInventoryTypeBlocked(InventoryType.SHULKER_BOX));
        assertTrue(restrictions.isInventoryTypeBlocked(InventoryType.BARREL));
        assertTrue(restrictions.isInventoryTypeBlocked(InventoryType.HOPPER));
        assertTrue(restrictions.isInventoryTypeBlocked(InventoryType.DROPPER));
        assertTrue(restrictions.isInventoryTypeBlocked(InventoryType.DISPENSER));
    }

    @Test
    void defaultRestrictions_blocksNothing() {
        RelicRestrictions restrictions = RelicRestrictions.defaults();

        assertFalse(restrictions.isInventoryTypeBlocked(InventoryType.CHEST));
        assertFalse(restrictions.isInventoryTypeBlocked(InventoryType.SHULKER_BOX));
        assertFalse(restrictions.isInventoryTypeBlocked(InventoryType.ENDER_CHEST));
    }

    @Test
    void relicIdExtraction_worksCorrectly() {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        var meta = item.getItemMeta();
        assertNotNull(meta);

        meta.getPersistentDataContainer().set(
            Relic.RELIC_TYPE_KEY,
            PersistentDataType.STRING,
            "crown"
        );
        item.setItemMeta(meta);

        // Verify relic detection
        assertTrue(Relic.isAnyRelic(item));
        assertEquals("crown", Relic.getRelicId(item));
    }

    @Test
    void blockedTypesSet_containsCorrectTypes() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("no-chest-storage", true);
        config.set("no-shulker-box", true);

        RelicRestrictions restrictions = RelicRestrictions.fromConfig(config);

        var blockedTypes = restrictions.getBlockedInventoryTypes();
        assertEquals(2, blockedTypes.size());
        assertTrue(blockedTypes.contains(InventoryType.CHEST));
        assertTrue(blockedTypes.contains(InventoryType.SHULKER_BOX));
    }

    @Test
    void hasAnyStorageRestrictions_returnsTrueWhenSet() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("no-chest-storage", true);

        RelicRestrictions restrictions = RelicRestrictions.fromConfig(config);
        assertTrue(restrictions.hasAnyStorageRestrictions());
    }

    @Test
    void hasAnyStorageRestrictions_returnsFalseWhenNoneSet() {
        RelicRestrictions restrictions = RelicRestrictions.defaults();
        assertFalse(restrictions.hasAnyStorageRestrictions());
    }
}
