package com.ihy2ln.weaverse.feature.novel.write.editor

/** Bounded undo/redo history (spec §6: "Undo/redo stack (min 100 steps) scoped per document"). */
class UndoRedoStack<T>(private val maxSize: Int = 100) {
    private val undoStack = ArrayDeque<T>()
    private val redoStack = ArrayDeque<T>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun push(state: T) {
        undoStack.addLast(state)
        if (undoStack.size > maxSize) undoStack.removeFirst()
        redoStack.clear()
    }

    fun undo(current: T): T? {
        val previous = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(current)
        return previous
    }

    fun redo(current: T): T? {
        val next = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(current)
        return next
    }
}
