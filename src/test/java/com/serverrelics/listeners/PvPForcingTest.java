package com.serverrelics.listeners;

import com.serverrelics.ServerRelics;
import com.serverrelics.hooks.PvPManagerHook;
import com.serverrelics.managers.RelicManager;
import com.serverrelics.relics.ActiveSlot;
import com.serverrelics.relics.Relic;
import com.serverrelics.relics.RelicRegistry;
import com.serverrelics.relics.RelicRestrictions;
import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Critical tests for PvP forcing functionality.
 *
 * CRITICAL BUG HISTORY:
 * - Bug: Crown with force-pvp=true was not actually forcing PvP on
 * - Bug: Players could use /pvp command to toggle PvP off while holding crown
 * - Root causes:
 *   1. Broken reflection code in PvPManagerHook (dead code, wrong method names)
 *   2. No command blocker for /pvp while holding force-pvp relic
 *   3. No continuous enforcement of PvP state
 *
 * These tests verify that PvP forcing works correctly.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PvPForcingTest {

    @Mock private ServerRelics plugin;
    @Mock private RelicRegistry relicRegistry;
    @Mock private RelicManager relicManager;
    @Mock private PvPManagerHook pvpManagerHook;
    @Mock private Player player;
    @Mock private Server server;
    @Mock private PlayerInventory playerInventory;
    @Mock private ItemStack crownItem;

    private PvPCommandListener pvpCommandListener;

    @BeforeEach
    void setUp() {
        when(plugin.getRelicRegistry()).thenReturn(relicRegistry);
        when(plugin.getRelicManager()).thenReturn(relicManager);
        when(plugin.getPvPManagerHook()).thenReturn(pvpManagerHook);

        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getServer()).thenReturn(server);
        when(server.getOnlinePlayers()).thenReturn(List.of());
        when(player.getInventory()).thenReturn(playerInventory);
        when(player.getItemOnCursor()).thenReturn(null);
        when(playerInventory.getContents()).thenReturn(new ItemStack[36]);

        pvpCommandListener = new PvPCommandListener(plugin);
    }

    @Nested
    @DisplayName("PvP Command Blocking Tests")
    class PvPCommandBlockingTests {

        @ParameterizedTest
        @ValueSource(strings = {"/pvp", "/pvptoggle", "/togglepvp", "/pvpmanager"})
        @DisplayName("CRITICAL: PvP toggle commands are blocked while holding crown")
        void pvpCommandsBlockedWithCrown(String command) {
            // Given: Player holds crown with force-pvp
            Relic crown = createCrownRelic();
            when(relicManager.getRelicsHeldBy(player)).thenReturn(List.of("crown"));
            when(relicRegistry.getRelic("crown")).thenReturn(crown);
            // Make activeSlot check return true (player is wearing it)
            when(crown.getActiveSlot().isInActiveSlot(eq(player), any())).thenReturn(true);

            PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(player, command);

            // When
            pvpCommandListener.onPlayerCommand(event);

            // Then
            assertTrue(event.isCancelled(),
                "Command '" + command + "' must be blocked while holding force-pvp relic");
        }

        @Test
        @DisplayName("PvP commands allowed when not holding any relic")
        void pvpCommandsAllowedWithoutRelic() {
            when(relicManager.getRelicsHeldBy(player)).thenReturn(List.of());

            PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(player, "/pvp");

            // When
            pvpCommandListener.onPlayerCommand(event);

            // Then
            assertFalse(event.isCancelled(), "Command should be allowed without relic");
        }

        @Test
        @DisplayName("PvP commands allowed with relic that doesn't force PvP")
        void pvpCommandsAllowedWithNonForcingRelic() {
            Relic scepter = createNonForcingRelic();
            when(relicManager.getRelicsHeldBy(player)).thenReturn(List.of("scepter"));
            when(relicRegistry.getRelic("scepter")).thenReturn(scepter);

            PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(player, "/pvp");

            // When
            pvpCommandListener.onPlayerCommand(event);

            // Then
            assertFalse(event.isCancelled(), "Command should be allowed with non-forcing relic");
        }

        @Test
        @DisplayName("Other commands are not affected")
        void otherCommandsNotAffected() {
            Relic crown = createCrownRelic();
            when(relicManager.getRelicsHeldBy(player)).thenReturn(List.of("crown"));
            when(relicRegistry.getRelic("crown")).thenReturn(crown);
            when(crown.getActiveSlot().isInActiveSlot(eq(player), any())).thenReturn(true);

            PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(player, "/help");

            // When
            pvpCommandListener.onPlayerCommand(event);

            // Then
            assertFalse(event.isCancelled(), "Other commands should not be affected");
        }

        @Test
        @DisplayName("PvP command blocked even with arguments")
        void pvpCommandBlockedWithArgs() {
            Relic crown = createCrownRelic();
            when(relicManager.getRelicsHeldBy(player)).thenReturn(List.of("crown"));
            when(relicRegistry.getRelic("crown")).thenReturn(crown);
            when(crown.getActiveSlot().isInActiveSlot(eq(player), any())).thenReturn(true);

            PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(player, "/pvp off");

            // When
            pvpCommandListener.onPlayerCommand(event);

            // Then
            assertTrue(event.isCancelled(), "PvP command with args must be blocked");
        }
    }

    @Nested
    @DisplayName("Relic Restrictions Configuration Tests")
    class RelicRestrictionsConfigTests {

        @Test
        @DisplayName("force-pvp config option loads correctly")
        void forcePvpLoadsCorrectly() {
            YamlConfiguration config = new YamlConfiguration();
            config.set("force-pvp", true);

            RelicRestrictions restrictions = RelicRestrictions.fromConfig(config);

            assertTrue(restrictions.isForcePvp(), "force-pvp must be loaded as true");
        }

        @Test
        @DisplayName("force-pvp defaults to false")
        void forcePvpDefaultsFalse() {
            RelicRestrictions restrictions = RelicRestrictions.defaults();

            assertFalse(restrictions.isForcePvp(), "force-pvp must default to false");
        }

        @Test
        @DisplayName("Crown config has all required restrictions")
        void crownHasAllRequiredRestrictions() {
            // Simulate actual crown config
            YamlConfiguration config = new YamlConfiguration();
            config.set("force-pvp", true);
            config.set("always-drop-on-death", true);
            config.set("bypass-graves", true);
            config.set("never-despawn", true);
            config.set("indestructible", true);

            RelicRestrictions restrictions = RelicRestrictions.fromConfig(config);

            assertTrue(restrictions.isForcePvp(), "Crown must have force-pvp enabled");
            assertTrue(restrictions.isAlwaysDropOnDeath(), "Crown must drop on death");
            assertTrue(restrictions.isBypassGraves(), "Crown must bypass graves");
            assertTrue(restrictions.isNeverDespawn(), "Crown must never despawn");
            assertTrue(restrictions.isIndestructible(), "Crown must be indestructible");
        }
    }

    @Nested
    @DisplayName("PvPManagerHook Tests")
    class PvPManagerHookTests {

        @Test
        @DisplayName("isPvPForced returns false for players without forced PvP")
        void isPvPForcedReturnsFalseWhenNotForced() {
            PvPManagerHook hook = new PvPManagerHook(plugin);

            assertFalse(hook.isPvPForced(player),
                "isPvPForced should return false before forcePvPOn is called");
        }

        @Test
        @DisplayName("isAvailable returns false when not initialized")
        void isAvailableReturnsFalseWhenNotInitialized() {
            PvPManagerHook hook = new PvPManagerHook(plugin);

            assertFalse(hook.isAvailable(),
                "isAvailable should return false before initialization");
        }
    }

    // Helper methods

    private Relic createCrownRelic() {
        Relic relic = mock(Relic.class);
        RelicRestrictions restrictions = mock(RelicRestrictions.class);
        ActiveSlot activeSlot = mock(ActiveSlot.class);

        when(relic.getId()).thenReturn("crown");
        when(relic.getRestrictions()).thenReturn(restrictions);
        when(relic.getActiveSlot()).thenReturn(activeSlot);
        when(relic.getDisplayName()).thenReturn("Crown of the Server");
        when(restrictions.isForcePvp()).thenReturn(true);

        // Note: We no longer check active slot for command blocking
        // If player holds a force-pvp relic anywhere, /pvp is blocked

        return relic;
    }

    private Relic createNonForcingRelic() {
        Relic relic = mock(Relic.class);
        RelicRestrictions restrictions = mock(RelicRestrictions.class);
        ActiveSlot activeSlot = mock(ActiveSlot.class);

        when(relic.getId()).thenReturn("scepter");
        when(relic.getRestrictions()).thenReturn(restrictions);
        when(relic.getActiveSlot()).thenReturn(activeSlot);
        when(restrictions.isForcePvp()).thenReturn(false);

        return relic;
    }
}
