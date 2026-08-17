package com.ihy2ln.weaverse.sync.web

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WebChromeTest {
    @Test
    fun topBarHasImportExportAndKeepsModes() {
        val html = webIndexHtml()
        assertTrue(html.contains(">Import<"))
        assertTrue(html.contains(">Export<"))
        assertTrue(html.contains("data-tab=\"plan\""))
        assertTrue(html.contains("data-tab=\"write\""))
        assertTrue(html.contains("data-tab=\"chat\""))
        assertTrue(html.contains("data-tab=\"review\""))
        assertTrue(html.contains("data-tab=\"roleplay\""))
        assertTrue(html.contains("data-tab=\"notes\""))
        assertTrue(html.contains("data-tab=\"pictures\""))
        assertTrue(html.contains("Codex · shared"))
        assertTrue(html.contains("Notes · shared"))
    }
}
