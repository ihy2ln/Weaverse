package com.ihy2ln.weaverse.feature.novel.codex

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.core.media.CodexMediaIds
import com.ihy2ln.weaverse.core.media.MediaRepository
import com.ihy2ln.weaverse.core.text.decodeAliases
import com.ihy2ln.weaverse.data.db.entities.CodexEntryEntity
import com.ihy2ln.weaverse.data.db.entities.CodexRelationshipEntity
import com.ihy2ln.weaverse.data.repo.CodexRepository
import com.ihy2ln.weaverse.feature.shell.WorkspaceHistory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RelationshipRow(
    val id: String,
    val label: String,
    val otherEntryId: String,
    val otherEntryName: String,
    /** true: this entry -> other; false: other -> this (relationship was created from the other side). */
    val outgoing: Boolean,
)

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
    val aliasesText: String = "",
    val alwaysInclude: Boolean = false,
    val trackMentions: Boolean = true,
    val caseSensitiveMatching: Boolean = false,
    val media: List<CodexMediaItem> = emptyList(),
    val mediaPickRequestId: Long = 0L,
    val audioPickRequestId: Long = 0L,
    val saved: Boolean = false,
    val statusMessage: String = "",
    val showSettingsMenu: Boolean = false,
    val relationships: List<RelationshipRow> = emptyList(),
    /** Every other Codex entry, offered as relationship targets. */
    val otherEntries: List<CodexEntryEntity> = emptyList(),
    val showAddRelationship: Boolean = false,
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
                        aliasesText = decodeAliases(entry.aliasesJson).joinToString(", "),
                        alwaysInclude = entry.alwaysInclude,
                        trackMentions = entry.trackMentions,
                        caseSensitiveMatching = entry.caseSensitiveMatching,
                        media = resolveMedia(mediaIds),
                    )
                }
            }
        }
        viewModelScope.launch {
            combine(
                codexRepository.observeRelationships(entryId),
                codexRepository.observeAllEntries(),
            ) { relationships, allEntries ->
                val nameById = allEntries.associateBy({ it.id }, { it.name })
                val rows = relationships.mapNotNull { rel ->
                    val outgoing = rel.fromEntryId == entryId
                    val otherId = if (outgoing) rel.toEntryId else rel.fromEntryId
                    val otherName = nameById[otherId] ?: return@mapNotNull null
                    RelationshipRow(rel.id, rel.label, otherId, otherName, outgoing)
                }.sortedBy { it.otherEntryName }
                rows to allEntries.filter { it.id != entryId }
            }.collect { (rows, others) ->
                _uiState.update { it.copy(relationships = rows, otherEntries = others) }
            }
        }
    }

    fun onName(value: String) = _uiState.update { it.copy(name = value, saved = false) }
    fun onBody(value: String) = _uiState.update { it.copy(plainText = value, saved = false) }
    fun onAliasesText(value: String) = _uiState.update { it.copy(aliasesText = value, saved = false) }
    fun onAlwaysInclude(value: Boolean) = _uiState.update { it.copy(alwaysInclude = value, saved = false) }
    fun onTrackMentions(value: Boolean) = _uiState.update { it.copy(trackMentions = value, saved = false) }
    fun onCaseSensitiveMatching(value: Boolean) =
        _uiState.update { it.copy(caseSensitiveMatching = value, saved = false) }

    fun onShowSettingsMenuChange(show: Boolean) = _uiState.update { it.copy(showSettingsMenu = show) }
    fun onShowAddRelationshipChange(show: Boolean) = _uiState.update { it.copy(showAddRelationship = show) }

    fun addRelationship(toEntryId: String, label: String) {
        if (label.isBlank()) return
        val fromEntryId = _uiState.value.id
        if (fromEntryId.isBlank()) return
        viewModelScope.launch {
            val entity = codexRepository.addRelationship(fromEntryId, toEntryId, label.trim())
            workspaceHistory.record(
                undo = { codexRepository.deleteRelationship(entity.id) },
                redo = { codexRepository.addRelationship(entity.fromEntryId, entity.toEntryId, entity.label) },
            )
            _uiState.update { it.copy(showAddRelationship = false) }
        }
    }

    fun removeRelationship(id: String) {
        viewModelScope.launch { codexRepository.deleteRelationship(id) }
    }

    /** Body text to insert when the caller pastes clipboard content in. */
    fun onPaste(clipboardText: String) {
        if (clipboardText.isBlank()) return
        _uiState.update {
            val separator = if (it.plainText.isBlank() || it.plainText.endsWith("\n")) "" else "\n"
            it.copy(plainText = it.plainText + separator + clipboardText, saved = false)
        }
    }

    private fun aliasesList(): List<String> =
        _uiState.value.aliasesText.split(",").map { it.trim() }.filter { it.isNotBlank() }

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
                aliases = aliasesList(),
                alwaysInclude = state.alwaysInclude,
                trackMentions = state.trackMentions,
                caseSensitiveMatching = state.caseSensitiveMatching,
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
                aliasesText = decodeAliases(entity.aliasesJson).joinToString(", "),
                alwaysInclude = entity.alwaysInclude,
                trackMentions = entity.trackMentions,
                caseSensitiveMatching = entity.caseSensitiveMatching,
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
