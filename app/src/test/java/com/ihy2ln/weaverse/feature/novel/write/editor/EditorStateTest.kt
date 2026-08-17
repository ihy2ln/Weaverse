package com.ihy2ln.weaverse.feature.novel.write.editor

import com.ihy2ln.weaverse.core.text.Document
import com.ihy2ln.weaverse.core.text.Mark
import com.ihy2ln.weaverse.core.text.Paragraph
import com.ihy2ln.weaverse.core.text.Span
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EditorStateTest {
    private fun paragraph(id: String, text: String) = Paragraph(id, listOf(Span(text)))

    @Test
    fun `splitParagraph divides text at the cursor into two blocks`() {
        val state = EditorState(Document(listOf(paragraph("a", "Hello world"))))
        state.splitParagraph("a", cursorOffset = 5)

        assertEquals(2, state.blocks.size)
        assertEquals("Hello", (state.blocks[0] as Paragraph).spans.single().text)
        assertEquals(" world", (state.blocks[1] as Paragraph).spans.single().text)
    }

    @Test
    fun `mergeWithPrevious combines text and removes the current block`() {
        val state = EditorState(Document(listOf(paragraph("a", "Hello"), paragraph("b", " world"))))
        val caretOffset = state.mergeWithPrevious("b")

        assertEquals(5, caretOffset)
        assertEquals(1, state.blocks.size)
        assertEquals("Hello world", (state.blocks[0] as Paragraph).spans.single().text)
    }

    @Test
    fun `mergeWithPrevious on the first block does nothing`() {
        val state = EditorState(Document(listOf(paragraph("a", "Only block"))))
        val result = state.mergeWithPrevious("a")

        assertNull(result)
        assertEquals(1, state.blocks.size)
    }

    @Test
    fun `undo reverts a split and redo reapplies it`() {
        val state = EditorState(Document(listOf(paragraph("a", "Hello world"))))
        state.splitParagraph("a", cursorOffset = 5)
        assertEquals(2, state.blocks.size)

        state.undo()
        assertEquals(1, state.blocks.size)
        assertEquals("Hello world", (state.blocks[0] as Paragraph).spans.single().text)

        state.redo()
        assertEquals(2, state.blocks.size)
        assertEquals("Hello", (state.blocks[0] as Paragraph).spans.single().text)
    }

    @Test
    fun `a new edit after undo clears the redo stack`() {
        val state = EditorState(Document(listOf(paragraph("a", "one"), paragraph("b", "two"))))
        state.splitParagraph("a", cursorOffset = 1)
        state.undo()
        assertTrue(state.canRedo)

        state.insertBlockAfter("b", paragraph("c", "three"))
        assertFalse(state.canRedo)
    }

    @Test
    fun `moveBlock down swaps with the next block`() {
        val state = EditorState(Document(listOf(paragraph("a", "one"), paragraph("b", "two"), paragraph("c", "three"))))
        state.moveBlock("a", delta = 1)
        assertEquals(listOf("b", "a", "c"), state.blocks.map { it.id })
    }

    @Test
    fun `moveBlock up swaps with the previous block`() {
        val state = EditorState(Document(listOf(paragraph("a", "one"), paragraph("b", "two"), paragraph("c", "three"))))
        state.moveBlock("c", delta = -1)
        assertEquals(listOf("a", "c", "b"), state.blocks.map { it.id })
    }

    @Test
    fun `moveBlock past either edge is a no-op`() {
        val state = EditorState(Document(listOf(paragraph("a", "one"), paragraph("b", "two"))))
        state.moveBlock("a", delta = -1)
        assertEquals(listOf("a", "b"), state.blocks.map { it.id })
        state.moveBlock("b", delta = 1)
        assertEquals(listOf("a", "b"), state.blocks.map { it.id })
    }

    @Test
    fun `mergeWithPrevious preserves marks from both sides`() {
        val state = EditorState(
            Document(
                listOf(
                    Paragraph("a", listOf(Span("Hello ", marks = setOf(Mark.Bold)))),
                    Paragraph("b", listOf(Span("world"))),
                ),
            ),
        )
        state.mergeWithPrevious("b")
        val merged = (state.blocks.single() as Paragraph).spans
        assertEquals(2, merged.size)
        assertEquals(setOf(Mark.Bold), merged[0].marks)
        assertEquals("Hello world", merged.joinToString("") { it.text })
    }

    @Test
    fun `undo redo stack survives at least 100 steps`() {
        val state = EditorState(Document(listOf(paragraph("a", "x"))))
        repeat(150) { state.insertBlockAfter("a", paragraph("gen-$it", "y")) }
        assertEquals(151, state.blocks.size)

        var undone = 0
        while (state.canUndo) {
            state.undo()
            undone++
        }
        assertEquals(100, undone)
    }
}
