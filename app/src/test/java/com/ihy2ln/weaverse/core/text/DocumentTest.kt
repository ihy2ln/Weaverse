package com.ihy2ln.weaverse.core.text

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DocumentTest {
    @Test
    fun `plainText flattens blocks with newlines between them`() {
        val document = Document(
            listOf(
                Paragraph("1", listOf(Span("First paragraph."))),
                Paragraph("2", listOf(Span("Second paragraph."))),
            ),
        )
        assertEquals("First paragraph.\nSecond paragraph.", document.toPlainText())
    }

    @Test
    fun `plainText ignores dividers and pulls captions from media blocks`() {
        val document = Document(
            listOf(
                Paragraph("1", listOf(Span("Before."))),
                Divider("2"),
                MediaBlock("3", mediaId = "m1", kind = MediaKind.Image, caption = listOf(Span("A photo."))),
            ),
        )
        assertEquals("Before.\n\nA photo.", document.toPlainText())
    }

    @Test
    fun `wordCount is zero for an empty document`() {
        assertEquals(0, Document().wordCount())
    }

    @Test
    fun `wordCount counts across all blocks`() {
        val document = Document(
            listOf(
                Paragraph("1", listOf(Span("Four little words"))),
                Heading("2", level = 1, spans = listOf(Span("here too"))),
            ),
        )
        assertEquals(5, document.wordCount())
    }

    @Test
    fun `String wordCount trims and collapses whitespace`() {
        assertEquals(3, "  one   two three  ".wordCount())
        assertEquals(0, "   ".wordCount())
    }
}
