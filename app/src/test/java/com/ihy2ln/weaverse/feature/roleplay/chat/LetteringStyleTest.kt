package com.ihy2ln.weaverse.feature.roleplay.chat

import com.ihy2ln.weaverse.core.text.TextOverlay
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LetteringStyleTest {
    @Test fun formattingSurvivesSaveAndEditorRoundTrip() {
        val original = TextOverlay(id = "style", text = "Emphasis", bold = false,
            italic = true, fontFamily = "serif")
        val restored = Json.decodeFromString<TextOverlay>(Json.encodeToString(original))
        val edited = restored.toPanelTextRegion().toEditableOverlay()
        assertFalse(edited.bold)
        assertTrue(edited.italic)
        assertEquals("serif", edited.fontFamily)
    }

    @Test fun legacyOverlayKeepsPreviousTypeface() {
        val restored = Json.decodeFromString<TextOverlay>("""{"id":"old","text":"Hello"}""")
        assertTrue(restored.bold)
        assertFalse(restored.italic)
        assertEquals("sans-serif", restored.fontFamily)
    }
}
