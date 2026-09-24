package com.ihy2ln.weaverse.feature.prompt

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class PromptEntryKind {
    /** `/` — AI-generated text prompt window. */
    Ai,
    /** `\` — non-AI manual / brainstorm entry. */
    Manual,
}

/** Where dock-generated prose should land inside the novel editor. */
data class PromptInsertAnchor(
    val sceneId: String,
    val blockIndex: Int,
    val caret: Int,
)

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

    private val _insertAnchor = MutableStateFlow<PromptInsertAnchor?>(null)

    /** Latest editor caret the user tapped, so dock prompts can insert on target. */
    val insertAnchor: StateFlow<PromptInsertAnchor?> = _insertAnchor.asStateFlow()

    fun setInsertAnchor(anchor: PromptInsertAnchor?) {
        _insertAnchor.value = anchor
    }

    fun notifyNoteChanged(noteId: String) {
        _notesChanged.tryEmit(noteId)
    }

    private val _notesChanged = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val notesChanged: SharedFlow<String> = _notesChanged.asSharedFlow()

    fun requestOpen(kind: PromptEntryKind) {
        _openRequests.tryEmit(kind)
    }
}
