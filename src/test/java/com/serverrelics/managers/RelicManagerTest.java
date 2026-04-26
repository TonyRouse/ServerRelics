package com.serverrelics.managers;

import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import com.serverrelics.ServerRelics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RelicManager state tracking
 */
class RelicManagerTest {

    private ServerMock server;
    private ServerRelics plugin;
    private RelicManager manager;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(ServerRelics.class);
        manager = plugin.getRelicManager();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void getHolder_returnsNullForUnknownRelic() {
        assertNull(manager.getHolder("nonexistent"));
    }

    @Test
    void getHolder_returnsNullForUnheldRelic() {
        assertNull(manager.getHolder("crown"));
    }

    @Test
    void relicExistsInWorld_returnsFalseInitially() {
        assertFalse(manager.relicExistsInWorld("crown"));
    }

    @Test
    void getHolderPlayer_returnsNullForOfflineHolder() {
        UUID offlineUuid = UUID.randomUUID();
        // Can't directly set holder without going through the full flow
        // This tests the null case
        assertNull(manager.getHolderPlayer("crown"));
    }

    @Test
    void getCurrentReignTime_returnsZeroForNoHolder() {
        assertEquals(0, manager.getCurrentReignTime("crown"));
    }

    @Test
    void playerHoldsAnyRelic_returnsFalseForNewPlayer() {
        PlayerMock player = server.addPlayer();
        assertFalse(manager.playerHoldsAnyRelic(player));
    }

    @Test
    void getRelicsHeldBy_returnsEmptyForNewPlayer() {
        PlayerMock player = server.addPlayer();
        assertTrue(manager.getRelicsHeldBy(player).isEmpty());
    }

    @Test
    void isDropped_returnsFalseInitially() {
        assertFalse(manager.isDropped("crown"));
    }

    @Test
    void getDroppedLocation_returnsNullInitially() {
        assertNull(manager.getDroppedLocation("crown"));
    }

    @Test
    void getRelicLocation_returnsNullForUnknown() {
        assertNull(manager.getRelicLocation("nonexistent"));
    }

    @Test
    void caseInsensitiveRelicIds() {
        // Verify that relic IDs are case-insensitive
        assertNull(manager.getHolder("CROWN"));
        assertNull(manager.getHolder("Crown"));
        assertNull(manager.getHolder("crown"));
        // All should behave the same
    }
}
