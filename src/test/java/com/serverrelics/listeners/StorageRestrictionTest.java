package com.serverrelics.listeners;

import com.serverrelics.relics.RelicRestrictions;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.inventory.InventoryType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Critical tests for storage restriction configuration and enforcement.
 *
 * CRITICAL BUG HISTORY:
 * - Bug: Crown could be placed in chests, ender chests, and hoppers
 *   despite config having no-chest-storage, no-ender-chest, no-hopper set to true
 *
 * These tests verify that storage restrictions are loaded correctly.
 */
class StorageRestrictionTest {

    @Nested
    @DisplayName("Config Loading Tests")
    class ConfigLoadingTests {

        @Test
        @DisplayName("CRITICAL: All storage restrictions load from config correctly")
        void allStorageRestrictionsLoadCorrectly() {
            YamlConfiguration config = new YamlConfiguration();
            config.set("no-chest-storage", true);
            config.set("no-ender-chest", true);
            config.set("no-shulker-box", true);
            config.set("no-item-frame", true);
            config.set("no-armor-stand", true);
            config.set("no-barrel", true);
            config.set("no-hopper", true);
            config.set("no-dropper", true);
            config.set("no-dispenser", true);

            RelicRestrictions restrictions = RelicRestrictions.fromConfig(config);

            // Verify all flags are set
            assertTrue(restrictions.isNoChestStorage(), "no-chest-storage must be true");
            assertTrue(restrictions.isNoEnderChest(), "no-ender-chest must be true");
            assertTrue(restrictions.isNoShulkerBox(), "no-shulker-box must be true");
            assertTrue(restrictions.isNoItemFrame(), "no-item-frame must be true");
            assertTrue(restrictions.isNoArmorStand(), "no-armor-stand must be true");
            assertTrue(restrictions.isNoBarrel(), "no-barrel must be true");
            assertTrue(restrictions.isNoHopper(), "no-hopper must be true");
            assertTrue(restrictions.isNoDropper(), "no-dropper must be true");
            assertTrue(restrictions.isNoDispenser(), "no-dispenser must be true");

            // Verify blocked inventory types set is populated
            var blocked = restrictions.getBlockedInventoryTypes();
            assertTrue(blocked.contains(InventoryType.CHEST), "CHEST must be blocked");
            assertTrue(blocked.contains(InventoryType.ENDER_CHEST), "ENDER_CHEST must be blocked");
            assertTrue(blocked.contains(InventoryType.SHULKER_BOX), "SHULKER_BOX must be blocked");
            assertTrue(blocked.contains(InventoryType.BARREL), "BARREL must be blocked");
            assertTrue(blocked.contains(InventoryType.HOPPER), "HOPPER must be blocked");
            assertTrue(blocked.contains(InventoryType.DROPPER), "DROPPER must be blocked");
            assertTrue(blocked.contains(InventoryType.DISPENSER), "DISPENSER must be blocked");
        }

        @Test
        @DisplayName("Crown config matches expected restrictions")
        void crownConfigHasCorrectRestrictions() {
            // Simulate the actual crown config
            YamlConfiguration config = new YamlConfiguration();
            config.set("no-chest-storage", true);
            config.set("no-ender-chest", true);
            config.set("no-shulker-box", true);
            config.set("no-item-frame", true);
            config.set("no-armor-stand", true);
            config.set("no-barrel", true);
            config.set("no-hopper", true);
            config.set("no-dropper", true);
            config.set("no-dispenser", true);
            config.set("force-pvp", true);
            config.set("always-drop-on-death", true);

            RelicRestrictions restrictions = RelicRestrictions.fromConfig(config);

            // Crown must block all storage
            assertTrue(restrictions.hasAnyStorageRestrictions());
            assertTrue(restrictions.isInventoryTypeBlocked(InventoryType.CHEST));
            assertTrue(restrictions.isInventoryTypeBlocked(InventoryType.ENDER_CHEST));
            assertTrue(restrictions.isInventoryTypeBlocked(InventoryType.HOPPER));

            // Crown must force PvP and drop on death
            assertTrue(restrictions.isForcePvp());
            assertTrue(restrictions.isAlwaysDropOnDeath());
        }

