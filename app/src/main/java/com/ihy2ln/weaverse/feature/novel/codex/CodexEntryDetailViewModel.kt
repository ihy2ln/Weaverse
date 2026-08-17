package com.ihy2ln.weaverse.feature.novel.codex

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.core.media.CodexMediaIds
import com.ihy2ln.weaverse.core.media.MediaRepository
import com.ihy2ln.weaverse.data.db.entities.CodexEntryEntity
import com.ihy2ln.weaverse.data.repo.CodexRepository
import com.ihy2ln.weaverse.feature.shell.WorkspaceHistory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CodexMediaItem(
    val id: String,
    val path: String,
    val isVideo: Boolean,
    val isAudio: Boolean = false,
)

data class CodexEntryDetailUiState(
    val id: String = "",
    val name: String = "",
    val plainText: String = "",
    val alwaysInclude: Boolean = false,
    val media: List<CodexMediaItem> = emptyList(),
    val mediaPickRequestId: Long = 0L,
    val audioPickRequestId: Long = 0L,
    val saved: Boolean = false,
    val statusMessage: String = "",
)

@HiltViewModel
class CodexEntryDetailViewModel @Inject constructor(
    private val codexRepository: CodexRepository,
    private val mediaRepository: MediaRepository,
    private val workspaceHistory: WorkspaceHistory,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CodexEntryDetailUiState())
    val uiState: StateFlow<CodexEntryDetailUiState> = _uiState.asStateFlow()
    private var loadedId: String? = null
    private var mediaIds: MutableList<String> = mutableListOf()

    fun load(entryId: String) {
        if (loadedId == entryId) return
        loadedId = entryId
        viewModelScope.launch {
            codexRepository.observeEntry(entryId).collect { entry ->
                if (entry == null) return@collect
                if (_uiState.value.id != entry.id) {
                    mediaIds = CodexMediaIds.parse(entry.imageMediaId).toMutableList()
                    _uiState.value = CodexEntryDetailUiState(
                        id = entry.id,
                        name = entry.name,
                        plainText = entry.plainText,
                        alwaysInclude = entry.alwaysInclude,
                        media = resolveMedia(mediaIds),
                    )
                }
            }
        }
    }

    fun onName(value: String) = _uiState.update { it.copy(name = value, saved = false) }
    fun onBody(value: String) = _uiState.update { it.copy(plainText = value, saved = false) }
    fun onAlwaysInclude(value: Boolean) = _uiState.update { it.copy(alwaysInclude = value, saved = false) }

    fun requestMediaPick() {
        _uiState.update { it.copy(mediaPickRequestId = it.mediaPickRequestId + 1) }
    }

    fun requestAudioPick() {
        _uiState.update { it.copy(audioPickRequestId = it.audioPickRequestId + 1) }
    }

    fun importMedia(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val before = codexRepository.getEntry(_uiState.value.id) ?: return@launch
            runCatching {
                val imported = mediaRepository.importFromUris(uris)
                mediaIds += imported.map { it.id }
                mediaIds = mediaIds.distinct().toMutableList()
                persistMediaIds()
                val after = codexRepository.getEntry(before.id)
                if (after != null) {
                    workspaceHistory.record(
                        undo = { restoreCodexEntry(before) },
                        redo = { restoreCodexEntry(after) },
                    )
                }
                _uiState.update {
                    it.copy(
                        media = resolveMedia(mediaIds),
                        saved = true,
                        statusMessage = "Added ${imported.size} media item(s)",
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(statusMessage = err.message ?: "Failed to import media")
                }
            }
        }
    }

    fun removeMedia(mediaId: String) {
        viewModelScope.launch {
            val before = codexRepository.getEntry(_uiState.value.id) ?: return@launch
            mediaIds.removeAll { it == mediaId }
            persistMediaIds()
            val after = codexRepository.getEntry(before.id) ?: return@launch
            workspaceHistory.record(
                undo = { restoreCodexEntry(before) },
                redo = { restoreCodexEntry(after) },
            )
            _uiState.update {
                it.copy(media = resolveMedia(mediaIds), saved = true, statusMessage = "Media removed")
            }
        }
    }

    fun save() {
        val state = _uiState.value
        if (state.id.isBlank()) return
        viewModelScope.launch {
            val before = codexRepository.getEntry(state.id) ?: return@launch
            codexRepository.updateEntry(
                id = state.id,
                name = state.name,
                plainText = state.plainText,
                alwaysInclude = state.alwaysInclude,
                imageMediaId = CodexMediaIds.encode(mediaIds),
                clearImageMediaId = mediaIds.isEmpty(),
            )
            val after = codexRepository.getEntry(state.id) ?: return@launch
            if (before != after) {
                workspaceHistory.record(
                    undo = { restoreCodexEntry(before) },
                    redo = { restoreCodexEntry(after) },
                )
            }
            _uiState.update { it.copy(saved = true, statusMessage = "Saved") }
        }
    }

    private suspend fun restoreCodexEntry(entity: CodexEntryEntity) {
        codexRepository.saveEntry(entity)
        mediaIds = CodexMediaIds.parse(entity.imageMediaId).toMutableList()
        _uiState.update {
            it.copy(
                name = entity.name,
                plainText = entity.plainText,
                alwaysInclude = entity.alwaysInclude,
                media = resolveMedia(mediaIds),
                saved = true,
            )
        }
    }

    private suspend fun persistMediaIds() {
        val id = _uiState.value.id
        if (id.isBlank()) return
        codexRepository.setEntryMediaIds(id, mediaIds)
    }

    private suspend fun resolveMedia(ids: List<String>): List<CodexMediaItem> =
        ids.mapNotNull { id ->
            val entity = mediaRepository.getById(id) ?: return@mapNotNull null
            CodexMediaItem(
                id = id,
                path = mediaRepository.resolveFile(entity).absolutePath,
                isVideo = entity.type == "video",
                isAudio = entity.type == "audio",
            )
        }
}
