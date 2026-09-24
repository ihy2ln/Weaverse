package com.ihy2ln.weaverse.feature.roleplay.chat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StoryboardGenerationTest {
    @Test
    fun parsesJsonPlanAndClampsUnknownTemplate() {
        val draft = parseStoryboardPageDraft(
            """
            {"title":"Harbor arrival","templateId":"unknown","readingOrder":"RTL","panels":[
              {"description":"Fog over the harbor","caption":"Dawn","dialogue":"","speaker":"","mediaQuery":"harbor"},
              {"description":"A lantern moves","caption":"","dialogue":"Who is there?","speaker":"Mara","mediaQuery":"lantern"}
            ]}
            """.trimIndent(),
        )

        assertNotNull(draft)
        assertEquals("classic-6", draft!!.templateId)
        assertEquals("rtl", draft.readingOrder)
        assertEquals(2, draft.panels.size)
    }

    @Test
    fun malformedOrEmptyModelOutputDoesNotCreateAnEmptyPage() {
        assertNull(parseStoryboardPageDraft("not json"))
        assertNull(parseStoryboardPageDraft("{\"panels\":[]}"))
    }

    @Test
    fun offlineFallbackAlwaysHasEditablePanelsAndReadingDirection() {
        val draft = fallbackStoryboardPageDraft("The party enters the ruined observatory.", rightToLeft = true)

        assertEquals("rtl", draft.readingOrder)
        assertTrue(draft.panels.isNotEmpty())
        assertTrue(draft.panels.all { it.description.isNotBlank() })
        assertTrue(draft.panels.any { it.caption.isNotBlank() || it.dialogue.isNotBlank() })
    }
}
