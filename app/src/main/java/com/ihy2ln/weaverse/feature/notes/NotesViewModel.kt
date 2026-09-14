package com.ihy2ln.weaverse.feature.notes

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.core.media.MediaClipboard
import com.ihy2ln.weaverse.core.media.MediaClipboardPayload
import com.ihy2ln.weaverse.core.media.MediaRepository
import com.ihy2ln.weaverse.core.text.Document
import com.ihy2ln.weaverse.core.text.MediaBlock
import com.ihy2ln.weaverse.core.text.MediaKind
import com.ihy2ln.weaverse.core.text.Paragraph
import com.ihy2ln.weaverse.core.text.Span
import com.ihy2ln.weaverse.core.text.documentFromJson
import com.ihy2ln.weaverse.core.text.plainText
import com.ihy2ln.weaverse.core.text.toJson
import com.ihy2ln.weaverse.core.ui.components.MediaEditAction
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.data.db.entities.SnippetEntity
import com.ihy2ln.weaverse.feature.prompt.PromptEntryBus
import com.ihy2ln.weaverse.feature.shell.WorkspaceHistory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class NoteMediaUi(
    val blockId: String,
    val mediaId: String,
    val path: String,
    val isAudio: Boolean,
    val collapsed: Boolean = false,
    val widthPercent: Float = 100f,
)

data class NotesUiState(
    val notes: List<SnippetEntity> = emptyList(),
    val selectedId: String? = null,
    val title: String = "",
    val body: String = "",
    val media: List<NoteMediaUi> = emptyList(),
    val mediaPickRequestId: Long = 0L,
    val audioPickRequestId: Long = 0L,
    val status: String = "",
    val canPasteMedia: Boolean = false,
)

