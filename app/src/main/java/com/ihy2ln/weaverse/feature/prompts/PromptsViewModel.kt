package com.ihy2ln.weaverse.feature.prompts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.ai.prompt.PromptMessage
import com.ihy2ln.weaverse.ai.prompt.PromptRole
import com.ihy2ln.weaverse.ai.prompt.decodePromptMessages
import com.ihy2ln.weaverse.ai.prompt.encodePromptMessages
import com.ihy2ln.weaverse.data.db.entities.PromptEntity
import com.ihy2ln.weaverse.data.db.entities.PromptFolderEntity
import com.ihy2ln.weaverse.data.repo.PromptRepository
import com.ihy2ln.weaverse.feature.shell.WorkspaceHistory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import javax.inject.Inject

enum class PromptEditorTab { General, Instructions, Advanced, Description }

data class PromptFolderGroup(
    val folder: PromptFolderEntity,
    val prompts: List<PromptEntity>,
    val expanded: Boolean = true,
)

/** One message box in the Instructions tab — [localId] is a stable Compose key, not persisted. */
data class PromptMessageUi(
    val localId: String,
    val role: String,
    val content: String,
)

data class PromptsUiState(
    val folders: List<PromptFolderGroup> = emptyList(),
    val selectedId: String? = null,
    val editorTab: PromptEditorTab = PromptEditorTab.General,
    val name: String = "",
    val type: String = "scene_beat",
    val description: String = "",
    val messages: List<PromptMessageUi> = emptyList(),
    val isDefault: Boolean = false,
    val bias: String = "",
    val guidance: String = "",
    val newFolderName: String = "",
)

