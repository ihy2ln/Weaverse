package com.ihy2ln.weaverse.core.text

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Regression coverage for the Revision 02 Priority Zero bug: seeded and
 * freshly-created scenes had `docJson = ""`, and the old `String.toDocument()`
 * fallback resolved that to `Document(emptyList())` — zero blocks, which
 * [com.ihy2ln.weaverse.feature.novel.write.editor.BlockEditor]'s `LazyColumn`
 * renders as literally nothing (no cursor, no visible editor at all). The fix
 * makes blank/malformed input resolve to one empty paragraph instead, matching
 * `EditorState`'s own already-established default.
 */
class DocumentSerializationTest {
    @Test
    fun `blank docJson resolves to one empty paragraph, not zero blocks`() {
        val document = "".toDocument()
        assertEquals(1, document.blocks.size)
        assertTrue(document.blocks.single() is Paragraph)
        assertEquals("", document.toPlainText())
    }

    @Test
    fun `malformed docJson falls back to one empty paragraph rather than throwing`() {
        val document = "{ not valid json at all".toDocument()
        assertEquals(1, document.blocks.size)
        assertTrue(document.blocks.single() is Paragraph)
    }

    @Test
    fun `a real document round-trips through toJson and toDocument with span text intact`() {
        val original = Document(
            listOf(
                Paragraph("p1", listOf(Span("The bus dropped John Zhao at the edge of Adams Haven.", colorHex = "#4A90D9"))),
                Heading("h1", level = 2, spans = listOf(Span("Old Secrets"))),
            ),
        )
        val roundTripped = original.toJson().toDocument()
        assertEquals(original, roundTripped)
        assertEquals(
            "The bus dropped John Zhao at the edge of Adams Haven.",
            (roundTripped.blocks[0] as Paragraph).spans.single().text,
        )
        assertEquals("#4A90D9", (roundTripped.blocks[0] as Paragraph).spans.single().colorHex)
    }

    @Test
    fun `paragraph, media, and paragraph blocks round-trip in order`() {
        val original = Document(
            listOf(
                Paragraph("p1", listOf(Span("Before the image."))),
                MediaBlock("m1", mediaId = "media-1", kind = MediaKind.Image),
                Paragraph("p2", listOf(Span("After the image."))),
            ),
        )
        val roundTripped = original.toJson().toDocument()
        assertEquals(3, roundTripped.blocks.size)
        assertTrue(roundTripped.blocks[0] is Paragraph)
        assertTrue(roundTripped.blocks[1] is MediaBlock)
        assertTrue(roundTripped.blocks[2] is Paragraph)
        assertEquals("Before the image.", (roundTripped.blocks[0] as Paragraph).spans.single().text)
        assertEquals("After the image.", (roundTripped.blocks[2] as Paragraph).spans.single().text)
    }
}
