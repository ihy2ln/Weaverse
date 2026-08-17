package com.ihy2ln.weaverse.feature.shell

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class WorkspaceHistoryState(
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
)

/**
 * App-wide undo/redo for reversible user actions (not screen transitions).
 * Chrome Undo / Redo buttons sit next to Import / Export.
 */
@Singleton
class WorkspaceHistory @Inject constructor() {
    data class Record(
        val undo: suspend () -> Unit,
        val redo: suspend () -> Unit,
    )

    private val lock = Any()
    private val undoStack = ArrayDeque<Record>()
    private val redoStack = ArrayDeque<Record>()
    private val preUndo = mutableListOf<() -> Unit>()
    private var pendingUndoCount = 0
    private val _state = MutableStateFlow(WorkspaceHistoryState())
    val state: StateFlow<WorkspaceHistoryState> = _state.asStateFlow()

    /**
     * Flush in-progress typing (or similar) into [record] before Undo/Redo.
     * Returns an unregister function.
     */
    fun registerPreUndo(block: () -> Unit): () -> Unit {
        synchronized(lock) { preUndo.add(block) }
        return { synchronized(lock) { preUndo.remove(block) } }
    }

    /**
     * Mark in-progress typing (or similar) so chrome Undo stays enabled
     * until [registerPreUndo] flushes it onto the stack.
     */
    fun addPendingUndo() {
        synchronized(lock) {
            pendingUndoCount++
            publishLocked()
        }
    }

    fun removePendingUndo() {
        synchronized(lock) {
            if (pendingUndoCount == 0) return
            pendingUndoCount--
            publishLocked()
        }
    }

    fun record(undo: suspend () -> Unit, redo: suspend () -> Unit) {
        synchronized(lock) {
            undoStack.addLast(Record(undo = undo, redo = redo))
            while (undoStack.size > 50) undoStack.removeFirst()
            redoStack.clear()
            publishLocked()
        }
    }

    suspend fun undo() {
        runPreUndo()
        val action = synchronized(lock) { undoStack.removeLastOrNull() } ?: return
        action.undo()
        synchronized(lock) {
            redoStack.addLast(action)
            publishLocked()
        }
    }

    suspend fun redo() {
        runPreUndo()
        val action = synchronized(lock) { redoStack.removeLastOrNull() } ?: return
        action.redo()
        synchronized(lock) {
            undoStack.addLast(action)
            publishLocked()
        }
    }

    private fun runPreUndo() {
        val hooks = synchronized(lock) { preUndo.toList() }
        hooks.forEach { it() }
    }

    private fun publishLocked() {
        _state.value = WorkspaceHistoryState(
            canUndo = undoStack.isNotEmpty() || pendingUndoCount > 0,
            canRedo = redoStack.isNotEmpty(),
        )
    }
}
