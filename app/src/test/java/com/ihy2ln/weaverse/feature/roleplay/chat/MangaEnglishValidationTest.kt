package com.ihy2ln.weaverse.feature.roleplay.chat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Script validation runs before any pixel is cleaned, so these cases are the gate that keeps
 * a failed translation from turning a page into empty bubbles.
 */
class MangaEnglishValidationTest {

    private fun region(original: String, translation: String, language: String = "ja") =
        PanelTextRegion(
            x = 0.1f,
            y = 0.1f,
            w = 0.2f,
            h = 0.2f,
            original = original,
            translation = translation,
            sourceLanguage = language,
        )

    @Test
    fun everyNonLatinScriptIsRejectedAsAnEnglishReplacement() {
        val samples = mapOf(
            "japanese" to "助けて",
            "chinese" to "快走",
            "korean" to "괜찮아",
            "cyrillic" to "Привет",
            "arabic" to "مرحبا",
            "greek" to "Γειά",
        )

        samples.forEach { (label, text) ->
            assertTrue(PanelAi.containsForeignScript(text), "$label should be rejected")
        }
    }

    @Test
    fun englishWithPunctuationAndDigitsIsAccepted() {
        assertFalse(PanelAi.containsForeignScript("Run! 3 of them — hurry?"))
    }

    @Test
    fun aTranslationLeftInTheSourceScriptIsFlagged() {
        val bad = region(original = "助けて", translation = "助けて")

        assertEquals(listOf(0), PanelAi.invalidEnglishRegionIndexes(listOf(bad)))
    }

    @Test
    fun aBlankTranslationOverSourceTextIsFlagged() {
        val bad = region(original = "助けて", translation = "   ")

        assertEquals(listOf(0), PanelAi.invalidEnglishRegionIndexes(listOf(bad)))
    }

    @Test
    fun mixedScriptLeakageInsideAnOtherwiseEnglishLineIsFlagged() {
        val bad = region(original = "助けて", translation = "Help me 助けて!")

        assertEquals(listOf(0), PanelAi.invalidEnglishRegionIndexes(listOf(bad)))
    }

    @Test
    fun cleanEnglishPassesValidation() {
        val good = region(original = "助けて", translation = "Help me!")

        assertTrue(PanelAi.invalidEnglishRegionIndexes(listOf(good)).isEmpty())
    }

    @Test
    fun modelPrefacesAreStrippedBeforeLettering() {
        assertEquals("Help me!", PanelAi.normalizeEnglishText("Translation: Help me!"))
        assertEquals("Run!", PanelAi.normalizeEnglishText("1. \"Run!\""))
    }

    @Test
    fun verticalCaptionBoxesKeepTightCleanupBoundsMatchingTheSourceGlyphs() {
        // A tall, narrow white-on-black caption column — the failure this pass exists for.
        val regions = PanelAi.parseTranslatedRegions(
            """[{"x":820,"y":60,"w":70,"h":420,"original":"必ず戻る","translation":"I will come back.","language":"ja"}]""",
        ).orEmpty()

        val caption = regions.single()
        assertEquals(caption.x, caption.cleanupX, 0.0001f)
        assertEquals(caption.y, caption.cleanupY, 0.0001f)
        assertEquals(caption.w, caption.cleanupW, 0.0001f)
        assertEquals(caption.h, caption.cleanupH, 0.0001f)
        assertEquals("ja", caption.sourceLanguage)
        // The box stays the narrow column the glyphs occupy; it is not widened to fit English.
        assertTrue(caption.w < caption.h, "vertical source box should stay taller than it is wide")
    }

    @Test
    fun invisibleRegionsAreNeverValidatedOrCleaned() {
        val hidden = region(original = "助けて", translation = "").copy(visible = false)

        assertTrue(PanelAi.invalidEnglishRegionIndexes(listOf(hidden)).isEmpty())
    }
}
