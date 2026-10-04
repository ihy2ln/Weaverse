package com.ihy2ln.weaverse.core.media

import com.ihy2ln.weaverse.core.text.MediaBlock
import com.ihy2ln.weaverse.core.text.Paragraph
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GeneratedTextIllustratorTest {
    private val kaela = IllustrationSubject("codex:kaela", listOf("Kaela Stormfang", "Kaela"), "m-kaela", "Kaela Stormfang", priority = 2)
    private val shack = IllustrationSubject("building:shack", listOf("shack", "hut", "cabin"), "m-shack", "The Shack")
    private val barn = IllustrationSubject("building:barn", listOf("barn"), "m-barn", "Barn")

    @Test
    fun picturesGoAfterTheParagraphThatNamesThem() {
        val text = "JD raised his hand.\n\nLight folded into a crooked shack at the forest edge.\n\nKaela whistled at it."
        val paragraphs = splitIntoParagraphs(text)
        val picks = pickIllustrations(paragraphs, "summon a shack", listOf(kaela, shack, barn))
        assertEquals(listOf("m-shack" to 1, "m-kaela" to 2), picks.map { it.subject.mediaId to it.afterParagraph })
        val blocks = illustratedBlocks(text, picks)
        assertEquals(listOf("P", "P", "M", "P", "M"), blocks.map { if (it is Paragraph) "P" else if (it is MediaBlock) "M" else "?" })
    }

    @Test
    fun aThingOnlyThePromptNamedGoesAbove() {
        val picks = pickIllustrations(splitIntoParagraphs("The walls rose around them."), "JD summons a shack", listOf(shack))
        assertEquals(-1, picks.single().afterParagraph)
        assertTrue(illustratedBlocks("The walls rose around them.", picks).first() is MediaBlock)
    }

    @Test
    fun wordsMatchWholeWordsAndPlurals() {
        assertTrue(mentions("Three huts stood empty.", "hut"))
        assertTrue(mentions("Kaela's gauntlets", "Kaela"))
        assertFalse(mentions("The shutters rattled.", "hut"))
        assertFalse(mentions("Well, that went badly.", "stone well"))
    }

    @Test
    fun grandWordsPickTheTopTier() {
        val tiers = listOf("F", "B", "SSR")
        assertEquals("SSR", buildingTierFor("a grand shack of crystal", tiers))
        assertEquals("F", buildingTierFor("a ramshackle little shack", tiers))
        assertEquals("B", buildingTierFor("a shack", tiers))
    }

    @Test
    fun atMostThreePictures() {
        val many = (1..6).map { IllustrationSubject("s$it", listOf("thing$it"), "m$it", "T$it") }
        val text = (1..6).joinToString("\n\n") { "A thing$it appears." }
        assertEquals(3, pickIllustrations(splitIntoParagraphs(text), "", many).size)
    }

    @Test
    fun picturesAtTheSameSpotShareARow() {
        val text = "Kaela and JD stood by the shack.\n\nThey went in."
        val picks = pickIllustrations(splitIntoParagraphs(text), "", listOf(kaela, shack))
        val blocks = illustratedBlocks(text, picks)
        val row = blocks[1] as com.ihy2ln.weaverse.core.text.MediaGridBlock
        assertEquals(listOf("m-kaela", "m-shack"), row.mediaIds)
        assertTrue(blocks[2] is Paragraph)
    }
}
