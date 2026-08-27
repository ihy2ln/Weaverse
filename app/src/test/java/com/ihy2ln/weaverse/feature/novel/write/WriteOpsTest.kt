package com.ihy2ln.weaverse.feature.novel.write

import com.ihy2ln.weaverse.core.text.MediaBlock
import com.ihy2ln.weaverse.core.text.MediaKind
import com.ihy2ln.weaverse.core.text.MediaStackBlock
import com.ihy2ln.weaverse.core.text.Paragraph
import com.ihy2ln.weaverse.core.text.Span
import com.ihy2ln.weaverse.core.text.plainText
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WriteDocumentOpsTest {
    private val ops = WriteDocumentOps(mockk(relaxed = true), mockk(relaxed = true))

    private fun paras(vararg texts: String) =
        texts.mapIndexed { i, text -> Paragraph("p$i", listOf(Span(text))) }

    @Test
    fun recomputeFind_tracksMatchIndex() {
        val state = FindReplaceState(query = "cat", matchIndex = 9)
        val next = ops.recomputeFind(paras("cat sat", "the cat"), state)
        assertEquals(2, next.matches.size)
        assertEquals(1, next.matchIndex)
    }

    @Test
    fun replaceCurrent_rewritesOnlyTheActiveHit() {
        val blocks = paras("cat sat on the cat")
        val state = ops.recomputeFind(blocks, FindReplaceState(query = "cat", replacement = "dog"))
        val next = ops.replaceCurrent(blocks, state)!!
        assertEquals("dog sat on the cat", (next[0] as Paragraph).spans.plainText())
    }

    @Test
    fun replaceAll_countsEveryMatch() {
        val blocks = paras("alpha beta alpha")
        val (next, count) = ops.replaceAll(blocks, FindReplaceState(query = "alpha", replacement = "omega"))
        assertEquals(2, count)
        assertEquals("omega beta omega", (next[0] as Paragraph).spans.plainText())
    }

    @Test
    fun stepFind_wrapsAround() {
        val state = FindReplaceState(
            matches = listOf(
                com.ihy2ln.weaverse.core.text.FindHit(0, 0, 3),
                com.ihy2ln.weaverse.core.text.FindHit(1, 0, 3),
            ),
            matchIndex = 1,
        )
        assertEquals(0, ops.stepFind(state, 1).matchIndex)
        assertEquals(0, ops.stepFind(state.copy(matchIndex = 0), -1).matchIndex)
    }
}

class WriteMediaOpsTest {
    @Test
    fun clipboardRoundTrip_singleMedia() {
        val block = MediaBlock("b1", "img-a", MediaKind.Image, widthPercent = 80f)
        val payload = WriteMediaOps.clipboardFromBlock(block)!!
        assertEquals("img-a", payload.mediaId)
        val restored = WriteMediaOps.blockFromPayload(payload) as MediaBlock
        assertEquals("img-a", restored.mediaId)
        assertEquals(80f, restored.widthPercent)
    }

    @Test
    fun clipboardRoundTrip_stack() {
        val stack = MediaStackBlock("s1", listOf("a", "b"))
        val payload = WriteMediaOps.clipboardFromBlock(stack)!!
        assertEquals(listOf("a", "b"), payload.stackedMediaIds)
        val restored = WriteMediaOps.blockFromPayload(payload) as MediaStackBlock
        assertEquals(listOf("a", "b"), restored.mediaIds)
    }

    @Test
    fun adjustWidth_clampsMediaAndStackSpan() {
        val media = WriteMediaOps.adjustWidth(
            MediaBlock("m", "id", MediaKind.Image, widthPercent = 100f),
            15f,
        ) as MediaBlock
        assertEquals(100f, media.widthPercent)
        val stack = WriteMediaOps.adjustWidth(
            MediaStackBlock("s", listOf("a"), gridColSpan = 1),
            -15f,
        ) as MediaStackBlock
        assertEquals(1, stack.gridColSpan)
    }

    @Test
    fun dragRelease_stacksWhenCrossingAnotherMedia() {
        val blocks = listOf(
            MediaBlock("m1", "a", MediaKind.Image),
            Paragraph("p", listOf(Span("x"))),
            MediaBlock("m2", "b", MediaKind.Image),
        )
        val action = WriteMediaOps.dragRelease(blocks, 0, 500f)
        assertEquals(WriteMediaDragAction.StackOnto(0, 2), action)
    }

    @Test
    fun dragRelease_movesWhenShortNudge() {
        val blocks = listOf(
            MediaBlock("m1", "a", MediaKind.Image),
            Paragraph("p", listOf(Span("x"))),
        )
        assertEquals(WriteMediaDragAction.Move(0, 1), WriteMediaOps.dragRelease(blocks, 0, 60f))
        assertEquals(WriteMediaDragAction.None, WriteMediaOps.dragRelease(blocks, 0, 10f))
    }

    @Test
    fun emptyClipboardPayload_isRejected() {
        assertNull(WriteMediaOps.clipboardFromBlock(Paragraph("p", listOf(Span("hi")))))
        assertTrue(WriteMediaOps.clipboardFromBlock(MediaBlock("m", "", MediaKind.Image)) == null)
    }
}

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
