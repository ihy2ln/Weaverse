package com.ihy2ln.weaverse.feature.roleplay.characters

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.core.roleplay.CharacterCardImporter
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.data.db.entities.RpCharacterEntity
import com.ihy2ln.weaverse.feature.shell.WorkspaceHistory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import javax.inject.Inject

data class CharacterCategoryGroup(
    val name: String,
    val expanded: Boolean,
    val entries: List<RpCharacterEntity>,
)

data class CharactersUiState(
    val groups: List<CharacterCategoryGroup> = emptyList(),
    val importStatus: String = "",
    val pendingOpenId: String? = null,
)

@HiltViewModel
class CharactersViewModel @Inject constructor(
    private val db: WeaverseDatabase,
    private val cardImporter: CharacterCardImporter,
    private val workspaceHistory: WorkspaceHistory,
) : ViewModel() {
    private val json = Json { ignoreUnknownKeys = true }
    private val collapsed = MutableStateFlow<Set<String>>(emptySet())
    private val _importStatus = MutableStateFlow("")
    private val _pendingOpenId = MutableStateFlow<String?>(null)

    val uiState: StateFlow<CharactersUiState> = combine(
        db.roleplayDao().observeCharacters(),
        collapsed,
        _importStatus,
        _pendingOpenId,
    ) { characters, collapsedIds, status, pendingOpen ->
        val grouped = characters.groupBy { categoryOf(it) }
            .toSortedMap()
            .map { (name, entries) ->
                CharacterCategoryGroup(
                    name = name,
                    expanded = name !in collapsedIds,
                    entries = entries.sortedBy { it.name },
                )
            }
        CharactersUiState(groups = grouped, importStatus = status, pendingOpenId = pendingOpen)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CharactersUiState())

    /** Back-compat. */
    val characters: StateFlow<List<RpCharacterEntity>> = db.roleplayDao()
        .observeCharacters()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val importStatus: StateFlow<String> = _importStatus.asStateFlow()

    fun toggleCategory(name: String) {
        collapsed.update { set ->
            if (name in set) set - name else set + name
        }
    }

    fun addCharacter(category: String = "Characters") {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val safe = category.replace("\"", "")
            val id = "rpc-${UUID.randomUUID()}"
            val entity = RpCharacterEntity(
                id = id,
                name = "New character",
                description = "",
                tagsJson = "[\"$safe\"]",
                createdAt = now,
            )
            db.roleplayDao().upsertCharacter(entity)
            workspaceHistory.record(
                undo = { db.roleplayDao().deleteCharacter(id) },
                redo = { db.roleplayDao().upsertCharacter(entity) },
            )
            collapsed.update { it - category }
            _pendingOpenId.value = id
        }
    }

    fun removeCharacter(id: String) {
        viewModelScope.launch {
            val existing = db.roleplayDao().getCharacter(id) ?: return@launch
            db.roleplayDao().deleteCharacter(id)
            workspaceHistory.record(
                undo = { db.roleplayDao().upsertCharacter(existing) },
                redo = { db.roleplayDao().deleteCharacter(id) },
            )
        }
    }

    fun importCard(uri: Uri) {
        viewModelScope.launch {
            runCatching { cardImporter.importFromUri(uri) }
                .onSuccess { id ->
                    _importStatus.update { "Imported character $id" }
                    _pendingOpenId.value = id
                }
                .onFailure { err -> _importStatus.update { "Import failed: ${err.message}" } }
        }
    }

    fun consumePendingOpen() {
        _pendingOpenId.value = null
    }

    private fun categoryOf(character: RpCharacterEntity): String {
        val tags = runCatching {
            json.parseToJsonElement(character.tagsJson).jsonArray.map { it.jsonPrimitive.content }
        }.getOrDefault(emptyList())
        return tags.firstOrNull()?.takeIf { it.isNotBlank() } ?: "Characters"
    }
}
