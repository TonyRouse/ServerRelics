package com.serverrelics.relics;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RelicRegistry - tests that don't require full plugin initialization
 */
class RelicRegistryTest {

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void isRelic_detectsTaggedItem() {
        ItemStack item = createTaggedRelic("crown");
        // Static method check - doesn't need registry
        assertTrue(Relic.isAnyRelic(item));
    }

    @Test
    void isRelic_rejectsNormalItem() {
        ItemStack diamond = new ItemStack(Material.DIAMOND);
        assertFalse(Relic.isAnyRelic(diamond));
    }

    @Test
    void getRelicId_extractsCorrectId() {
        ItemStack item = createTaggedRelic("crown");
        assertEquals("crown", Relic.getRelicId(item));
    }

    @Test
    void getRelicId_returnsNullForNormalItem() {
        ItemStack diamond = new ItemStack(Material.DIAMOND);
        assertNull(Relic.getRelicId(diamond));
    }

    @Test
    void taggedItem_withDifferentIds_areDistinguished() {
        ItemStack crown = createTaggedRelic("crown");
        ItemStack orb = createTaggedRelic("orb");

        assertEquals("crown", Relic.getRelicId(crown));
        assertEquals("orb", Relic.getRelicId(orb));
        assertNotEquals(Relic.getRelicId(crown), Relic.getRelicId(orb));
    }

    @Test
    void pdcKeys_areStable() {
        // Verify namespace and key names are consistent
        assertEquals("serverrelics", Relic.RELIC_TYPE_KEY.getNamespace());
        assertEquals("relic_type", Relic.RELIC_TYPE_KEY.getKey());
        assertEquals("serverrelics", Relic.RELIC_UUID_KEY.getNamespace());
        assertEquals("relic_uuid", Relic.RELIC_UUID_KEY.getKey());
    }

    @Test
    void isAnyRelic_returnsFalseForNull() {
        assertFalse(Relic.isAnyRelic(null));
    }

    @Test
    void isAnyRelic_returnsFalseForAir() {
        ItemStack air = new ItemStack(Material.AIR);
        assertFalse(Relic.isAnyRelic(air));
    }

    @Test
    void activeSlot_HEAD_isArmorSlot() {
        assertTrue(ActiveSlot.HEAD.isArmorSlot());
    }

    @Test
    void activeSlot_MAINHAND_isNotArmorSlot() {
        assertFalse(ActiveSlot.MAINHAND.isArmorSlot());
    }

    private ItemStack createTaggedRelic(String relicId) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        var meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(
            Relic.RELIC_TYPE_KEY,
            PersistentDataType.STRING,
            relicId
        );
        item.setItemMeta(meta);
        return item;
    }
}
