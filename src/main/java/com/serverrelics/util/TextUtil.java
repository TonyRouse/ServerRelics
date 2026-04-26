package com.serverrelics.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * Utility class for text formatting and color codes.
 */
public final class TextUtil {

    private static final LegacyComponentSerializer LEGACY_SERIALIZER =
        LegacyComponentSerializer.legacyAmpersand();

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private TextUtil() {
        // Utility class
    }

    /**
     * Convert legacy color codes (&a, &l, etc.) to Adventure Component
     */
    public static Component colorize(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }
        // Use legacy serializer to parse & codes
        return LEGACY_SERIALIZER.deserialize(text)
            .decoration(TextDecoration.ITALIC, false); // Remove default italic from lore
    }

    /**
     * Strip color codes from text
     */
    public static String stripColor(String text) {
        if (text == null) return "";
        return text.replaceAll("&[0-9a-fk-or]", "");
    }

    /**
     * Convert Component to plain text
     */
    public static String toPlainText(Component component) {
        if (component == null) return "";
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
            .plainText().serialize(component);
    }

    /**
     * Convert Component to legacy string with & codes
     */
    public static String toLegacy(Component component) {
        if (component == null) return "";
        return LEGACY_SERIALIZER.serialize(component);
    }

    /**
     * Parse MiniMessage format to Component
     */
    public static Component miniMessage(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }
        return MINI_MESSAGE.deserialize(text);
    }

    /**
     * Format time in seconds to human-readable string
     */
    public static String formatTime(long seconds) {
        if (seconds < 0) seconds = 0;

        if (seconds < 60) {
            return seconds + "s";
        } else if (seconds < 3600) {
            long mins = seconds / 60;
            long secs = seconds % 60;
            return mins + "m " + secs + "s";
        } else if (seconds < 86400) {
            long hours = seconds / 3600;
            long mins = (seconds % 3600) / 60;
            return hours + "h " + mins + "m";
        } else {
            long days = seconds / 86400;
            long hours = (seconds % 86400) / 3600;
            return days + "d " + hours + "h";
        }
    }

    /**
     * Format a number with commas
     */
    public static String formatNumber(long number) {
        return String.format("%,d", number);
    }
}
