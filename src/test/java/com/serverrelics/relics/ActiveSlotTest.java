package com.serverrelics.relics;

import org.bukkit.inventory.EquipmentSlot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ActiveSlot enum parsing and equipment slot mapping
 */
class ActiveSlotTest {

    @ParameterizedTest
    @CsvSource({
        "ANY, ANY",
        "HEAD, HEAD",
        "CHEST, CHEST",
        "LEGS, LEGS",
        "FEET, FEET",
        "MAINHAND, MAINHAND",
        "OFFHAND, OFFHAND",
        "HOTBAR, HOTBAR",
        "INVENTORY, INVENTORY",
        "any, ANY",
        "head, HEAD",
        "Head, HEAD",
        "HEAD, HEAD"
    })
    void fromString_parsesCorrectly(String input, ActiveSlot expected) {
        assertEquals(expected, ActiveSlot.fromString(input));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "INVALID", "xyz", "123"})
    void fromString_defaultsToAny(String input) {
        assertEquals(ActiveSlot.ANY, ActiveSlot.fromString(input));
    }

    @Test
    void toEquipmentSlot_mapsCorrectly() {
        assertEquals(EquipmentSlot.HEAD, ActiveSlot.HEAD.toEquipmentSlot());
        assertEquals(EquipmentSlot.CHEST, ActiveSlot.CHEST.toEquipmentSlot());
        assertEquals(EquipmentSlot.LEGS, ActiveSlot.LEGS.toEquipmentSlot());
        assertEquals(EquipmentSlot.FEET, ActiveSlot.FEET.toEquipmentSlot());
        assertEquals(EquipmentSlot.HAND, ActiveSlot.MAINHAND.toEquipmentSlot());
        assertEquals(EquipmentSlot.OFF_HAND, ActiveSlot.OFFHAND.toEquipmentSlot());
    }

    @Test
    void toEquipmentSlot_returnsNullForNonArmor() {
        assertNull(ActiveSlot.ANY.toEquipmentSlot());
        assertNull(ActiveSlot.HOTBAR.toEquipmentSlot());
        assertNull(ActiveSlot.INVENTORY.toEquipmentSlot());
    }

    @Test
    void isArmorSlot_identifiesArmorSlots() {
        assertTrue(ActiveSlot.HEAD.isArmorSlot());
        assertTrue(ActiveSlot.CHEST.isArmorSlot());
        assertTrue(ActiveSlot.LEGS.isArmorSlot());
        assertTrue(ActiveSlot.FEET.isArmorSlot());

        assertFalse(ActiveSlot.ANY.isArmorSlot());
        assertFalse(ActiveSlot.MAINHAND.isArmorSlot());
        assertFalse(ActiveSlot.OFFHAND.isArmorSlot());
        assertFalse(ActiveSlot.HOTBAR.isArmorSlot());
        assertFalse(ActiveSlot.INVENTORY.isArmorSlot());
    }

    @Test
    void allValuesHaveNames() {
        for (ActiveSlot slot : ActiveSlot.values()) {
            assertNotNull(slot.name());
            assertFalse(slot.name().isEmpty());
        }
    }
}
