package com.ihy2ln.weaverse.core.export

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ExportOutlineFormatsTest {
    private val outline = ExportOutline(
        title = "Adams Haven",
        nodes = listOf(
            ExportNode.Heading(1, "Act One"),
            ExportNode.Heading(2, "Arrival"),
            ExportNode.Heading(3, "Docking"),
            ExportNode.Paragraph("The airlock hissed open."),
            ExportNode.Paragraph("Mara stepped through, boots echoing."),
        ),
    )

    @Test
    fun `markdown round-trip preserves title, headings, and paragraphs`() {
        val decoded = outline.toMarkdown().parseMarkdownOutline(fallbackTitle = "fallback")

        assertEquals(outline.title, decoded.title)
        assertEquals(outline.nodes, decoded.nodes)
    }

    @Test
    fun `html round-trip preserves title, headings, and paragraphs`() {
        val decoded = outline.toHtml().parseHtmlOutline(fallbackTitle = "fallback")

        assertEquals(outline.title, decoded.title)
        assertEquals(outline.nodes, decoded.nodes)
    }

    @Test
    fun `html escapes then unescapes special characters`() {
        val special = ExportOutline(title = "A & B", nodes = listOf(ExportNode.Paragraph("<tag> & \"quotes\"")))
        val html = special.toHtml()

        assertEquals(false, html.contains("<tag>"))

        val decoded = html.parseHtmlOutline(fallbackTitle = "fallback")
        assertEquals(special.title, decoded.title)
        assertEquals(special.nodes, decoded.nodes)
    }

    @Test
    fun `docx round-trip preserves title, headings, and paragraphs`() {
        val bytes = DocxCodec.encode(outline)
        val decoded = DocxCodec.decode(bytes)

        assertEquals(outline.title, decoded.title)
        assertEquals(outline.nodes, decoded.nodes)
    }

    @Test
    fun `docx escapes xml-sensitive characters`() {
        val special = ExportOutline(title = "Q&A", nodes = listOf(ExportNode.Paragraph("<script> & more")))
        val decoded = DocxCodec.decode(DocxCodec.encode(special))

        assertEquals(special.title, decoded.title)
        assertEquals(special.nodes, decoded.nodes)
    }

    @Test
    fun `decoding a docx with no document xml part returns an empty outline instead of throwing`() {
        val decoded = DocxCodec.decode(ByteArray(0))
        assertEquals("Untitled", decoded.title)
        assertEquals(emptyList<ExportNode>(), decoded.nodes)
    }

    @Test
    fun `markdown parser falls back to the caller-supplied title when there is no h1`() {
        val decoded = "Just a paragraph, no heading.".parseMarkdownOutline(fallbackTitle = "Fallback Title")
        assertEquals("Fallback Title", decoded.title)
        assertEquals(listOf(ExportNode.Paragraph("Just a paragraph, no heading.")), decoded.nodes)
    }
}
