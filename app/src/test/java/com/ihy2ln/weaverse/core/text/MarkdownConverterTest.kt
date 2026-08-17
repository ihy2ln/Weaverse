package com.ihy2ln.weaverse.core.text

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MarkdownConverterTest {
    @Test
    fun `paragraph round trips through markdown`() {
        val document = Document(listOf(Paragraph("1", listOf(Span("Plain sentence.")))))
        val markdown = MarkdownConverter.toMarkdown(document)
        assertEquals("Plain sentence.", markdown)

        val parsed = MarkdownConverter.fromMarkdown(markdown)
        assertEquals(1, parsed.blocks.size)
        assertEquals("Plain sentence.", parsed.toPlainText())
    }

    @Test
    fun `bold and italic marks render as expected markdown syntax`() {
        val document = Document(
            listOf(
                Paragraph(
                    "1",
                    listOf(
                        Span("normal "),
                        Span("bold", marks = setOf(Mark.Bold)),
                        Span(" and "),
                        Span("italic", marks = setOf(Mark.Italic)),
                    ),
                ),
            ),
        )
        val markdown = MarkdownConverter.toMarkdown(document)
        assertEquals("normal **bold** and *italic*", markdown)
    }

    @Test
    fun `parseSpans recovers bold and italic marks`() {
        val spans = MarkdownConverter.parseSpans("normal **bold** and *italic*")
        assertEquals(4, spans.size)
        assertEquals("normal ", spans[0].text)
        assertEquals(setOf<Mark>(), spans[0].marks)
        assertEquals("bold", spans[1].text)
        assertEquals(setOf(Mark.Bold), spans[1].marks)
        assertEquals(" and ", spans[2].text)
        assertEquals("italic", spans[3].text)
        assertEquals(setOf(Mark.Italic), spans[3].marks)
    }

    @Test
    fun `scene break divider round trips as three asterisks`() {
        val document = Document(listOf(Divider("1", DividerStyle.SceneBreak)))
        val markdown = MarkdownConverter.toMarkdown(document)
        assertEquals("***", markdown)

        val parsed = MarkdownConverter.fromMarkdown(markdown)
        assertEquals(1, parsed.blocks.size)
        assertTrue(parsed.blocks[0] is Divider)
        assertEquals(DividerStyle.SceneBreak, (parsed.blocks[0] as Divider).style)
    }

    @Test
    fun `media block round trips with the media colon slash slash scheme`() {
        val document = Document(
            listOf(MediaBlock("1", mediaId = "abc-123", kind = MediaKind.Image, caption = listOf(Span("A caption")))),
        )
        val markdown = MarkdownConverter.toMarkdown(document)
        assertEquals("![A caption](media://abc-123)", markdown)

        val parsed = MarkdownConverter.fromMarkdown(markdown)
        val media = parsed.blocks.single() as MediaBlock
        assertEquals("abc-123", media.mediaId)
        assertEquals("A caption", media.caption.single().text)
    }

    @Test
    fun `heading level round trips`() {
        val document = Document(listOf(Heading("1", level = 2, spans = listOf(Span("Section")))))
        val markdown = MarkdownConverter.toMarkdown(document)
        assertEquals("## Section", markdown)

        val parsed = MarkdownConverter.fromMarkdown(markdown)
        val heading = parsed.blocks.single() as Heading
        assertEquals(2, heading.level)
        assertEquals("Section", heading.toPlainTextForTest())
    }

    @Test
    fun `unordered list lines each become their own list item block`() {
        val markdown = "- First\n- Second\n- Third"
        val parsed = MarkdownConverter.fromMarkdown(markdown)
        assertEquals(3, parsed.blocks.size)
        assertTrue(parsed.blocks.all { it is ListItem })
        assertEquals(listOf("First", "Second", "Third"), parsed.blocks.map { (it as ListItem).spans.single().text })
    }

    @Test
    fun `code block preserves language and body`() {
        val document = Document(listOf(CodeBlock("1", text = "val x = 1", language = "kotlin")))
        val markdown = MarkdownConverter.toMarkdown(document)
        assertEquals("```kotlin\nval x = 1\n```", markdown)

        val parsed = MarkdownConverter.fromMarkdown(markdown)
        val code = parsed.blocks.single() as CodeBlock
        assertEquals("kotlin", code.language)
        assertEquals("val x = 1", code.text)
    }

    @Test
    fun `scene beat blocks are excluded from export by default`() {
        val document = Document(
            listOf(
                Paragraph("1", spans = listOf(Span("Before."))),
                SceneBeatBlock("2", prompt = "Show the storm arriving."),
                Paragraph("3", spans = listOf(Span("After."))),
            ),
        )
        val markdown = MarkdownConverter.toMarkdown(document)
        assertEquals("Before.\n\nAfter.", markdown)
    }

    @Test
    fun `scene beat marker still parses back in on import`() {
        val parsed = MarkdownConverter.fromMarkdown("> **Scene Beat:** Show the storm arriving.")
        val beat = parsed.blocks.single() as SceneBeatBlock
        assertEquals("Show the storm arriving.", beat.prompt)
    }

    @Test
    fun `media stack round trips losslessly through an HTML comment`() {
        val stack = MediaStack(
            id = "s1",
            items = listOf(MediaItemRef("m1", MediaKind.Image), MediaItemRef("m2", MediaKind.Video, autoplay = true)),
            coverIndex = 1,
            widthPercent = 80f,
        )
        val markdown = MarkdownConverter.toMarkdown(Document(listOf(stack)))
        assertTrue(markdown.startsWith("<!--weaverse:mediastack:"))
        assertTrue(markdown.endsWith("-->"))

        val parsed = MarkdownConverter.fromMarkdown(markdown).blocks.single() as MediaStack
        assertEquals(stack, parsed)
    }

    @Test
    fun `media grid round trips losslessly through an HTML comment`() {
        val grid = MediaGrid(
            id = "g1",
            template = MediaGridTemplate.MangaPage,
            items = listOf(MediaItemRef("m1", MediaKind.Image), MediaItemRef("m2", MediaKind.Image)),
            gutterDp = 12,
            cornerRadiusDp = 8,
            backgroundColorHex = "#112233",
            aspectLocked = false,
        )
        val markdown = MarkdownConverter.toMarkdown(Document(listOf(grid)))
        assertTrue(markdown.startsWith("<!--weaverse:mediagrid:"))

        val parsed = MarkdownConverter.fromMarkdown(markdown).blocks.single() as MediaGrid
        assertEquals(grid, parsed)
    }

    @Test
    fun `malformed media stack comment is skipped rather than crashing`() {
        val parsed = MarkdownConverter.fromMarkdown("<!--weaverse:mediastack:not valid json-->")
        assertTrue(parsed.blocks.isEmpty())
    }
}

private fun Heading.toPlainTextForTest(): String = spans.joinToString("") { it.text }
