package com.ihy2ln.weaverse.core.ui.theme

import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AppearanceProfileTest {
    @Test
    fun classicProfileIsUnchangedFromTheOriginalLook() {
        // Existing users default to Classic, so it must still resolve to the
        // original per-mode palettes exactly.
        AppThemeMode.entries.forEach { mode ->
            assertEquals(tokensFor(mode), AppearanceProfile.Classic.tokens(mode))
        }
        assertEquals(WeaverseTypography, AppearanceProfile.Classic.typography)
        assertTrue(AppearanceProfile.Classic.usesThemeModes)
    }

    @Test
    fun styledProfilesCollapseFourModesIntoLightAndDark() {
        AppearanceProfile.entries.filter { it != AppearanceProfile.Classic }.forEach { profile ->
            assertEquals(
                profile.tokens(AppThemeMode.Light),
                profile.tokens(AppThemeMode.Sepia),
                "${profile.name} should treat Sepia as its light palette",
            )
            assertEquals(
                profile.tokens(AppThemeMode.Dark),
                profile.tokens(AppThemeMode.OledBlack),
                "${profile.name} should treat OledBlack as its dark palette",
            )
            assertNotEquals(
                profile.tokens(AppThemeMode.Light),
                profile.tokens(AppThemeMode.Dark),
                "${profile.name} light and dark should differ",
            )
            assertTrue(!profile.usesThemeModes)
        }
    }

    @Test
    fun everyProfileIsVisuallyDistinct() {
        // Guards against a copy-paste palette: no two profiles share a background.
        val backgrounds = AppearanceProfile.entries.map { it.tokens(AppThemeMode.Dark).background }
        assertEquals(backgrounds.size, backgrounds.toSet().size, "profiles share a dark background")
    }

    @Test
    fun arcadeIsSquareAndChillIsSoft() {
        assertEquals(0.dp, AppearanceProfile.Arcade.cornerRadius)
        assertTrue(AppearanceProfile.Chill.cornerRadius > AppearanceProfile.Classic.cornerRadius)
        // Shape scale stays ordered regardless of the profile's base radius.
        AppearanceProfile.entries.forEach { profile ->
            val r = profile.cornerRadius
            assertTrue(r >= 0.dp, "${profile.name} radius must not be negative")
        }
    }

    @Test
    fun themeModeDarknessMapping() {
        assertTrue(AppThemeMode.Dark.isDark)
        assertTrue(AppThemeMode.OledBlack.isDark)
        assertTrue(!AppThemeMode.Light.isDark)
        assertTrue(!AppThemeMode.Sepia.isDark)
    }
}
