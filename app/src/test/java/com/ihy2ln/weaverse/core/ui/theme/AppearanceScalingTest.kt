package com.ihy2ln.weaverse.core.ui.theme

import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AppearanceScalingTest {
    @Test
    fun lineSpacingRewritesLeadingAsAMultipleOfEachStylesFontSize() {
        val base = AppearanceProfile.Classic.typography
        val spaced = base.withLineSpacing(1.5f)
        // bodyLarge is 16sp on 26sp leading: 26 / 16 × 1.5.
        assertEquals((26f / 16f * 1.5f).em.value, spaced.bodyLarge.lineHeight.value, 0.001f)
        assertEquals(true, spaced.bodyLarge.lineHeight.isEm)
        assertEquals(16.sp, spaced.bodyLarge.fontSize)
    }

    @Test
    fun defaultLineSpacingKeepsEachStylesOriginalRatio() {
        val base = AppearanceProfile.Fantasy.typography
        val spaced = base.withLineSpacing(1f)
        assertEquals(base.titleLarge.lineHeight.value / base.titleLarge.fontSize.value, spaced.titleLarge.lineHeight.value, 0.001f)
    }

    @Test
    fun everyProfileSchemeUsesItsOwnSurfacesNotMaterialDefaults() {
        AppearanceProfile.entries.filter { it != AppearanceProfile.Streaming }.forEach { profile ->
            listOf(AppThemeMode.Light, AppThemeMode.Dark).forEach { mode ->
                val tokens = profile.tokens(mode)
                val scheme = colorSchemeFrom(tokens, mode.isDark)
                assertEquals(tokens.panel, scheme.surfaceContainer, "${profile.name} $mode surfaceContainer")
                assertEquals(tokens.hover, scheme.surfaceContainerHighest, "${profile.name} $mode surfaceContainerHighest")
                assertEquals(tokens.activePill, scheme.secondary, "${profile.name} $mode secondary")
            }
        }
    }

    @Test
    fun unknownBackdropNamesFallBackToProfileArt() {
        assertEquals(BackdropStyle.Profile, BackdropStyle.fromName("Nope"))
        assertEquals(BackdropStyle.Bloom, BackdropStyle.fromName("Bloom"))
    }
}