        @Test
        @DisplayName("Default restrictions block nothing")
        void defaultRestrictionsBlockNothing() {
            RelicRestrictions restrictions = RelicRestrictions.defaults();

            assertFalse(restrictions.isNoChestStorage());
            assertFalse(restrictions.isNoEnderChest());
            assertFalse(restrictions.isNoHopper());
            assertFalse(restrictions.hasAnyStorageRestrictions());
            assertTrue(restrictions.getBlockedInventoryTypes().isEmpty());
        }

        @Test
        @DisplayName("Null config section returns defaults")
        void nullConfigReturnsDefaults() {
            RelicRestrictions restrictions = RelicRestrictions.fromConfig(null);

            assertNotNull(restrictions);
            assertFalse(restrictions.isNoChestStorage());
            assertFalse(restrictions.isForcePvp());
        }
    }

    @Nested
    @DisplayName("Inventory Type Blocking Tests")
    class InventoryTypeBlockingTests {

        @ParameterizedTest
        @EnumSource(value = InventoryType.class, names = {
            "CHEST", "ENDER_CHEST", "SHULKER_BOX", "BARREL", "HOPPER", "DROPPER", "DISPENSER"
        })
        @DisplayName("CRITICAL: Each storage type is individually blocked")
        void eachStorageTypeBlocked(InventoryType type) {
            YamlConfiguration config = new YamlConfiguration();

            // Map inventory type to config key
            String configKey = switch (type) {
                case CHEST -> "no-chest-storage";
                case ENDER_CHEST -> "no-ender-chest";
                case SHULKER_BOX -> "no-shulker-box";
                case BARREL -> "no-barrel";
                case HOPPER -> "no-hopper";
                case DROPPER -> "no-dropper";
                case DISPENSER -> "no-dispenser";
                default -> throw new IllegalArgumentException("Unexpected type: " + type);
            };

            config.set(configKey, true);
            RelicRestrictions restrictions = RelicRestrictions.fromConfig(config);

            assertTrue(restrictions.isInventoryTypeBlocked(type),
                "InventoryType." + type + " must be blocked when " + configKey + " is true");
        }

        @ParameterizedTest
        @EnumSource(value = InventoryType.class, names = {
            "PLAYER", "CRAFTING", "CREATIVE", "WORKBENCH", "FURNACE", "ANVIL"
        })
        @DisplayName("Non-storage inventory types are never blocked")
        void nonStorageTypesNeverBlocked(InventoryType type) {
            // Even with all restrictions enabled
            YamlConfiguration config = new YamlConfiguration();
            config.set("no-chest-storage", true);
            config.set("no-ender-chest", true);
            config.set("no-shulker-box", true);
            config.set("no-barrel", true);
            config.set("no-hopper", true);
            config.set("no-dropper", true);
            config.set("no-dispenser", true);

            RelicRestrictions restrictions = RelicRestrictions.fromConfig(config);

            assertFalse(restrictions.isInventoryTypeBlocked(type),
                "InventoryType." + type + " should never be blocked");
        }

        @Test
        @DisplayName("Blocked types set has correct size")
        void blockedTypesSetHasCorrectSize() {
            YamlConfiguration config = new YamlConfiguration();
            config.set("no-chest-storage", true);
            config.set("no-hopper", true);

            RelicRestrictions restrictions = RelicRestrictions.fromConfig(config);

            var blocked = restrictions.getBlockedInventoryTypes();
            assertEquals(2, blocked.size());
            assertTrue(blocked.contains(InventoryType.CHEST));
            assertTrue(blocked.contains(InventoryType.HOPPER));
            assertFalse(blocked.contains(InventoryType.BARREL));
        }
    }

    @Nested
    @DisplayName("Storage Restrictions Combined Tests")
    class CombinedTests {

        @Test
        @DisplayName("hasAnyStorageRestrictions detects any restriction")
        void hasAnyStorageRestrictionsDetectsAny() {
            // No restrictions
            assertFalse(RelicRestrictions.defaults().hasAnyStorageRestrictions());

            // Each type should trigger true
            String[] restrictionKeys = {
                "no-chest-storage", "no-ender-chest", "no-shulker-box",
                "no-item-frame", "no-armor-stand", "no-barrel",
                "no-hopper", "no-dropper", "no-dispenser"
            };

            for (String key : restrictionKeys) {
                YamlConfiguration config = new YamlConfiguration();
                config.set(key, true);
                RelicRestrictions restrictions = RelicRestrictions.fromConfig(config);
                assertTrue(restrictions.hasAnyStorageRestrictions(),
                    "hasAnyStorageRestrictions should be true when " + key + " is set");
            }
        }
    }
}
