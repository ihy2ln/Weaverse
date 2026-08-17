package com.ihy2ln.weaverse.core.ui.theme

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ThemeTokensTest {
    @Test
    fun lightTheme_hasNovelcrafterBackground() {
        val tokens = tokensFor(AppThemeMode.Light)
        assertEquals(InkBackground, tokens.background)
    }

    @Test
    fun darkTheme_usesNovelAiNavyAndGold() {
        val tokens = tokensFor(AppThemeMode.Dark)
        assertEquals(InkDarkBackground, tokens.background)
        assertEquals(InkDarkPanel, tokens.panel)
        assertEquals(InkDarkActivePill, tokens.activePill)
    }

    @Test
    fun codexCategoryColors_hasTenEntries() {
        assertEquals(10, CodexCategoryColors.size)
    }
}
