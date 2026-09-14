package com.ihy2ln.weaverse.feature.novel.write

import com.ihy2ln.weaverse.core.text.Paragraph
import com.ihy2ln.weaverse.core.text.Span
import com.ihy2ln.weaverse.core.text.plainText
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WriteGenerationAcceptTest {
    private val generation = WriteGeneration(mockk(relaxed = true), mockk(relaxed = true))

    @Test
    fun acceptIntoBlocks_replacesSelectedRange() {
        val blocks = listOf(Paragraph("p0", listOf(Span("hello world"))))
        val overlay = AiOverlayState(
            commandId = "replace",
            replaceBlockIndex = 0,
            replaceStart = 6,
            replaceEnd = 11,
        )
        val next = generation.acceptIntoBlocks(blocks, overlay, "there")
        assertEquals("hello there", (next[0] as Paragraph).spans.plainText())
        assertEquals(1, next.size)
    }

    @Test
    fun acceptIntoBlocks_appendsGeneratedProse() {
        val blocks = listOf(Paragraph("p0", listOf(Span("start"))))
        val overlay = AiOverlayState(commandId = "continue", insertAfterIndex = 0)
        val next = generation.acceptIntoBlocks(blocks, overlay, "more")
        assertEquals(2, next.size)
        assertEquals("more", (next[1] as Paragraph).spans.plainText())
    }

    @Test
    fun mimeForExtension_mapsCommonTypes() {
        assertEquals("image/png", WriteGeneration.mimeForExtension("png"))
        assertEquals("image/jpeg", WriteGeneration.mimeForExtension("jpg"))
    }
}
