package com.ihy2ln.weaverse.feature.novel.codex

import com.ihy2ln.weaverse.core.ui.theme.AppThemeMode
import com.ihy2ln.weaverse.core.ui.theme.InkDarkBackground
import com.ihy2ln.weaverse.core.ui.theme.InkDarkPanel
import com.ihy2ln.weaverse.core.ui.theme.InkHover
import com.ihy2ln.weaverse.core.ui.theme.InkOledPanel
import com.ihy2ln.weaverse.core.ui.theme.InkSepiaPanel
import com.ihy2ln.weaverse.core.ui.theme.tokensFor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class CodexListThemeTest {
    @Test
    fun entryListUsesChromePanelNotGreyPageOrHover() {
        AppThemeMode.entries.forEach { mode ->
            val tokens = tokensFor(mode)
            assertEquals(tokens.panel, codexListBackground(tokens), mode.name)
        }
        assertEquals(InkSepiaPanel, codexListBackground(tokensFor(AppThemeMode.Sepia)))
        assertEquals(InkDarkPanel, codexListBackground(tokensFor(AppThemeMode.Dark)))
        assertEquals(InkOledPanel, codexListBackground(tokensFor(AppThemeMode.OledBlack)))
        assertNotEquals(InkDarkBackground, codexListBackground(tokensFor(AppThemeMode.Dark)))
        assertNotEquals(InkHover, codexListBackground(tokensFor(AppThemeMode.Dark)))
        assertNotEquals(InkHover, codexListBackground(tokensFor(AppThemeMode.OledBlack)))
        assertNotEquals(InkHover, codexListBackground(tokensFor(AppThemeMode.Sepia)))
    }
}
