package com.ihy2ln.weaverse.feature.shell

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WorkspaceHistoryTest {
    @Test
    fun undoRestoresAndRedoReapplies() = runBlocking {
        val history = WorkspaceHistory()
        var value = "keep"
        history.record(
            undo = { value = "keep" },
            redo = { value = "gone" },
        )
        value = "gone"
        assertTrue(history.state.value.canUndo)
        assertFalse(history.state.value.canRedo)

        history.undo()
        assertEquals("keep", value)
        assertTrue(history.state.value.canRedo)

        history.redo()
        assertEquals("gone", value)
        assertTrue(history.state.value.canUndo)
        assertFalse(history.state.value.canRedo)
    }

    @Test
    fun newRecordClearsRedo() = runBlocking {
        val history = WorkspaceHistory()
        var n = 0
        history.record(undo = { n = 0 }, redo = { n = 1 })
        n = 1
        history.undo()
        history.record(undo = { n = 0 }, redo = { n = 2 })
        assertFalse(history.state.value.canRedo)
        n = 2
        history.undo()
        assertEquals(0, n)
    }

    @Test
    fun preUndoFlushesThenUndoPopsFlushedAction() = runBlocking {
        val history = WorkspaceHistory()
        var value = "start"
        history.registerPreUndo {
            history.record(undo = { value = "start" }, redo = { value = "typed" })
            value = "typed"
        }
        history.undo()
        assertEquals("start", value)
        assertTrue(history.state.value.canRedo)
    }

    @Test
    fun pendingUndoEnablesButtonThenFlushesOnUndo() = runBlocking {
        val history = WorkspaceHistory()
        var value = "start"
        history.addPendingUndo()
        assertTrue(history.state.value.canUndo)
        history.registerPreUndo {
            history.removePendingUndo()
            history.record(undo = { value = "start" }, redo = { value = "typed" })
            value = "typed"
        }
        history.undo()
        assertEquals("start", value)
        assertTrue(history.state.value.canRedo)
        assertFalse(history.state.value.canUndo)
    }
}
