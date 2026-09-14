package com.ihy2ln.weaverse.feature.roleplay.chat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PanelAiTranslationTest {
    @Test
    fun parsesJapaneseKoreanAndChineseRegionsWithEnglishText() {
        val response = """
            model preface
            [
              {"x":100,"y":50,"w":250,"h":100,"original":"助けて","translation":"Help me!"},
              {"x":400,"y":250,"w":200,"h":80,"original":"괜찮아","translation":"I'm okay."},
              {"x":50,"y":700,"w":300,"h":120,"original":"快走","translation":"Run!"}
            ]
        """.trimIndent()

        val regions = PanelAi.parseTranslatedRegions(response).orEmpty()

        assertEquals(listOf("Help me!", "I'm okay.", "Run!"), regions.map { it.translation })
        assertEquals(listOf("助けて", "괜찮아", "快走"), regions.map { it.original })
        assertTrue(regions.all { it.x in 0f..1f && it.y in 0f..1f && it.w in 0f..1f && it.h in 0f..1f })
    }

    @Test
    fun rejectsNonJsonTranslationResponse() {
        assertNull(PanelAi.parseTranslatedRegions("I could not read the page"))
    }
}
