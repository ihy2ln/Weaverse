package com.ihy2ln.weaverse.core.text

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SceneBeatOpsTest {
    @Test
    fun ensureSceneBeatAtStart_insertsWhenMissing() {
        val blocks = listOf(Paragraph("p1", listOf(Span("Hello"))))
        val (next, index) = blocks.ensureSceneBeatAtStart()
        assertEquals(0, index)
        assertTrue(next[0] is SceneBeatBlock)
        assertEquals("Hello", (next[1] as Paragraph).spans.first().text)
    }

    @Test
    fun ensureSceneBeatAtStart_reusesExisting() {
        val beat = SceneBeatBlock("beat-1", prompt = "Wish start")
        val blocks = listOf(Paragraph("p1", listOf(Span("Prose"))), beat)
        val (next, index) = blocks.ensureSceneBeatAtStart()
        assertEquals(1, index)
        assertEquals(2, next.size)
        assertEquals("Wish start", (next[1] as SceneBeatBlock).prompt)
    }

    @Test
    fun appendParagraphs_putsNewestTextAtTheBottom() {
        val blocks = listOf(
            Paragraph("p1", listOf(Span("Oldest"))),
            Paragraph("p2", listOf(Span("Middle"))),
        )
        val next = blocks.appendParagraphs("Newest first paragraph.\n\nNewest second.")
        assertEquals(2, next.size)
        assertEquals("Oldest", (next[0] as Paragraph).spans.first().text)
        assertEquals(
            "Middle Newest first paragraph.\n\nNewest second.",
            (next[1] as Paragraph).spans.first().text,
        )
        assertTrue(next.none { it is SceneBeatBlock })
    }

    @Test
    fun appendParagraphs_mergesSequentialAddsIntoOneParagraph() {
        val first = listOf(Paragraph("p1", listOf(Span("te")))).appendParagraphs("aaa")
        val next = first.appendParagraphs("adfttgg")
        assertEquals(1, next.size)
        assertEquals("te aaa adfttgg", (next[0] as Paragraph).spans.first().text)
    }

    @Test
    fun appendParagraphs_dropsTrailingEmptyParagraphs() {
        val blocks = listOf(
            Paragraph("p1", listOf(Span("Keep"))),
            Paragraph("empty", listOf(Span(""))),
        )
        val next = blocks.appendParagraphs("Added at bottom")
        assertEquals(1, next.size)
        assertEquals("Keep Added at bottom", (next[0] as Paragraph).spans.first().text)
    }

    @Test
    fun appendParagraphs_keepsMediaThenAppendsText() {
        val media = MediaBlock("m1", mediaId = "media-1", kind = MediaKind.Image)
        val blocks = listOf(Paragraph("p1", listOf(Span("Caption"))), media)
        val next = blocks.appendParagraphs("After the picture")
        assertEquals(3, next.size)
        assertTrue(next[1] is MediaBlock)
        assertEquals("After the picture", (next[2] as Paragraph).spans.first().text)
    }

    @Test
    fun appendSceneBeat_alwaysAddsANewBox() {
        val beat = SceneBeatBlock("beat-1", prompt = "existing")
        val blocks = listOf(beat, Paragraph("p1", listOf(Span("Prose"))))
        val next = blocks.appendSceneBeat()
        assertEquals(3, next.size)
        assertTrue(next[0] is SceneBeatBlock)
        assertEquals("existing", (next[0] as SceneBeatBlock).prompt)
        assertTrue(next[2] is SceneBeatBlock)
        assertEquals("", (next[2] as SceneBeatBlock).prompt)
    }

    @Test
    fun insertGeneratedProseAfter_keepsPromptInBeat_andProseOutside() {
        val beat = SceneBeatBlock("beat-1", prompt = "old")
        val blocks = listOf(beat, Paragraph("p1", listOf(Span("Already here"))))
        val next = blocks.insertGeneratedProseAfter(
            insertAfterIndex = 0,
            generatedText = "The phone glowed.\n\nI sat up.",
            beatPrompt = "Cliche wish fulfillment start",
        )
        assertTrue(next[0] is SceneBeatBlock)
        assertEquals("Cliche wish fulfillment start", (next[0] as SceneBeatBlock).prompt)
        assertEquals(3, next.size)
        assertEquals(
            "The phone glowed.\n\nI sat up.",
            (next[1] as Paragraph).spans.first().text,
        )
        assertEquals("Already here", (next[2] as Paragraph).spans.first().text)
        next.filterIsInstance<Paragraph>().forEach { paragraph ->
            assertFalse(paragraph.spans.any { it.text.contains("wish fulfillment") })
        }
    }

    @Test
    fun toggleCollapsed_flipsFlag() {
        val blocks = listOf(SceneBeatBlock("beat-1", prompt = "x", collapsed = false))
        val next = blocks.withSceneBeatCollapsedToggled(0)
        assertTrue((next[0] as SceneBeatBlock).collapsed)
    }

    @Test
    fun jsonRoundTrip_preservesSceneBeat() {
        val original = Document(
            listOf(
                SceneBeatBlock("beat-1", prompt = "John doe gets a power", collapsed = true),
                Paragraph("p1", listOf(Span("The blue glow of my phone."))),
            ),
        )
        val restored = documentFromJson(original.toJson())
        val beat = restored.blocks[0] as SceneBeatBlock
        assertEquals("John doe gets a power", beat.prompt)
        assertTrue(beat.collapsed)
        assertEquals("The blue glow of my phone.", restored.plainText())
    }

    @Test
    fun findCodexMentionRanges_matchesNamesAndWikiLinks() {
        val text = "John doe finds a broadcast conduit and [[Old Map]]."
        val ranges = findCodexMentionRanges(text, listOf("John doe", "broadcast conduit"))
        val snippets = ranges.map { text.substring(it) }
        assertTrue(snippets.contains("John doe"))
        assertTrue(snippets.contains("broadcast conduit"))
        assertTrue(snippets.any { it.contains("Old Map") })
    }
}
