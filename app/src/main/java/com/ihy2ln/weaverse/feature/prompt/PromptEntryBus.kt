package com.ihy2ln.weaverse.feature.prompt

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class PromptEntryKind {
    /** `/` — AI-generated text prompt window. */
    Ai,
    /** `\` — non-AI manual / brainstorm entry. */
    Manual,
}

/**
 * App-wide prompt entry: keyboard `/` and `\` open the shared prompt window.
 * Screens may also collect [openRequests] to sync local UI.
 */
@Singleton
class PromptEntryBus @Inject constructor() {
    private val _openRequests = MutableSharedFlow<PromptEntryKind>(extraBufferCapacity = 1)
    val openRequests: SharedFlow<PromptEntryKind> = _openRequests.asSharedFlow()

    /** Last selected Notes entry, for prompt insert targeting. */
    @Volatile
    var activeNoteId: String? = null

    private val _notesChanged = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val notesChanged: SharedFlow<String> = _notesChanged.asSharedFlow()

    fun notifyNoteChanged(noteId: String) {
        _notesChanged.tryEmit(noteId)
    }

    fun requestOpen(kind: PromptEntryKind) {
        _openRequests.tryEmit(kind)
    }
}
