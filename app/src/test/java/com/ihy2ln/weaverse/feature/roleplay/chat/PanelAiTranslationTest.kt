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
        assertEquals(listOf("t0", "t1", "t2"), regions.map { it.id })
        assertTrue(regions.all { it.x in 0f..1f && it.y in 0f..1f && it.w in 0f..1f && it.h in 0f..1f })
    }

    @Test
    fun rejectsNonJsonTranslationResponse() {
        assertNull(PanelAi.parseTranslatedRegions("I could not read the page"))
    }

    @Test
    fun parseDetectedRegionsKeepsEmptyTextBoxes() {
        val regions = PanelAi.parseDetectedRegions(
            """[{"x":10,"y":20,"w":100,"h":80},{"x":200,"y":40,"w":50,"h":60}]""",
        ).orEmpty()

        assertEquals(2, regions.size)
        assertTrue(regions.all { it.original.isBlank() && it.translation.isBlank() })
        assertEquals(0.01f, regions[0].x, 0.0001f)
    }

    @Test
    fun normalizesModelWrappersBeforeLettering() {
        assertEquals("HELP ME!", PanelAi.normalizeEnglishText("  Translation: `HELP ME!`  "))
        assertEquals("Run!", PanelAi.normalizeEnglishText("2. Run!"))
    }

    @Test
    fun rejectsSourceScriptAndCopiedTextBeforeCleanup() {
        val regions = listOf(
            PanelTextRegion(.1f, .1f, .3f, .2f, "助けて", "助けて"),
            PanelTextRegion(.5f, .1f, .3f, .2f, "こんにちは", "Hello!"),
            PanelTextRegion(.1f, .5f, .3f, .2f, "", "日本語"),
        )

        assertEquals(listOf(0, 2), PanelAi.invalidEnglishRegionIndexes(regions))
        assertTrue(PanelAi.containsForeignScript("助けて"))
        assertTrue(!PanelAi.containsForeignScript("HELLO!"))
    }

    @Test
    fun clampsTranslatedRegionInsidePage() {
        val region = PanelAi.parseTranslatedRegions(
            """[{"x":980,"y":980,"w":200,"h":200,"original":"Hi","translation":"Hello"}]""",
        )!!.single()

        assertTrue(region.x + region.w <= 1.001f)
        assertTrue(region.y + region.h <= 1.001f)
    }
}
