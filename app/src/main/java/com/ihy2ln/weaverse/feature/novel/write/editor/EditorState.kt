package com.ihy2ln.weaverse.feature.novel.write.editor

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ihy2ln.weaverse.core.text.Block
import com.ihy2ln.weaverse.core.text.Document
import com.ihy2ln.weaverse.core.text.Paragraph
import com.ihy2ln.weaverse.core.text.mergeAdjacentSpans
import com.ihy2ln.weaverse.core.text.splitSpansAt
import com.ihy2ln.weaverse.core.util.newId

/**
 * Owns the block list + undo/redo history for one open document (spec §6).
 * A new [EditorState] per document (Write screen creates one per scene) —
 * this class has no idea what scene/entity it belongs to, that's the
 * caller's job (load a [Document] in, read [document] back out to save).
 */
@Stable
class EditorState(initialDocument: Document = Document(listOf(Paragraph(newId())))) {
    var blocks: List<Block> by mutableStateOf(initialDocument.blocks)
        private set

    private val history = UndoRedoStack<List<Block>>(maxSize = 100)

    val canUndo: Boolean get() = history.canUndo
    val canRedo: Boolean get() = history.canRedo

    val document: Document get() = Document(blocks)

    private fun commit(newBlocks: List<Block>) {
        history.push(blocks)
        blocks = newBlocks
    }

    fun undo() {
        history.undo(blocks)?.let { blocks = it }
    }

    fun redo() {
        history.redo(blocks)?.let { blocks = it }
    }

    fun replaceBlock(blockId: String, updated: Block) {
        val index = blocks.indexOfFirst { it.id == blockId }
        if (index == -1) return
        commit(blocks.toMutableList().also { it[index] = updated })
    }

    fun insertBlockAfter(afterBlockId: String, block: Block) {
        val index = blocks.indexOfFirst { it.id == afterBlockId }
        val insertAt = if (index == -1) blocks.size else index + 1
        commit(blocks.toMutableList().also { it.add(insertAt, block) })
    }

    fun removeBlock(blockId: String) {
        commit(blocks.filterNot { it.id == blockId })
    }

    /**
     * Enter inside a text block: splits it into two at [cursorOffset] (spec
     * §6: "Enter at end of a text block creates a new block"). Only
     * [Paragraph] is splittable for now — other text block types keep their
     * mark/structure semantics distinct enough (list depth, heading level)
     * that splitting them raises questions (does the new block inherit the
     * heading level? the list depth?) the spec doesn't answer, so scoped to
     * the common case (spec's own example is a plain paragraph). Returns the
     * new (second) block's id, so the caller can move focus to it.
     */
    fun splitParagraph(blockId: String, cursorOffset: Int): String? {
        val index = blocks.indexOfFirst { it.id == blockId }
        val block = blocks.getOrNull(index) as? Paragraph ?: return null
        val fullText = block.spans.joinToString(separator = "") { it.text }
        val safeOffset = cursorOffset.coerceIn(0, fullText.length)
        val (beforeSpans, afterSpans) = splitSpansAt(block.spans, safeOffset)

        val newBlockId = newId()
        val newBlocks = blocks.toMutableList()
        newBlocks[index] = block.copy(spans = beforeSpans)
        newBlocks.add(index + 1, Paragraph(id = newBlockId, spans = afterSpans))
        commit(newBlocks)
        return newBlockId
    }

    /**
     * Backspace at offset 0: merges this block into the previous one (spec
     * §6: "Backspace at offset 0 merges into the previous block"). Returns
     * the caret offset the merged block's cursor should land at (the
     * previous block's original text length), or null if there was nothing
     * to merge into.
     */
    fun mergeWithPrevious(blockId: String): Int? {
        val index = blocks.indexOfFirst { it.id == blockId }
        if (index <= 0) return null
        val current = blocks[index] as? Paragraph ?: return null
        val previous = blocks[index - 1] as? Paragraph ?: return null

        val previousLength = previous.spans.sumOf { it.text.length }
        val newBlocks = blocks.toMutableList()
        newBlocks[index - 1] = previous.copy(spans = mergeAdjacentSpans(previous.spans + current.spans))
        newBlocks.removeAt(index)
        commit(newBlocks)
        return previousLength
    }

    /**
     * Swaps a block with its immediate neighbor (spec §7's Move, simplified — see BUILD_NOTES
     * "rev02-08" for why this ships as Up/Down gutter buttons rather than a continuous drag
     * gesture with live elevation/parting: that's a well-known hard-to-get-right-blind Compose
     * pattern with no device in this sandbox to verify it on, the same judgment call
     * [BlockEditor]'s own KDoc already makes for focus-following). [delta] is +1 (down) or -1 (up).
     */
    fun moveBlock(blockId: String, delta: Int) {
        val index = blocks.indexOfFirst { it.id == blockId }
        val targetIndex = index + delta
        if (index == -1 || targetIndex !in blocks.indices) return
        val newBlocks = blocks.toMutableList()
        val moved = newBlocks.removeAt(index)
        newBlocks.add(targetIndex, moved)
        commit(newBlocks)
    }
}
