package com.serverrelics.listeners;

import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import com.serverrelics.relics.Relic;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for inventory-related relic detection.
 * These tests don't require full plugin initialization.
 */
class InventoryListenerTest {

    private ServerMock server;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        player = server.addPlayer();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void normalItem_isNotRecognizedAsRelic() {
        ItemStack diamond = new ItemStack(Material.DIAMOND);
        assertFalse(Relic.isAnyRelic(diamond));
    }

    @Test
    void taggedItem_isRecognizedAsRelic() {
        ItemStack item = createTaggedRelic("crown");
        assertTrue(Relic.isAnyRelic(item));
    }

    @Test
    void taggedItem_hasCorrectRelicId() {
        ItemStack item = createTaggedRelic("crown");
        assertEquals("crown", Relic.getRelicId(item));
    }

    @Test
    void multipleRelicTypes_areDistinguished() {
        ItemStack crown = createTaggedRelic("crown");
        ItemStack orb = createTaggedRelic("orb");

        assertEquals("crown", Relic.getRelicId(crown));
        assertEquals("orb", Relic.getRelicId(orb));
        assertNotEquals(Relic.getRelicId(crown), Relic.getRelicId(orb));
    }

    @Test
    void player_canHoldItems() {
        ItemStack item = new ItemStack(Material.DIAMOND);
        player.getInventory().addItem(item);
        assertTrue(player.getInventory().contains(Material.DIAMOND));
    }

    @Test
    void player_inventoryStartsEmpty() {
        assertTrue(player.getInventory().isEmpty());
    }

    @Test
    void player_canHoldRelicInInventory() {
        ItemStack relic = createTaggedRelic("crown");
        player.getInventory().addItem(relic);

        // Find the relic in inventory
        boolean foundRelic = false;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && Relic.isAnyRelic(item)) {
                foundRelic = true;
                break;
            }
        }
        assertTrue(foundRelic);
    }

    @Test
    void player_canEquipRelicAsHelmet() {
        ItemStack relic = createTaggedRelic("crown");
        player.getInventory().setHelmet(relic);

        ItemStack helmet = player.getInventory().getHelmet();
        assertNotNull(helmet);
        assertTrue(Relic.isAnyRelic(helmet));
        assertEquals("crown", Relic.getRelicId(helmet));
    }

    @Test
    void relicId_isCaseSensitive() {
        ItemStack crown1 = createTaggedRelic("CROWN");
        ItemStack crown2 = createTaggedRelic("crown");

        // IDs stored exactly as given
        assertEquals("CROWN", Relic.getRelicId(crown1));
        assertEquals("crown", Relic.getRelicId(crown2));
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
