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
                Paragraph("p1", listOf(Span("Before the picture."))),
                MediaBlock(
                    "m1",
                    "media-abc",
                    MediaKind.Image,
                    widthPercent = 80f,
                    caption = listOf(Span("A map of the harbor")),
                ),
                Paragraph("p2", listOf(Span("After the picture."))),
                MediaStackBlock("s1", listOf("media-a", "media-b"), currentIndex = 1),
                MediaGridBlock("g1", listOf("media-c", "media-d"), template = "2-up"),
            ),
        )
        val restored = documentFromJson(original.toJson())
        assertEquals(5, restored.blocks.size)
        assertEquals("Before the picture.", (restored.blocks[0] as Paragraph).plainText())
        val media = restored.blocks[1] as MediaBlock
        assertEquals("media-abc", media.mediaId)
        assertEquals(80f, media.widthPercent)
        assertEquals("A map of the harbor", media.caption.plainText())
        assertEquals("After the picture.", (restored.blocks[2] as Paragraph).plainText())
        val stack = restored.blocks[3] as MediaStackBlock
        assertEquals(listOf("media-a", "media-b"), stack.mediaIds)
        assertEquals(1, stack.currentIndex)
        val grid = restored.blocks[4] as MediaGridBlock
        assertEquals(listOf("media-c", "media-d"), grid.mediaIds)
        assertEquals(listOf("media-abc", "media-a", "media-b", "media-c", "media-d"), restored.referencedMediaIds())
    }

    @Test
    fun insertMediaAfter_keepsSurroundingProse() {
        val blocks = listOf(Paragraph("p1", listOf(Span("Keep this sentence."))))
        val next = blocks.insertMediaAfter(0, MediaBlock("m1", "img-1", MediaKind.Image))
        assertEquals(2, next.size)
        assertEquals("Keep this sentence.", (next[0] as Paragraph).plainText())
        assertEquals("img-1", (next[1] as MediaBlock).mediaId)
    }

    @Test
    fun insertMediaAfter_stripsSlashCommandResidueOnly() {
        val blocks = listOf(Paragraph("p1", listOf(Span("/image"))))
        val next = blocks.insertMediaAfter(0, MediaBlock("m1", "img-1", MediaKind.Image))
        assertEquals("", (next[0] as Paragraph).plainText())
        assertTrue(next[1] is MediaBlock)
    }

    @Test
    fun insertMediaAfter_negativeIndexAppends() {
        val blocks = listOf(Paragraph("p1", listOf(Span("text"))))
        val next = blocks.insertMediaAfter(-1, MediaBlock("m1", "img-1", MediaKind.Image))
        assertEquals(2, next.size)
        assertTrue(next.last() is MediaBlock)
    }

    @Test
    fun mediaPlacement_followsBlockOrderAndSkipsBlankPaths() {
        val blocks = listOf(
            Paragraph("p1", listOf(Span("one"))),
            MediaBlock("m1", "img-a", MediaKind.Image),
            Paragraph("p2", listOf(Span("two"))),
            MediaStackBlock("s1", listOf("img-b", "img-missing")),
        )
        val paths = mapOf("img-a" to "/tmp/a.jpg", "img-b" to "/tmp/b.jpg", "img-missing" to "")
        val placement = blocks.mediaPlacement(paths)
        assertEquals(listOf(1 to listOf("/tmp/a.jpg"), 3 to listOf("/tmp/b.jpg")), placement)
    }

    @Test
    fun documentFromJson_readsFqcnMediaBlockUsedByWritePersistedJson() {
        val json = """
            {"blocks":[
              {"type":"com.ihy2ln.weaverse.core.text.Paragraph","id":"p-1","spans":[{"text":"Before"}],"align":"Start","indentLevel":0},
              {"type":"com.ihy2ln.weaverse.core.text.MediaBlock","id":"m-art","mediaId":"img-1","kind":"Image","widthPercent":100.0,"align":"Center","caption":[{"text":"Harbor"}],"autoplay":false,"loop":false,"muted":true,"gridCol":-1,"gridRow":-1,"gridColSpan":2,"gridRowSpan":2,"collapsed":false},
              {"type":"com.ihy2ln.weaverse.core.text.Paragraph","id":"p-2","spans":[{"text":"After"}],"align":"Start","indentLevel":0}
            ]}
        """.trimIndent()
        val doc = documentFromJson(json)
        assertEquals(3, doc.blocks.size)
        assertEquals("Before", (doc.blocks[0] as Paragraph).plainText())
        assertEquals("img-1", (doc.blocks[1] as MediaBlock).mediaId)
        assertEquals("Harbor", (doc.blocks[1] as MediaBlock).caption.plainText())
        assertEquals("After", (doc.blocks[2] as Paragraph).plainText())
        assertEquals(listOf(1 to listOf("/files/img-1.jpg")), doc.blocks.mediaPlacement(mapOf("img-1" to "/files/img-1.jpg")))
    }
}
