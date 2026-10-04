package com.ihy2ln.weaverse.core.story

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StartTemplateLibraryTest {
    @Test
    fun aTemplateFileRoundTripsAndOtherFilesAreRefused() {
        val template = StartTemplate(id = "abc12345-x", kind = "rpg", name = "Dragon heist", savedAt = 5, payload = "{\"step\":\"Verification\"}")
        assertEquals(template, decodeStartTemplate(encodeStartTemplate(template)))
        assertNull(decodeStartTemplate("{\"hello\":1}"))
        assertNull(decodeStartTemplate(encodeStartTemplate(template.copy(kind = "manga"))))
        assertNull(decodeStartTemplate(encodeStartTemplate(template.copy(payload = ""))))
        assertNull(decodeStartTemplate("not json"))
    }

    @Test
    fun fileNamesAreReadableAndUnique() {
        val name = startTemplateFileName(StartTemplate(id = "abc12345-x", kind = "novel", name = "Isekai Gacha: Vol 2!", payload = "{}"))
        assertEquals("novel-isekai-gacha-vol-2-abc12345.weaverse-template.json", name)
        assertTrue(startTemplateFileName(StartTemplate(id = "zz", kind = "rpg", name = "   ", payload = "{}")).startsWith("rpg-template-"))
    }

    @Test
    fun theSameTemplateFromTwoPlacesAppearsOnceNewestFirst() {
        val old = StartTemplate(id = "a", kind = "novel", name = "Old name", savedAt = 1, payload = "{}")
        val renamed = old.copy(name = "New name", savedAt = 3)
        val other = StartTemplate(id = "b", kind = "novel", name = "Other", savedAt = 2, payload = "{}")
        val merged = mergeStartTemplates(listOf(old, other), listOf(renamed))
        assertEquals(listOf("New name", "Other"), merged.map { it.name })
    }
}
