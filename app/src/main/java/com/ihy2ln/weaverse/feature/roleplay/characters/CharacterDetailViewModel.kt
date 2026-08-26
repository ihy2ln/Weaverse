package com.ihy2ln.weaverse.feature.roleplay.characters

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.data.db.entities.RpCharacterEntity
import com.ihy2ln.weaverse.data.db.entities.decodeEquipment
import com.ihy2ln.weaverse.data.db.entities.decodeItems
import com.ihy2ln.weaverse.feature.shell.WorkspaceHistory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CharacterDetailUiState(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val personality: String = "",
    val scenario: String = "",
    val firstMes: String = "",
    val mesExample: String = "",
    val creatorNotes: String = "",
    val systemPrompt: String = "",
    val postHistoryInstructions: String = "",
    val tags: String = "",
    val colorHex: String = "",
    val sheet: RpgCharacterSheet = RpgCharacterSheet(),
    val inventory: List<String> = emptyList(),
    val equipment: List<String> = emptyList(),
    val saved: Boolean = false,
    val statusMessage: String = "",
)

@HiltViewModel
class CharacterDetailViewModel @Inject constructor(
    private val db: WeaverseDatabase,
    private val workspaceHistory: WorkspaceHistory,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CharacterDetailUiState())
    val uiState: StateFlow<CharacterDetailUiState> = _uiState.asStateFlow()
    private var loadedId: String? = null
    private var base: RpCharacterEntity? = null

    fun load(characterId: String) {
        if (loadedId == characterId) return
        loadedId = characterId
        viewModelScope.launch {
            db.roleplayDao().observeCharacter(characterId).collect { entity ->
                if (entity == null) return@collect
                if (_uiState.value.id != entity.id) {
                    base = entity
                    _uiState.value = CharacterDetailUiState(
                        id = entity.id,
                        name = entity.name,
                        description = entity.description,
                        personality = entity.personality,
                        scenario = entity.scenario,
                        firstMes = entity.firstMes,
                        mesExample = entity.mesExample,
                        creatorNotes = entity.creatorNotes,
                        systemPrompt = entity.systemPrompt,
                        postHistoryInstructions = entity.postHistoryInstructions,
                        tags = tagsFromJson(entity.tagsJson),
                        colorHex = entity.colorHex.orEmpty(),
                        sheet = decodeRpgSheet(entity.extensionsJson),
                        inventory = decodeItems(entity.inventoryJson).map { item ->
                            if (item.quantity > 1) "${item.name} ×${item.quantity}" else item.name
                        },
                        equipment = decodeEquipment(entity.equipmentJson).values.filter { it.isNotBlank() },
                    )
                }
            }
        }
    }

    fun onName(value: String) = _uiState.update { it.copy(name = value, saved = false) }
    fun onDescription(value: String) = _uiState.update { it.copy(description = value, saved = false) }
    fun onPersonality(value: String) = _uiState.update { it.copy(personality = value, saved = false) }
    fun onScenario(value: String) = _uiState.update { it.copy(scenario = value, saved = false) }
    fun onFirstMes(value: String) = _uiState.update { it.copy(firstMes = value, saved = false) }
    fun onMesExample(value: String) = _uiState.update { it.copy(mesExample = value, saved = false) }
    fun onCreatorNotes(value: String) = _uiState.update { it.copy(creatorNotes = value, saved = false) }
    fun onSystemPrompt(value: String) = _uiState.update { it.copy(systemPrompt = value, saved = false) }
    fun onPostHistory(value: String) = _uiState.update { it.copy(postHistoryInstructions = value, saved = false) }
    fun onTags(value: String) = _uiState.update { it.copy(tags = value, saved = false) }
    fun onColorHex(value: String) = _uiState.update { it.copy(colorHex = value, saved = false) }
    fun onSheet(value: RpgCharacterSheet) = _uiState.update { it.copy(sheet = value, saved = false) }
    fun adjustHp(delta: Int) = _uiState.update {
        it.copy(sheet = it.sheet.withCurrentHp(it.sheet.currentHp + delta), saved = false)
    }
    fun adjustAbility(name: String, delta: Int) = _uiState.update { state ->
        val sheet = state.sheet
        val updated = when (name) {
            "Strength" -> sheet.copy(strength = (sheet.strength + delta).coerceIn(1, 30))
            "Dexterity" -> sheet.copy(dexterity = (sheet.dexterity + delta).coerceIn(1, 30))
            "Constitution" -> sheet.copy(constitution = (sheet.constitution + delta).coerceIn(1, 30))
            "Intelligence" -> sheet.copy(intelligence = (sheet.intelligence + delta).coerceIn(1, 30))
            "Wisdom" -> sheet.copy(wisdom = (sheet.wisdom + delta).coerceIn(1, 30))
            else -> sheet.copy(charisma = (sheet.charisma + delta).coerceIn(1, 30))
        }
        state.copy(sheet = updated, saved = false)
    }

    fun save() {
        val state = _uiState.value
        if (state.id.isBlank()) return
        viewModelScope.launch {
            val existing = base ?: db.roleplayDao().getCharacter(state.id) ?: return@launch
            val updated = existing.copy(
                name = state.name.ifBlank { "Untitled character" },
                description = state.description,
                personality = state.personality,
                scenario = state.scenario,
                firstMes = state.firstMes,
                mesExample = state.mesExample,
                creatorNotes = state.creatorNotes,
                systemPrompt = state.systemPrompt,
                postHistoryInstructions = state.postHistoryInstructions,
                tagsJson = tagsToJson(state.tags),
                colorHex = state.colorHex.takeIf { it.isNotBlank() },
                extensionsJson = encodeRpgSheet(existing.extensionsJson, state.sheet),
            )
            db.roleplayDao().upsertCharacter(updated)
            if (existing != updated) {
                workspaceHistory.record(
                    undo = { restoreCharacter(existing) },
                    redo = { restoreCharacter(updated) },
                )
            }
            base = updated
            _uiState.update { it.copy(saved = true, statusMessage = "Saved") }
        }
    }

    private suspend fun restoreCharacter(entity: RpCharacterEntity) {
        db.roleplayDao().upsertCharacter(entity)
        if (_uiState.value.id != entity.id) return
        base = entity
        _uiState.update {
            it.copy(
                name = entity.name,
                description = entity.description,
                personality = entity.personality,
                scenario = entity.scenario,
                firstMes = entity.firstMes,
                mesExample = entity.mesExample,
                creatorNotes = entity.creatorNotes,
                systemPrompt = entity.systemPrompt,
                postHistoryInstructions = entity.postHistoryInstructions,
                tags = tagsFromJson(entity.tagsJson),
                colorHex = entity.colorHex.orEmpty(),
                sheet = decodeRpgSheet(entity.extensionsJson),
                inventory = decodeItems(entity.inventoryJson).map { item ->
                    if (item.quantity > 1) "${item.name} ×${item.quantity}" else item.name
                },
                equipment = decodeEquipment(entity.equipmentJson).values.filter { it.isNotBlank() },
                saved = true,
                statusMessage = "Restored",
            )
        }
    }

    private fun tagsFromJson(json: String): String {
        val trimmed = json.trim()
        if (!trimmed.startsWith("[")) return trimmed
        return trimmed
            .removePrefix("[")
            .removeSuffix("]")
            .split(",")
            .map { it.trim().removeSurrounding("\"") }
            .filter { it.isNotBlank() }
            .joinToString(", ")
    }

    private fun tagsToJson(tags: String): String {
        val parts = tags.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (parts.isEmpty()) return "[]"
        return parts.joinToString(prefix = "[", postfix = "]") { "\"${it.replace("\"", "")}\"" }
    }
}
