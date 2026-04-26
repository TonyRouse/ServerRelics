package com.serverrelics.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TextUtil formatting utilities
 */
class TextUtilTest {

    @Test
    void stripColor_removesColorCodes() {
        assertEquals("Hello World", TextUtil.stripColor("&aHello &bWorld"));
        assertEquals("Test", TextUtil.stripColor("&l&nTest"));
        assertEquals("Plain", TextUtil.stripColor("Plain"));
    }

    @Test
    void stripColor_handlesNull() {
        assertEquals("", TextUtil.stripColor(null));
    }

    @Test
    void stripColor_handlesEmpty() {
        assertEquals("", TextUtil.stripColor(""));
    }

    @ParameterizedTest
    @CsvSource({
        "0, 0s",
        "30, 30s",
        "59, 59s",
        "60, 1m 0s",
        "90, 1m 30s",
        "3599, 59m 59s",
        "3600, 1h 0m",
        "3661, 1h 1m",
        "7200, 2h 0m",
        "86399, 23h 59m",
        "86400, 1d 0h",
        "90000, 1d 1h",
        "172800, 2d 0h"
    })
    void formatTime_formatsCorrectly(long seconds, String expected) {
        assertEquals(expected, TextUtil.formatTime(seconds));
    }

    @Test
    void formatTime_handlesNegative() {
        assertEquals("0s", TextUtil.formatTime(-1));
        assertEquals("0s", TextUtil.formatTime(-100));
    }

    @Test
    void formatNumber_formatsWithCommas() {
        assertEquals("1,000", TextUtil.formatNumber(1000));
        assertEquals("1,000,000", TextUtil.formatNumber(1000000));
        assertEquals("100", TextUtil.formatNumber(100));
        assertEquals("0", TextUtil.formatNumber(0));
    }
}
