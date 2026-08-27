package com.ihy2ln.weaverse.feature.novel.write

import com.ihy2ln.weaverse.core.text.MediaBlock
import com.ihy2ln.weaverse.core.text.MediaGridBlock
import com.ihy2ln.weaverse.core.text.MediaKind
import com.ihy2ln.weaverse.core.text.MediaStackBlock
import com.ihy2ln.weaverse.core.text.Paragraph
import com.ihy2ln.weaverse.core.text.Span
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

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
        assertNull(WriteMediaOps.clipboardFromBlock(MediaBlock("m", "", MediaKind.Image)))
    }

    @Test
    fun mediaIdsOf_includesGridAndStack() {
        assertEquals(listOf("img-a"), WriteMediaOps.mediaIdsOf(MediaBlock("m", "img-a", MediaKind.Image)))
        assertEquals(listOf("a", "b"), WriteMediaOps.mediaIdsOf(MediaStackBlock("s", listOf("a", "b"))))
        assertEquals(listOf("c", "d"), WriteMediaOps.mediaIdsOf(MediaGridBlock("g", listOf("c", "d"))))
    }
}
