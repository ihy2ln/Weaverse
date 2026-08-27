package com.ihy2ln.weaverse.core.text

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DocumentModelTest {
    @Test
    fun plainText_extractsFromParagraphs() {
        val doc = Document(
            listOf(
                Paragraph("1", listOf(Span("Hello"))),
                Paragraph("2", listOf(Span(" world"))),
            ),
        )
        assertTrue(doc.plainText().contains("Hello"))
        assertEquals(2, doc.wordCount())
    }

    @Test
    fun jsonRoundTrip_preservesSpanText() {
        val original = Document(listOf(Paragraph("p1", listOf(Span("Persist me.")))))
        val restored = documentFromJson(original.toJson())
        assertEquals("Persist me.", restored.plainText())
    }

    @Test
    fun jsonRoundTrip_preservesMediaPlacementAmongProse() {
        val original = Document(
            listOf(
                Paragraph("p1", listOf(Span("Before the picture"))),
                MediaBlock("m1", "img-a", MediaKind.Image, widthPercent = 80f, align = Align.Start),
                Paragraph("p2", listOf(Span("After the picture"))),
                MediaStackBlock("s1", listOf("img-b", "img-c"), currentIndex = 1),
            ),
        )
        val restored = documentFromJson(original.toJson())
        assertEquals(listOf("p1", "m1", "p2", "s1"), restored.blocks.map { it.id })
        val media = restored.blocks[1] as MediaBlock
        assertEquals("img-a", media.mediaId)
        assertEquals(80f, media.widthPercent)
        assertEquals(Align.Start, media.align)
        val stack = restored.blocks[3] as MediaStackBlock
        assertEquals(listOf("img-b", "img-c"), stack.mediaIds)
        assertEquals(1, stack.currentIndex)
        assertEquals(
            listOf("img-a", "img-b", "img-c"),
            restored.referencedMediaIds(),
        )
        assertEquals(
            listOf(1, 3),
            restored.mediaPlacement().map { it.blockIndex },
        )
    }

    @Test
    fun insertMediaAfter_keepsSurroundingProse() {
        val blocks = listOf(
            Paragraph("p1", listOf(Span("Keep me"))),
            Paragraph("p2", listOf(Span("Also keep"))),
        )
        val next = blocks.insertMediaAfter(0, MediaBlock("m1", "img-a", MediaKind.Image))
        assertEquals("Keep me", (next[0] as Paragraph).spans.first().text)
        assertEquals("m1", next[1].id)
        assertEquals("Also keep", (next[2] as Paragraph).spans.first().text)
    }

    @Test
    fun insertMediaAfter_stripsSlashCommandResidueOnly() {
        val blocks = listOf(Paragraph("p1", listOf(Span("/image"))))
        val next = blocks.insertMediaAfter(0, MediaBlock("m1", "img-a", MediaKind.Image))
        assertEquals("", (next[0] as Paragraph).plainText())
        assertEquals("m1", next[1].id)
    }

    @Test
    fun insertMediaAfter_emptyList() {
        val next = emptyList<Block>().insertMediaAfter(-1, MediaBlock("m1", "img-a", MediaKind.Image))
        assertEquals(listOf("m1"), next.map { it.id })
    }

    @Test
    fun speakableParagraphs_skipMediaBlocks() {
        val doc = Document(
            listOf(
                Paragraph("p1", listOf(Span("One"))),
                MediaBlock("m1", "img-a", MediaKind.Image),
                Paragraph("p2", listOf(Span("Two"))),
            ),
        )
        assertEquals(listOf("One", "Two"), doc.speakableParagraphs())
    }
}
