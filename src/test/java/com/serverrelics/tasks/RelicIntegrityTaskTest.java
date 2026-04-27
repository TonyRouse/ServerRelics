package com.serverrelics.tasks;

import com.serverrelics.ServerRelics;
import com.serverrelics.managers.RelicManager;
import com.serverrelics.relics.Relic;
import com.serverrelics.relics.RelicRegistry;
import com.serverrelics.relics.RelicRestrictions;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for RelicIntegrityTask to prevent regressions.
 *
 * CRITICAL BUG HISTORY:
 * - Bug: Crown would get "delinked" when moved between inventory slots
 * - Root cause: playerHasRelic() didn't check the cursor, so when the
 *   relic was on the cursor during an inventory move and the integrity
 *   check ran, it would clear the holder
 * - Fix: Added cursor check to playerHasRelic()
 *
 * These tests MUST pass before any deployment.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RelicIntegrityTaskTest {

    @Mock private ServerRelics plugin;
    @Mock private RelicRegistry relicRegistry;
    @Mock private RelicManager relicManager;
    @Mock private Player player;
    @Mock private PlayerInventory inventory;
    @Mock private Relic relic;
    @Mock private RelicRestrictions restrictions;

    private RelicIntegrityTask task;

    @BeforeEach
    void setUp() {
        when(plugin.getRelicRegistry()).thenReturn(relicRegistry);
        when(plugin.getRelicManager()).thenReturn(relicManager);
        when(player.getInventory()).thenReturn(inventory);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(relic.getId()).thenReturn("crown");
        when(relic.isEnabled()).thenReturn(true);
        when(relic.isUnique()).thenReturn(true);
        when(relic.getRestrictions()).thenReturn(restrictions);

        task = new RelicIntegrityTask(plugin);
    }

    @Nested
    @DisplayName("CRITICAL: Cursor Detection Tests")
    class CursorDetectionTests {

        @Test
        @DisplayName("CRITICAL: playerHasRelic should return true when relic is on cursor")
        void playerHasRelic_whenRelicOnCursor_returnsTrue() throws Exception {
            // Given: Crown is on player's cursor (being moved between slots)
            ItemStack crownItem = mock(ItemStack.class);

            when(player.getItemOnCursor()).thenReturn(crownItem);
            when(relic.isThisRelic(crownItem)).thenReturn(true);
            when(inventory.getContents()).thenReturn(new ItemStack[36]);
            when(inventory.getArmorContents()).thenReturn(new ItemStack[4]);
            when(inventory.getItemInOffHand()).thenReturn(null);

            // When: We check if player has relic
            boolean result = invokePlayerHasRelic(player, relic);

            // Then: Should return true (relic is on cursor)
            assertTrue(result,
                "CRITICAL: playerHasRelic must detect relics on cursor to prevent " +
                "delink bug when moving items between inventory slots");
        }

        @Test
        @DisplayName("playerHasRelic should return true when relic is in main inventory")
        void playerHasRelic_whenRelicInInventory_returnsTrue() throws Exception {
            // Given: Crown is in main inventory
            ItemStack crownItem = mock(ItemStack.class);
            ItemStack[] contents = new ItemStack[36];
            contents[5] = crownItem;

            when(player.getItemOnCursor()).thenReturn(null);
            when(relic.isThisRelic(crownItem)).thenReturn(true);
            when(relic.isThisRelic(null)).thenReturn(false);
            when(inventory.getContents()).thenReturn(contents);
            when(inventory.getArmorContents()).thenReturn(new ItemStack[4]);
            when(inventory.getItemInOffHand()).thenReturn(null);

            // When
            boolean result = invokePlayerHasRelic(player, relic);

            // Then
            assertTrue(result);
        }

        @Test
        @DisplayName("playerHasRelic should return true when relic is in armor slot")
        void playerHasRelic_whenRelicInArmor_returnsTrue() throws Exception {
            // Given: Crown is in helmet slot
            ItemStack crownItem = mock(ItemStack.class);
            ItemStack[] armor = new ItemStack[4];
            armor[3] = crownItem; // Helmet slot

            when(player.getItemOnCursor()).thenReturn(null);
            when(relic.isThisRelic(crownItem)).thenReturn(true);
            when(relic.isThisRelic(null)).thenReturn(false);
            when(inventory.getContents()).thenReturn(new ItemStack[36]);
            when(inventory.getArmorContents()).thenReturn(armor);
            when(inventory.getItemInOffHand()).thenReturn(null);

            // When
            boolean result = invokePlayerHasRelic(player, relic);

            // Then
            assertTrue(result);
        }

        @Test
        @DisplayName("playerHasRelic should return true when relic is in offhand")
        void playerHasRelic_whenRelicInOffhand_returnsTrue() throws Exception {
            // Given: Crown is in offhand
            ItemStack crownItem = mock(ItemStack.class);

            when(player.getItemOnCursor()).thenReturn(null);
            when(relic.isThisRelic(crownItem)).thenReturn(true);
            when(relic.isThisRelic(null)).thenReturn(false);
            when(inventory.getContents()).thenReturn(new ItemStack[36]);
            when(inventory.getArmorContents()).thenReturn(new ItemStack[4]);
            when(inventory.getItemInOffHand()).thenReturn(crownItem);

            // When
            boolean result = invokePlayerHasRelic(player, relic);

            // Then
            assertTrue(result);
        }

        @Test
        @DisplayName("playerHasRelic should return false when relic is nowhere")
        void playerHasRelic_whenRelicNowhere_returnsFalse() throws Exception {
            // Given: Player has no relic anywhere
            when(player.getItemOnCursor()).thenReturn(null);
            when(relic.isThisRelic(null)).thenReturn(false);
            when(inventory.getContents()).thenReturn(new ItemStack[36]);
            when(inventory.getArmorContents()).thenReturn(new ItemStack[4]);
            when(inventory.getItemInOffHand()).thenReturn(null);

            // When
            boolean result = invokePlayerHasRelic(player, relic);

            // Then
            assertFalse(result);
        }

        @Test
        @DisplayName("CRITICAL: Cursor check should be first (optimization)")
        void cursorCheckShouldBeFirst() throws Exception {
            // Given: Crown is on cursor
            ItemStack crownItem = mock(ItemStack.class);
            ItemStack[] contents = new ItemStack[36];
            for (int i = 0; i < 36; i++) {
                contents[i] = mock(ItemStack.class);
            }

            when(player.getItemOnCursor()).thenReturn(crownItem);
            when(relic.isThisRelic(crownItem)).thenReturn(true);

            // When
            boolean result = invokePlayerHasRelic(player, relic);

            // Then: Should return true immediately without checking inventory
            assertTrue(result);
            verify(inventory, never()).getContents();
        }
    }

    /**
     * Use reflection to access the private playerHasRelic method
     */
    private boolean invokePlayerHasRelic(Player player, Relic relic) throws Exception {
        Method method = RelicIntegrityTask.class.getDeclaredMethod("playerHasRelic", Player.class, Relic.class);
        method.setAccessible(true);
        return (boolean) method.invoke(task, player, relic);
    }
}
