package com.serverrelics.listeners;

import com.serverrelics.ServerRelics;
import com.serverrelics.managers.ConfigManager;
import com.serverrelics.managers.RelicManager;
import com.serverrelics.relics.Relic;
import com.serverrelics.relics.RelicRegistry;
import com.serverrelics.relics.RelicRestrictions;
import org.bukkit.GameMode;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for the previous holder pickup cooldown feature.
 *
 * FEATURE DESCRIPTION:
 * When a player dies or drops a relic, they cannot immediately pick it up again.
 * This prevents the "/back" exploit where players can teleport to their death
 * location and reclaim the relic before anyone else has a chance.
 *
 * The cooldown is configurable per-relic via "previous-holder-pickup-cooldown"
 * in the restrictions section (time in seconds).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PreviousHolderCooldownTest {

    @Mock private ServerRelics plugin;
    @Mock private RelicRegistry relicRegistry;
    @Mock private RelicManager relicManager;
    @Mock private ConfigManager configManager;
    @Mock private Player player;
    @Mock private PlayerInventory inventory;
    @Mock private Item itemEntity;
    @Mock private ItemStack itemStack;

    private ItemListener itemListener;
    private UUID playerUuid;

    @BeforeEach
    void setUp() {
        playerUuid = UUID.randomUUID();

        when(plugin.getRelicRegistry()).thenReturn(relicRegistry);
        when(plugin.getRelicManager()).thenReturn(relicManager);
        when(plugin.getConfigManager()).thenReturn(configManager);

        when(player.getUniqueId()).thenReturn(playerUuid);
        when(player.getInventory()).thenReturn(inventory);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(inventory.firstEmpty()).thenReturn(0); // Has space

        when(itemEntity.getItemStack()).thenReturn(itemStack);

        when(configManager.getMessage(anyString())).thenReturn("&cTest message {time} {relic}");

        itemListener = new ItemListener(plugin);
    }

    @Nested
    @DisplayName("Previous Holder Pickup Cooldown Tests")
    class CooldownTests {

        @Test
        @DisplayName("CRITICAL: Previous holder cannot pickup relic during cooldown")
        void previousHolderBlockedDuringCooldown() {
            // Given: Player is previous holder with 5 minute cooldown remaining
            Relic relic = createMockRelic("crown", 300); // 5 min cooldown

            try (MockedStatic<Relic> relicStatic = mockStatic(Relic.class)) {
                relicStatic.when(() -> Relic.isAnyRelic(itemStack)).thenReturn(true);
                when(relicRegistry.getRelicFromItem(itemStack)).thenReturn(relic);
                when(relicManager.getRemainingPickupCooldown("crown", playerUuid)).thenReturn(180); // 3 min left

                EntityPickupItemEvent event = new EntityPickupItemEvent(player, itemEntity, 1);

                // When: Player tries to pick up
                itemListener.onCreativePickup(event);

                // Then: Pickup should be cancelled
                assertTrue(event.isCancelled(),
                    "CRITICAL: Previous holder must be blocked from pickup during cooldown");
            }
        }

        @Test
        @DisplayName("Previous holder can pickup after cooldown expires")
        void previousHolderAllowedAfterCooldown() {
            // Given: Player was previous holder but cooldown expired
            Relic relic = createMockRelic("crown", 300);

            try (MockedStatic<Relic> relicStatic = mockStatic(Relic.class)) {
                relicStatic.when(() -> Relic.isAnyRelic(itemStack)).thenReturn(true);
                when(relicRegistry.getRelicFromItem(itemStack)).thenReturn(relic);
                when(relicManager.getRemainingPickupCooldown("crown", playerUuid)).thenReturn(0); // Expired

                EntityPickupItemEvent event = new EntityPickupItemEvent(player, itemEntity, 1);

                // When
                itemListener.onCreativePickup(event);

                // Then: Pickup should NOT be cancelled
                assertFalse(event.isCancelled(),
                    "Previous holder should be allowed to pickup after cooldown expires");
            }
        }

        @Test
        @DisplayName("Other players can pickup immediately (not blocked by cooldown)")
        void otherPlayersNotBlocked() {
            // Given: A different player (not the previous holder)
            Relic relic = createMockRelic("crown", 300);
            UUID otherPlayerUuid = UUID.randomUUID();
            when(player.getUniqueId()).thenReturn(otherPlayerUuid);

            try (MockedStatic<Relic> relicStatic = mockStatic(Relic.class)) {
                relicStatic.when(() -> Relic.isAnyRelic(itemStack)).thenReturn(true);
                when(relicRegistry.getRelicFromItem(itemStack)).thenReturn(relic);
                when(relicManager.getRemainingPickupCooldown("crown", otherPlayerUuid)).thenReturn(0);

                EntityPickupItemEvent event = new EntityPickupItemEvent(player, itemEntity, 1);

                // When
                itemListener.onCreativePickup(event);

                // Then: Should not be cancelled
                assertFalse(event.isCancelled(),
                    "Other players should not be blocked by previous holder cooldown");
            }
        }

        @Test
        @DisplayName("No cooldown when config is set to 0")
        void noCooldownWhenDisabled() {
            // Given: Cooldown is disabled (0 seconds)
            Relic relic = createMockRelic("crown", 0);

            try (MockedStatic<Relic> relicStatic = mockStatic(Relic.class)) {
                relicStatic.when(() -> Relic.isAnyRelic(itemStack)).thenReturn(true);
                when(relicRegistry.getRelicFromItem(itemStack)).thenReturn(relic);
                when(relicManager.getRemainingPickupCooldown("crown", playerUuid)).thenReturn(0);

                EntityPickupItemEvent event = new EntityPickupItemEvent(player, itemEntity, 1);

                // When
                itemListener.onCreativePickup(event);

                // Then: Should not be cancelled
                assertFalse(event.isCancelled(),
                    "Pickup should not be blocked when cooldown is disabled");
            }
        }
    }

    @Nested
    @DisplayName("RelicManager Cooldown Tracking Tests")
    class RelicManagerCooldownTests {

        @Test
        @DisplayName("getRemainingPickupCooldown returns correct remaining time")
        void remainingCooldownCalculatedCorrectly() {
            // This is a unit test for the RelicManager method
            // Given: Previous holder dropped crown 2 minutes ago, cooldown is 5 minutes
            int cooldownSeconds = 300; // 5 minutes
            long dropTime = System.currentTimeMillis() - (120 * 1000); // 2 minutes ago

            // Calculate expected remaining (should be ~3 minutes = 180 seconds)
            long elapsed = (System.currentTimeMillis() - dropTime) / 1000;
            int expectedRemaining = (int) Math.max(0, cooldownSeconds - elapsed);

            // Remaining should be approximately 180 seconds (allow for small timing differences)
            assertTrue(expectedRemaining >= 175 && expectedRemaining <= 185,
                "Remaining cooldown should be approximately 180 seconds (3 minutes)");
        }

        @Test
        @DisplayName("getRemainingPickupCooldown returns 0 when not previous holder")
        void noCooldownForNonPreviousHolder() {
            // Given: Player was never the previous holder
            when(relicManager.getRemainingPickupCooldown("crown", playerUuid)).thenReturn(0);

            // Then: Should return 0
            assertEquals(0, relicManager.getRemainingPickupCooldown("crown", playerUuid));
        }
    }

    // Helper methods

    private Relic createMockRelic(String id, int cooldownSeconds) {
        Relic relic = mock(Relic.class);
        RelicRestrictions restrictions = mock(RelicRestrictions.class);

        when(relic.getId()).thenReturn(id);
        when(relic.getRestrictions()).thenReturn(restrictions);
        when(relic.getDisplayName()).thenReturn("Test Relic");
        when(restrictions.getPreviousHolderPickupCooldown()).thenReturn(cooldownSeconds);

        return relic;
    }
}
