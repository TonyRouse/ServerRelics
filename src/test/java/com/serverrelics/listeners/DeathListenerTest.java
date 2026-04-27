package com.serverrelics.listeners;

import com.serverrelics.ServerRelics;
import com.serverrelics.hooks.PvPManagerHook;
import com.serverrelics.managers.RelicManager;
import com.serverrelics.managers.StatsManager;
import com.serverrelics.relics.Relic;
import com.serverrelics.relics.RelicRegistry;
import com.serverrelics.relics.RelicRestrictions;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Critical tests for death handling to prevent relic duplication.
 *
 * CRITICAL BUG HISTORY:
 * - Bug: When player dies with crown, TWO crowns would appear (duplication)
 * - Root cause: ServerRelics removed crown from drops and spawned it, but
 *   AxGraves (with override-keep-inventory) grabbed it from player's armor
 *   slot which wasn't cleared
 * - Fix: DeathListener now clears the relic from player inventory/armor
 *   immediately when handling always-drop-on-death relics
 *
 * These tests MUST pass before any deployment.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DeathListenerTest {

    @Mock private ServerRelics plugin;
    @Mock private RelicRegistry relicRegistry;
    @Mock private RelicManager relicManager;
    @Mock private StatsManager statsManager;
    @Mock private PvPManagerHook pvpManagerHook;
    @Mock private Player player;
    @Mock private PlayerInventory inventory;
    @Mock private World world;
    @Mock private Location location;
    @Mock private Item droppedItem;

    private DeathListener deathListener;

    @BeforeEach
    void setUp() {
        when(plugin.getRelicRegistry()).thenReturn(relicRegistry);
        when(plugin.getRelicManager()).thenReturn(relicManager);
        when(plugin.getStatsManager()).thenReturn(statsManager);

        when(player.getInventory()).thenReturn(inventory);
        when(player.getLocation()).thenReturn(location);
        when(player.getWorld()).thenReturn(world);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(location.getWorld()).thenReturn(world);
        when(location.getY()).thenReturn(64.0);
        when(world.getMinHeight()).thenReturn(-64);
        when(world.dropItem(any(Location.class), any(ItemStack.class))).thenReturn(droppedItem);

        deathListener = new DeathListener(plugin);
    }

    @Nested
    @DisplayName("Death Duplication Prevention Tests")
    class DeathDuplicationTests {

        @Test
        @DisplayName("CRITICAL: Relic should be removed from drops when always-drop-on-death is true")
        void relicRemovedFromDrops_whenAlwaysDropOnDeath() {
            // Given: A crown relic with always-drop-on-death enabled
            ItemStack crownItem = mock(ItemStack.class);
            ItemStack diamondItem = mock(ItemStack.class);
            Relic crownRelic = createMockRelic("crown", true, false);

            // Mock static method
            try (MockedStatic<Relic> relicStatic = mockStatic(Relic.class)) {
                relicStatic.when(() -> Relic.isAnyRelic(crownItem)).thenReturn(true);
                relicStatic.when(() -> Relic.isAnyRelic(diamondItem)).thenReturn(false);
                when(relicRegistry.getRelicFromItem(crownItem)).thenReturn(crownRelic);

                List<ItemStack> drops = new ArrayList<>();
                drops.add(crownItem);
                drops.add(diamondItem);

                when(inventory.getArmorContents()).thenReturn(new ItemStack[4]);
                when(inventory.getItemInOffHand()).thenReturn(null);

                PlayerDeathEvent event = mock(PlayerDeathEvent.class);
                when(event.getEntity()).thenReturn(player);
                when(event.getDrops()).thenReturn(drops);
                when(event.getKeepInventory()).thenReturn(false);

                // When: Player dies
                deathListener.onPlayerDeath(event);

                // Then: Crown should be removed from the drops list
                assertFalse(drops.contains(crownItem),
                    "CRITICAL: Relic must be removed from drops to prevent grave plugins from getting it");
                assertEquals(1, drops.size(), "Only non-relic items should remain in drops");
                assertTrue(drops.contains(diamondItem));
            }
        }

        @Test
        @DisplayName("CRITICAL: Player inventory must be cleared when relic drops on death")
        void playerInventoryCleared_whenRelicDropsOnDeath() {
            // Given: A crown in inventory
            ItemStack crownItem = mock(ItemStack.class);
            Relic crownRelic = createMockRelic("crown", true, false);

            try (MockedStatic<Relic> relicStatic = mockStatic(Relic.class)) {
                relicStatic.when(() -> Relic.isAnyRelic(crownItem)).thenReturn(true);
                when(relicRegistry.getRelicFromItem(crownItem)).thenReturn(crownRelic);

                List<ItemStack> drops = new ArrayList<>();
                drops.add(crownItem);

                when(inventory.getArmorContents()).thenReturn(new ItemStack[4]);
                when(inventory.getItemInOffHand()).thenReturn(null);

                PlayerDeathEvent event = mock(PlayerDeathEvent.class);
                when(event.getEntity()).thenReturn(player);
                when(event.getDrops()).thenReturn(drops);
                when(event.getKeepInventory()).thenReturn(false);

                // When: Player dies
                deathListener.onPlayerDeath(event);

                // Then: Inventory.remove should be called to clear the item
                verify(inventory).remove(crownItem);
            }
        }

        @Test
        @DisplayName("CRITICAL: Player armor must be cleared when helmet relic drops on death")
        void playerArmorCleared_whenHelmetRelicDropsOnDeath() {
            // Given: A crown worn in helmet slot
            ItemStack crownItem = mock(ItemStack.class);
            Relic crownRelic = createMockRelic("crown", true, false);

            try (MockedStatic<Relic> relicStatic = mockStatic(Relic.class)) {
                relicStatic.when(() -> Relic.isAnyRelic(crownItem)).thenReturn(true);
                when(relicRegistry.getRelicFromItem(crownItem)).thenReturn(crownRelic);

                List<ItemStack> drops = new ArrayList<>();
                drops.add(crownItem);

                // Crown is in helmet slot (index 3)
                ItemStack[] armorContents = new ItemStack[4];
                armorContents[3] = crownItem;
                when(inventory.getArmorContents()).thenReturn(armorContents);
                when(inventory.getItemInOffHand()).thenReturn(null);

                PlayerDeathEvent event = mock(PlayerDeathEvent.class);
                when(event.getEntity()).thenReturn(player);
                when(event.getDrops()).thenReturn(drops);
                when(event.getKeepInventory()).thenReturn(false);

                // When: Player dies
                deathListener.onPlayerDeath(event);

                // Then: Armor should be updated to remove the crown
                ArgumentCaptor<ItemStack[]> armorCaptor = ArgumentCaptor.forClass(ItemStack[].class);
                verify(inventory).setArmorContents(armorCaptor.capture());
                ItemStack[] newArmor = armorCaptor.getValue();
                assertNull(newArmor[3], "Helmet slot should be null after removal");
            }
        }

        @Test
        @DisplayName("CRITICAL: Only ONE spawn call should happen per relic on death")
        void onlyOneRelicSpawned_onDeath() {
            // Given: A crown in drops
            ItemStack crownItem = mock(ItemStack.class);
            Relic crownRelic = createMockRelic("crown", true, false);

            try (MockedStatic<Relic> relicStatic = mockStatic(Relic.class)) {
                relicStatic.when(() -> Relic.isAnyRelic(crownItem)).thenReturn(true);
                when(relicRegistry.getRelicFromItem(crownItem)).thenReturn(crownRelic);

                List<ItemStack> drops = new ArrayList<>();
                drops.add(crownItem);

                when(inventory.getArmorContents()).thenReturn(new ItemStack[4]);
                when(inventory.getItemInOffHand()).thenReturn(null);

                PlayerDeathEvent event = mock(PlayerDeathEvent.class);
                when(event.getEntity()).thenReturn(player);
                when(event.getDrops()).thenReturn(drops);
                when(event.getKeepInventory()).thenReturn(false);

                // When: Player dies
                deathListener.onPlayerDeath(event);

                // Then: world.dropItem should only be called ONCE
                verify(world, times(1)).dropItem(any(Location.class), eq(crownItem));
            }
        }

        @Test
        @DisplayName("Relic manager should clear holder on death drop")
        void relicManagerClearsHolder_onDeathDrop() {
            ItemStack crownItem = mock(ItemStack.class);
            Relic crownRelic = createMockRelic("crown", true, false);

            try (MockedStatic<Relic> relicStatic = mockStatic(Relic.class)) {
                relicStatic.when(() -> Relic.isAnyRelic(crownItem)).thenReturn(true);
                when(relicRegistry.getRelicFromItem(crownItem)).thenReturn(crownRelic);

                List<ItemStack> drops = new ArrayList<>();
                drops.add(crownItem);

                when(inventory.getArmorContents()).thenReturn(new ItemStack[4]);
                when(inventory.getItemInOffHand()).thenReturn(null);

                PlayerDeathEvent event = mock(PlayerDeathEvent.class);
                when(event.getEntity()).thenReturn(player);
                when(event.getDrops()).thenReturn(drops);
                when(event.getKeepInventory()).thenReturn(false);

                // When
                deathListener.onPlayerDeath(event);

                // Then
                verify(relicManager).clearHolder("crown");
            }
        }
    }

    @Nested
    @DisplayName("Non-Drop Relic Tests")
    class NonDropRelicTests {

        @Test
        @DisplayName("Relics without always-drop-on-death stay in normal drops")
        void relicsWithoutAlwaysDropStayInNormalDrops() {
            ItemStack scepterItem = mock(ItemStack.class);
            Relic scepterRelic = createMockRelic("scepter", false, false); // NOT always-drop

            try (MockedStatic<Relic> relicStatic = mockStatic(Relic.class)) {
                relicStatic.when(() -> Relic.isAnyRelic(scepterItem)).thenReturn(true);
                when(relicRegistry.getRelicFromItem(scepterItem)).thenReturn(scepterRelic);

                List<ItemStack> drops = new ArrayList<>();
                drops.add(scepterItem);

                PlayerDeathEvent event = mock(PlayerDeathEvent.class);
                when(event.getEntity()).thenReturn(player);
                when(event.getDrops()).thenReturn(drops);
                when(event.getKeepInventory()).thenReturn(false);

                // When
                deathListener.onPlayerDeath(event);

                // Then: Relic should still be in drops (normal behavior)
                assertTrue(drops.contains(scepterItem));
                verify(world, never()).dropItem(any(), any());
            }
        }
    }

    // Helper methods

    private Relic createMockRelic(String id, boolean alwaysDropOnDeath, boolean trackDeaths) {
        Relic relic = mock(Relic.class);
        RelicRestrictions restrictions = mock(RelicRestrictions.class);

        when(relic.getId()).thenReturn(id);
        when(relic.getRestrictions()).thenReturn(restrictions);
        when(relic.isTrackDeaths()).thenReturn(trackDeaths);
        when(restrictions.isAlwaysDropOnDeath()).thenReturn(alwaysDropOnDeath);
        when(restrictions.isNeverDespawn()).thenReturn(true);
        when(restrictions.isIndestructible()).thenReturn(true);

        return relic;
    }
}
