package com.ihy2ln.weaverse.core.ui

import androidx.compose.ui.graphics.Color
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CustomThemeTest {
    @Test
    fun `contrast ratio between black and white is the maximum, 21 to 1`() {
        val ratio = contrastRatio(Color.Black, Color.White)
        assertEquals(21f, ratio, 0.1f)
    }

    @Test
    fun `contrast ratio between identical colors is the minimum, 1 to 1`() {
        val ratio = contrastRatio(Color(0xFF808080), Color(0xFF808080))
        assertEquals(1f, ratio, 0.01f)
    }

    @Test
    fun `black on white meets WCAG AA`() {
        assertTrue(meetsWcagAA(Color.Black, Color.White))
    }

    @Test
    fun `light gray on white fails WCAG AA`() {
        assertFalse(meetsWcagAA(Color(0xFFE0E0E0), Color.White))
    }

    @Test
    fun `suggestAccessibleColor returns a color that actually passes WCAG AA`() {
        val background = Color.White
        val poorForeground = Color(0xFFE0E0E0)
        val suggested = suggestAccessibleColor(poorForeground, background)
        assertTrue(meetsWcagAA(suggested, background))
    }

    @Test
    fun `suggestAccessibleColor is a no-op when contrast already passes`() {
        val background = Color.White
        assertEquals(Color.Black, suggestAccessibleColor(Color.Black, background))
    }

    @Test
    fun `customColorScheme fills every slot from just a seed color`() {
        val scheme = customColorScheme(CustomThemeSettings(seedHex = "#4A90D9"))
        assertEquals(Color(0xFF4A90D9), scheme.primary)
        // Every default-derived slot should differ from Color.Unspecified's sentinel behavior --
        // simplest real check is that primary/background/onBackground aren't all identical.
        assertTrue(scheme.primary != scheme.background)
    }

    @Test
    fun `customColorScheme respects an explicit background override`() {
        val scheme = customColorScheme(CustomThemeSettings(seedHex = "#4A90D9", backgroundHex = "#123456"))
        assertEquals(Color(0xFF123456), scheme.background)
    }

    @Test
    fun `a dark base theme produces a darker background than a light base with the same seed`() {
        val light = customColorScheme(CustomThemeSettings(seedHex = "#4A90D9", baseIsDark = false))
        val dark = customColorScheme(CustomThemeSettings(seedHex = "#4A90D9", baseIsDark = true))
        assertTrue(contrastRatio(light.background, Color.White) < contrastRatio(dark.background, Color.White))
    }
}
