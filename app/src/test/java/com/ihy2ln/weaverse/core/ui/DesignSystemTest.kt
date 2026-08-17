package com.ihy2ln.weaverse.core.ui

import androidx.compose.ui.unit.sp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DesignSystemTest {
    @Test
    fun `buildTypography scales body size off settings`() {
        val typography = buildTypography(TypographySettings.Manuscript)
        assertEquals(17.sp, typography.bodyMedium.fontSize)
    }

    @Test
    fun `buildTypography line height follows the multiplier`() {
        val settings = TypographySettings(fontSizeSp = 20f, lineHeightMultiplier = 2f)
        val typography = buildTypography(settings)
        assertEquals(40.sp, typography.bodyMedium.lineHeight)
    }

    @Test
    fun `each app theme has a distinct color scheme`() {
        val schemes = AppTheme.entries.map { colorSchemeFor(it) }
        val backgrounds = schemes.map { it.background }
        assertEquals(backgrounds.distinct().size, backgrounds.size)
    }

    @Test
    fun `dark themes are flagged for status bar contrast`() {
        assertTrue(AppTheme.Dark.isDark())
        assertTrue(AppTheme.OledBlack.isDark())
        assertEquals(false, AppTheme.Light.isDark())
        assertEquals(false, AppTheme.Sepia.isDark())
    }

    @Test
    fun `codex category kinds match Revision 02's fixed ten`() {
        assertEquals(10, CodexCategoryKind.entries.size)
        assertNotEquals(
            CodexCategoryKind.Characters.defaultColor,
            CodexCategoryKind.Locations.defaultColor,
        )
        assertEquals(10, CodexCategoryKind.entries.map { it.defaultColor }.distinct().size)
    }

    @Test
    fun `three named typography presets are seeded`() {
        assertEquals(3, TypographySettings.NamedPresets.size)
    }
}