@HiltViewModel
class PromptsViewModel @Inject constructor(
    private val promptRepository: PromptRepository,
    private val workspaceHistory: WorkspaceHistory,
) : ViewModel() {
    private val json = Json { ignoreUnknownKeys = true }
    private val _uiState = MutableStateFlow(PromptsUiState())
    val uiState: StateFlow<PromptsUiState> = _uiState.asStateFlow()
    private val collapsedFolders = MutableStateFlow<Set<String>>(emptySet())
    private var hydratedSelection: String? = null

    init {
        viewModelScope.launch {
            combine(
                promptRepository.observeFolders(),
                promptRepository.observePrompts(),
                collapsedFolders,
            ) { folders, prompts, collapsed ->
                Triple(folders, prompts, collapsed)
            }.collect { (folders, prompts, collapsed) ->
                val groups = folders.map { folder ->
                    PromptFolderGroup(
                        folder = folder,
                        prompts = prompts.filter { it.folderId == folder.id },
                        expanded = folder.id !in collapsed,
                    )
                }
                val currentSelected = _uiState.value.selectedId
                val selected = currentSelected?.takeIf { id -> prompts.any { it.id == id } }
                    ?: groups.firstOrNull()?.prompts?.firstOrNull()?.id
                _uiState.update { it.copy(folders = groups, selectedId = selected) }
                if (selected != null && selected != hydratedSelection) {
                    hydratedSelection = selected
                    applyPrompt(prompts.find { it.id == selected })
                }
            }
        }
    }

    fun selectPrompt(id: String) {
        hydratedSelection = id
        viewModelScope.launch {
            applyPrompt(promptRepository.observePrompt(id).first())
        }
    }

    private fun applyPrompt(prompt: PromptEntity?) {
        if (prompt == null) return
        val advanced = parseAdvanced(prompt.advancedJson)
        val messages = decodePromptMessages(prompt.instructionsJson)
            .ifEmpty { listOf(PromptMessage(PromptRole.System.name.lowercase(), "")) }
            .map { PromptMessageUi(UUID.randomUUID().toString(), it.role, it.content) }
        _uiState.update {
            it.copy(
                selectedId = prompt.id,
                name = prompt.name,
                type = prompt.type,
                description = prompt.description,
                messages = messages,
                isDefault = prompt.isDefault,
                bias = advanced.first,
                guidance = advanced.second,
            )
        }
    }

    fun setEditorTab(tab: PromptEditorTab) = _uiState.update { it.copy(editorTab = tab) }
    fun onName(value: String) = _uiState.update { it.copy(name = value) }
    fun onType(value: String) = _uiState.update { it.copy(type = value) }
    fun onDescription(value: String) = _uiState.update { it.copy(description = value) }
    fun onIsDefault(value: Boolean) = _uiState.update { it.copy(isDefault = value) }
    fun onBias(value: String) = _uiState.update { it.copy(bias = value) }
    fun onGuidance(value: String) = _uiState.update { it.copy(guidance = value) }
    fun onNewFolderName(value: String) = _uiState.update { it.copy(newFolderName = value) }

    fun onMessageContent(localId: String, value: String) = _uiState.update { state ->
        state.copy(messages = state.messages.map { if (it.localId == localId) it.copy(content = value) else it })
    }

    fun onMessageRole(localId: String, role: PromptRole) = _uiState.update { state ->
        state.copy(
            messages = state.messages.map {
                if (it.localId == localId) it.copy(role = role.name.lowercase()) else it
            },
        )
    }

    fun addMessage(role: PromptRole = PromptRole.User) = _uiState.update { state ->
        state.copy(
            messages = state.messages + PromptMessageUi(UUID.randomUUID().toString(), role.name.lowercase(), ""),
        )
    }

    fun removeMessage(localId: String) = _uiState.update { state ->
        val remaining = state.messages.filter { it.localId != localId }
        state.copy(messages = remaining.ifEmpty { listOf(PromptMessageUi(UUID.randomUUID().toString(), "system", "")) })
    }

    fun toggleFolder(folderId: String) {
        collapsedFolders.update { current ->
            if (folderId in current) current - folderId else current + folderId
        }
    }

    fun createFolder() {
        viewModelScope.launch {
            val name = _uiState.value.newFolderName.ifBlank { "New category" }
            val folder = promptRepository.createFolder(name)
            workspaceHistory.record(
                undo = { promptRepository.deleteFolder(folder.id) },
                redo = { promptRepository.upsertFolder(folder) },
            )
            _uiState.update { it.copy(newFolderName = "") }
        }
    }

    fun createPrompt(folderId: String) {
        viewModelScope.launch {
            val prompt = promptRepository.createPrompt(folderId, "New prompt", "custom")
            workspaceHistory.record(
                undo = { promptRepository.deletePrompt(prompt.id) },
                redo = { promptRepository.upsert(prompt) },
            )
            selectPrompt(prompt.id)
        }
    }

    fun deleteSelected() {
        val id = _uiState.value.selectedId ?: return
        viewModelScope.launch {
            val existing = promptRepository.getPrompt(id) ?: return@launch
            promptRepository.deletePrompt(id)
            workspaceHistory.record(
                undo = { promptRepository.upsert(existing) },
                redo = { promptRepository.deletePrompt(id) },
            )
            hydratedSelection = null
            _uiState.update { it.copy(selectedId = null) }
        }
    }

    fun save() {
        val state = _uiState.value
        val id = state.selectedId ?: return
        viewModelScope.launch {
            val existing = promptRepository.getPrompt(id)
                ?: state.folders.flatMap { it.prompts }.find { it.id == id }
            val entity = PromptEntity(
                id = id,
                folderId = existing?.folderId
                    ?: state.folders.firstOrNull()?.folder?.id.orEmpty(),
                name = state.name,
                type = state.type,
                description = state.description,
                instructionsJson = encodePromptMessages(
                    state.messages.map { PromptMessage(it.role, it.content) },
                ),
                advancedJson = encodeAdvanced(state.bias, state.guidance),
                isSystem = existing?.isSystem == true,
                isDefault = state.isDefault,
                createdAt = existing?.createdAt ?: System.currentTimeMillis(),
            )
            promptRepository.upsert(entity)
            if (existing != null && existing != entity) {
                workspaceHistory.record(
                    undo = { promptRepository.upsert(existing) },
                    redo = { promptRepository.upsert(entity) },
                )
            }
        }
    }

    private fun parseAdvanced(raw: String): Pair<String, String> = runCatching {
        val obj = json.parseToJsonElement(raw).jsonObject
        (obj["bias"]?.jsonPrimitive?.contentOrNull.orEmpty()) to
            (obj["guidance"]?.jsonPrimitive?.contentOrNull.orEmpty())
    }.getOrDefault("" to "")

    private fun encodeAdvanced(bias: String, guidance: String): String =
        buildJsonObject {
            put("bias", JsonPrimitive(bias))
            put("guidance", JsonPrimitive(guidance))
        }.toString()
}
