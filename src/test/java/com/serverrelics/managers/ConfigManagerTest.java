package com.serverrelics.managers;

import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import com.serverrelics.ServerRelics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ConfigManager configuration loading
 */
class ConfigManagerTest {

    private ServerMock server;
    private ServerRelics plugin;
    private ConfigManager config;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(ServerRelics.class);
        config = plugin.getConfigManager();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void load_returnsTrue() {
        assertTrue(config.load());
    }

    @Test
    void getDatabaseType_returnsConfiguredValue() {
        assertNotNull(config.getDatabaseType());
    }

    @Test
    void getMysqlHost_hasDefault() {
        assertNotNull(config.getMysqlHost());
    }

    @Test
    void getMysqlPort_hasDefault() {
        assertTrue(config.getMysqlPort() > 0);
    }

    @Test
    void getStatsSaveInterval_hasPositiveValue() {
        assertTrue(config.getStatsSaveInterval() > 0);
    }

    @Test
    void getLeaderboardSize_hasPositiveValue() {
        assertTrue(config.getLeaderboardSize() > 0);
    }

    @Test
    void getPrefix_isNotEmpty() {
        assertNotNull(config.getPrefix());
        assertFalse(config.getPrefix().isEmpty());
    }

    @Test
    void getMessage_returnsFormattedMessage() {
        String message = config.getMessage("no-permission");
        assertNotNull(message);
        assertTrue(message.contains(config.getPrefix()) || message.contains("permission"));
    }

    @Test
    void getRawMessage_doesNotIncludePrefix() {
        String raw = config.getRawMessage("no-permission");
        String withPrefix = config.getMessage("no-permission");

        // Raw message should be shorter (no prefix)
        assertTrue(raw.length() <= withPrefix.length());
    }

    @Test
    void getMessage_handlesMissingKey() {
        String message = config.getMessage("nonexistent.key.here");
        assertNotNull(message);
        assertTrue(message.contains("Missing message") || message.contains("nonexistent"));
    }

    @Test
    void getMarkerUpdateInterval_hasPositiveValue() {
        assertTrue(config.getMarkerUpdateInterval() > 0);
    }

    @Test
    void isMySql_matchesDatabaseType() {
        boolean isMySql = config.isMySql();
        assertEquals("mysql".equalsIgnoreCase(config.getDatabaseType()), isMySql);
    }
}
