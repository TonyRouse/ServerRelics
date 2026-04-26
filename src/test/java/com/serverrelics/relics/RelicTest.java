package com.serverrelics.relics;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Relic item identification - no full plugin needed
 */
class RelicTest {

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
    void isAnyRelic_returnsFalseForNull() {
        assertFalse(Relic.isAnyRelic(null));
    }

    @Test
    void isAnyRelic_returnsFalseForNormalItem() {
        ItemStack diamond = new ItemStack(Material.DIAMOND);
        assertFalse(Relic.isAnyRelic(diamond));
    }

    @Test
    void isAnyRelic_returnsFalseForItemWithoutMeta() {
        ItemStack air = new ItemStack(Material.AIR);
        assertFalse(Relic.isAnyRelic(air));
    }

    @Test
    void getRelicId_returnsNullForNormalItem() {
        ItemStack diamond = new ItemStack(Material.DIAMOND);
        assertNull(Relic.getRelicId(diamond));
    }

    @Test
    void getRelicId_returnsNullForNull() {
        assertNull(Relic.getRelicId(null));
    }

    @Test
    void relicKey_isConsistent() {
        assertEquals("serverrelics", Relic.RELIC_TYPE_KEY.getNamespace());
        assertEquals("relic_type", Relic.RELIC_TYPE_KEY.getKey());
        assertEquals("serverrelics", Relic.RELIC_UUID_KEY.getNamespace());
        assertEquals("relic_uuid", Relic.RELIC_UUID_KEY.getKey());
    }

    @Test
    void taggedItem_isRecognizedAsRelic() {
        ItemStack item = new ItemStack(Material.DIAMOND);
        var meta = item.getItemMeta();
        assertNotNull(meta);

        meta.getPersistentDataContainer().set(
            Relic.RELIC_TYPE_KEY,
            PersistentDataType.STRING,
            "test_relic"
        );
        item.setItemMeta(meta);

        assertTrue(Relic.isAnyRelic(item));
        assertEquals("test_relic", Relic.getRelicId(item));
    }

    @Test
    void taggedItem_withDifferentTypes_areDistinguished() {
        ItemStack item1 = new ItemStack(Material.DIAMOND);
        var meta1 = item1.getItemMeta();
        meta1.getPersistentDataContainer().set(Relic.RELIC_TYPE_KEY, PersistentDataType.STRING, "crown");
        item1.setItemMeta(meta1);

        ItemStack item2 = new ItemStack(Material.EMERALD);
        var meta2 = item2.getItemMeta();
        meta2.getPersistentDataContainer().set(Relic.RELIC_TYPE_KEY, PersistentDataType.STRING, "orb");
        item2.setItemMeta(meta2);

        assertEquals("crown", Relic.getRelicId(item1));
        assertEquals("orb", Relic.getRelicId(item2));
    }

    @Test
    void itemWithUuidKeyOnly_isNotRelic() {
        // Having only UUID key shouldn't count as a relic
        ItemStack item = new ItemStack(Material.DIAMOND);
        var meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(
            Relic.RELIC_UUID_KEY,
            PersistentDataType.STRING,
            "some-uuid"
        );
        item.setItemMeta(meta);

        // isAnyRelic checks for RELIC_TYPE_KEY specifically
        assertFalse(Relic.isAnyRelic(item));
    }
}