@HiltViewModel
class NotesViewModel @Inject constructor(
    private val db: WeaverseDatabase,
    private val mediaRepository: MediaRepository,
    private val promptEntryBus: PromptEntryBus,
    private val mediaClipboard: MediaClipboard,
    private val workspaceHistory: WorkspaceHistory,
) : ViewModel() {
    private val _uiState = MutableStateFlow(NotesUiState())
    val uiState: StateFlow<NotesUiState> = _uiState.asStateFlow()
    private var notesDraftBaseline: Pair<String, String>? = null
    private val unregisterHistoryFlush = workspaceHistory.registerPreUndo { flushNotesDraft() }

    init {
        viewModelScope.launch {
            // Lift any older book-scoped notes into the app-wide Notes section once.
            db.snippetDao().reassignCategoryScope(CATEGORY, SCOPE_TYPE, SCOPE_ID)
            db.snippetDao().observeCategory(CATEGORY).collect { list ->
                val currentId = _uiState.value.selectedId
                val keep = currentId?.takeIf { id -> list.any { it.id == id } }
                _uiState.update {
                    it.copy(notes = list, canPasteMedia = mediaClipboard.hasPayload)
                }
                when {
                    keep != null -> Unit
                    list.isNotEmpty() -> selectNote(list.first().id)
                    currentId != null -> selectNote(null)
                }
            }
        }
        viewModelScope.launch {
            promptEntryBus.notesChanged.collect { noteId ->
                if (noteId.isBlank()) return@collect
                val selected = _uiState.value.selectedId
                if (selected == null || selected == noteId) {
                    selectNote(noteId)
                }
            }
        }
    }

    fun selectNote(id: String?) {
        promptEntryBus.activeNoteId = id
        if (notesDraftBaseline != null) {
            notesDraftBaseline = null
            workspaceHistory.removePendingUndo()
        }
        if (id == null) {
            _uiState.update {
                it.copy(
                    selectedId = null,
                    title = "",
                    body = "",
                    media = emptyList(),
                    status = "",
                    canPasteMedia = mediaClipboard.hasPayload,
                )
            }
            return
        }
        viewModelScope.launch {
            val entity = _uiState.value.notes.find { it.id == id }
                ?: db.snippetDao().getById(id)
                ?: return@launch
            val doc = documentFromJson(entity.body)
            val media = doc.blocks.mapNotNull { block ->
                val mediaBlock = block as? MediaBlock ?: return@mapNotNull null
                val mediaEntity = mediaRepository.getById(mediaBlock.mediaId) ?: return@mapNotNull null
                NoteMediaUi(
                    blockId = mediaBlock.id,
                    mediaId = mediaBlock.mediaId,
                    path = mediaRepository.resolveFile(mediaEntity).absolutePath,
                    isAudio = mediaEntity.type == "audio" || mediaBlock.kind == MediaKind.Audio,
                    collapsed = mediaBlock.collapsed,
                    widthPercent = mediaBlock.widthPercent,
                )
            }
            _uiState.update {
                it.copy(
                    selectedId = id,
                    title = entity.title,
                    body = doc.plainText(),
                    media = media,
                    status = "",
                    canPasteMedia = mediaClipboard.hasPayload,
                )
            }
        }
    }

    fun onTitleChange(value: String) {
        captureNotesDraft()
        _uiState.update { it.copy(title = value) }
    }

    fun onBodyChange(value: String) {
        captureNotesDraft()
        _uiState.update { it.copy(body = value) }
    }

    fun createNote() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val id = "note-${UUID.randomUUID()}"
            val created = SnippetEntity(
                id = id,
                scopeType = SCOPE_TYPE,
                scopeId = SCOPE_ID,
                title = "New note",
                body = Document.fromPlainText("").toJson(),
                category = CATEGORY,
                pinned = false,
                createdAt = now,
            )
            db.snippetDao().upsert(created)
            workspaceHistory.record(
                undo = {
                    db.snippetDao().deleteById(id)
                    promptEntryBus.notifyNoteChanged(id)
                },
                redo = {
                    db.snippetDao().upsert(created)
                    promptEntryBus.notifyNoteChanged(id)
                },
            )
            _uiState.update {
                it.copy(
                    selectedId = id,
                    title = "New note",
                    body = "",
                    media = emptyList(),
                    status = "",
                    canPasteMedia = mediaClipboard.hasPayload,
                )
            }
            promptEntryBus.activeNoteId = id
        }
    }

    fun saveNote() {
        if (notesDraftBaseline != null) {
            notesDraftBaseline = null
            workspaceHistory.removePendingUndo()
        }
        viewModelScope.launch {
            val state = _uiState.value
            val id = state.selectedId ?: return@launch
            val existing = db.snippetDao().getById(id) ?: state.notes.find { it.id == id } ?: SnippetEntity(
                id = id,
                scopeType = SCOPE_TYPE,
                scopeId = SCOPE_ID,
                title = state.title,
                body = "",
                category = CATEGORY,
                createdAt = System.currentTimeMillis(),
            )
            val blocks = buildList {
                if (state.body.isNotBlank()) {
                    add(Paragraph("p-${System.currentTimeMillis()}", listOf(Span(state.body))))
                }
                state.media.forEach { m ->
                    add(
                        MediaBlock(
                            id = m.blockId,
                            mediaId = m.mediaId,
                            kind = if (m.isAudio) MediaKind.Audio else MediaKind.Image,
                            widthPercent = m.widthPercent,
                            collapsed = m.collapsed,
                        ),
                    )
                }
            }
            val after = existing.copy(
                title = state.title.ifBlank { "Untitled note" },
                body = Document(blocks = blocks).toJson(),
                category = CATEGORY,
                scopeType = SCOPE_TYPE,
                scopeId = SCOPE_ID,
            )
            if (after == existing) {
                _uiState.update {
                    it.copy(status = "Saved", canPasteMedia = mediaClipboard.hasPayload)
                }
                return@launch
            }
            db.snippetDao().upsert(after)
            workspaceHistory.record(
                undo = {
                    db.snippetDao().upsert(existing)
                    promptEntryBus.notifyNoteChanged(id)
                },
                redo = {
                    db.snippetDao().upsert(after)
                    promptEntryBus.notifyNoteChanged(id)
                },
            )
            _uiState.update {
                it.copy(status = "Saved", canPasteMedia = mediaClipboard.hasPayload)
            }
        }
    }

    fun deleteNote(id: String) {
        viewModelScope.launch {
            val existing = db.snippetDao().getById(id) ?: return@launch
            db.snippetDao().deleteById(id)
            workspaceHistory.record(
                undo = {
                    db.snippetDao().upsert(existing)
                    promptEntryBus.notifyNoteChanged(id)
                },
                redo = {
                    db.snippetDao().deleteById(id)
                    promptEntryBus.notifyNoteChanged(id)
                },
            )
            if (_uiState.value.selectedId == id) {
                promptEntryBus.activeNoteId = null
                _uiState.update {
                    it.copy(
                        selectedId = null,
                        title = "",
                        body = "",
                        media = emptyList(),
                        canPasteMedia = mediaClipboard.hasPayload,
                    )
                }
            }
        }
    }

    fun requestMediaPick() {
        _uiState.update { it.copy(mediaPickRequestId = it.mediaPickRequestId + 1) }
    }

    fun requestAudioPick() {
        _uiState.update { it.copy(audioPickRequestId = it.audioPickRequestId + 1) }
    }

    fun importMedia(uris: List<Uri>) {
        viewModelScope.launch {
            val imported = uris.mapNotNull { uri ->
                runCatching {
                    val entity = mediaRepository.importFromUri(uri)
                    NoteMediaUi(
                        blockId = "nm-${UUID.randomUUID()}",
                        mediaId = entity.id,
                        path = mediaRepository.resolveFile(entity).absolutePath,
                        isAudio = entity.type == "audio",
                    )
                }.getOrNull()
            }
            if (imported.isNotEmpty()) {
                _uiState.update { it.copy(media = it.media + imported) }
                saveNote()
            }
        }
    }

    fun removeMedia(blockId: String) {
        _uiState.update { it.copy(media = it.media.filterNot { m -> m.blockId == blockId }) }
        saveNote()
    }

    fun onMediaEditAction(blockId: String, action: MediaEditAction) {
        when (action) {
            MediaEditAction.Cut -> {
                copyMedia(blockId)
                removeMedia(blockId)
            }
            MediaEditAction.Copy -> copyMedia(blockId)
            MediaEditAction.Paste -> pasteMedia(afterBlockId = blockId)
            MediaEditAction.Delete -> removeMedia(blockId)
            MediaEditAction.Shrink -> adjustWidth(blockId, -15f)
            MediaEditAction.Expand -> adjustWidth(blockId, 15f)
            MediaEditAction.Collapse -> setCollapsed(blockId, true)
            MediaEditAction.Uncollapse -> setCollapsed(blockId, false)
            MediaEditAction.Stack -> {
                _uiState.update {
                    it.copy(status = "Stack is available in Write / Roleplay chat media.")
                }
            }
            MediaEditAction.Move -> Unit
            // Panel-canvas only (Roleplay/DM storyboard).
            MediaEditAction.AdjustImage, MediaEditAction.EditImage, MediaEditAction.SeparatePanels, MediaEditAction.SeparatePanelsAuto, MediaEditAction.AddMedia, MediaEditAction.GenerateMedia, MediaEditAction.AddTextOverlay -> Unit
        }
    }

    private fun copyMedia(blockId: String) {
        val media = _uiState.value.media.find { it.blockId == blockId } ?: return
        mediaClipboard.set(
            MediaClipboardPayload(
                mediaId = media.mediaId,
                kind = if (media.isAudio) MediaKind.Audio else MediaKind.Image,
                widthPercent = media.widthPercent,
            ),
        )
        _uiState.update {
            it.copy(canPasteMedia = true, status = "Media copied")
        }
    }

    private fun pasteMedia(afterBlockId: String?) {
        val payload = mediaClipboard.payload ?: return
        viewModelScope.launch {
            val entity = mediaRepository.getById(payload.mediaId) ?: return@launch
            val inserted = NoteMediaUi(
                blockId = "nm-${UUID.randomUUID()}",
                mediaId = payload.mediaId,
                path = mediaRepository.resolveFile(entity).absolutePath,
                isAudio = entity.type == "audio" || payload.kind == MediaKind.Audio,
                widthPercent = payload.widthPercent,
            )
            _uiState.update { state ->
                val list = state.media.toMutableList()
                val index = afterBlockId?.let { id -> list.indexOfFirst { it.blockId == id } } ?: -1
                if (index >= 0) list.add(index + 1, inserted) else list.add(inserted)
                state.copy(
                    media = list,
                    canPasteMedia = mediaClipboard.hasPayload,
                    status = "Media pasted",
                )
            }
            saveNote()
        }
    }

    private fun adjustWidth(blockId: String, delta: Float) {
        _uiState.update { state ->
            state.copy(
                media = state.media.map { m ->
                    if (m.blockId != blockId) m
                    else m.copy(widthPercent = (m.widthPercent + delta).coerceIn(25f, 100f))
                },
            )
        }
        saveNote()
    }

    private fun setCollapsed(blockId: String, collapsed: Boolean) {
        _uiState.update { state ->
            state.copy(
                media = state.media.map { m ->
                    if (m.blockId != blockId) m else m.copy(collapsed = collapsed)
                },
            )
        }
        saveNote()
    }

    private fun captureNotesDraft() {
        if (notesDraftBaseline != null) return
        val state = _uiState.value
        if (state.selectedId == null) return
        notesDraftBaseline = state.title to state.body
        workspaceHistory.addPendingUndo()
    }

    private fun flushNotesDraft() {
        val baseline = notesDraftBaseline ?: return
        notesDraftBaseline = null
        workspaceHistory.removePendingUndo()
        val state = _uiState.value
        val current = state.title to state.body
        if (baseline == current) return
        val id = state.selectedId ?: return
        workspaceHistory.record(
            undo = {
                if (_uiState.value.selectedId == id) {
                    _uiState.update { it.copy(title = baseline.first, body = baseline.second) }
                }
            },
            redo = {
                if (_uiState.value.selectedId == id) {
                    _uiState.update { it.copy(title = current.first, body = current.second) }
                }
            },
        )
    }

    override fun onCleared() {
        if (notesDraftBaseline != null) {
            notesDraftBaseline = null
            workspaceHistory.removePendingUndo()
        }
        unregisterHistoryFlush()
        super.onCleared()
    }

    companion object {
        const val CATEGORY = "notes"
        /** App-wide Notes section — independent of the selected book. */
        const val SCOPE_TYPE = "app"
        const val SCOPE_ID = "global"
    }
}
