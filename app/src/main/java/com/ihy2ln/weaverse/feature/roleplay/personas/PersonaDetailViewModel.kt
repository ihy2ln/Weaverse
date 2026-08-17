package com.ihy2ln.weaverse.feature.roleplay.personas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.data.db.entities.RpPersonaEntity
import com.ihy2ln.weaverse.feature.shell.WorkspaceHistory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PersonaDetailUiState(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val isDefault: Boolean = false,
    val saved: Boolean = false,
    val statusMessage: String = "",
)

@HiltViewModel
class PersonaDetailViewModel @Inject constructor(
    private val db: WeaverseDatabase,
    private val workspaceHistory: WorkspaceHistory,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PersonaDetailUiState())
    val uiState: StateFlow<PersonaDetailUiState> = _uiState.asStateFlow()
    private var loadedId: String? = null
    private var base: RpPersonaEntity? = null

    fun load(personaId: String) {
        if (loadedId == personaId) return
        loadedId = personaId
        viewModelScope.launch {
            db.roleplayDao().observePersona(personaId).collect { entity ->
                if (entity == null) return@collect
                if (_uiState.value.id != entity.id) {
                    base = entity
                    _uiState.value = PersonaDetailUiState(
                        id = entity.id,
                        name = entity.name,
                        description = entity.description,
                        isDefault = entity.isDefault,
                    )
                }
            }
        }
    }

    fun onName(value: String) = _uiState.update { it.copy(name = value, saved = false) }
    fun onDescription(value: String) = _uiState.update { it.copy(description = value, saved = false) }
    fun onDefault(value: Boolean) = _uiState.update { it.copy(isDefault = value, saved = false) }

    fun save() {
        val state = _uiState.value
        if (state.id.isBlank()) return
        viewModelScope.launch {
            val existing = base ?: db.roleplayDao().getPersona(state.id) ?: return@launch
            if (state.isDefault) {
                db.roleplayDao().getPersonas().forEach { persona ->
                    if (persona.isDefault && persona.id != state.id) {
                        db.roleplayDao().upsertPersona(persona.copy(isDefault = false))
                    }
                }
            }
            val updated = existing.copy(
                name = state.name.ifBlank { "Untitled persona" },
                description = state.description,
                isDefault = state.isDefault,
            )
            db.roleplayDao().upsertPersona(updated)
            if (existing != updated) {
                workspaceHistory.record(
                    undo = { restorePersona(existing) },
                    redo = { restorePersona(updated) },
                )
            }
            base = updated
            _uiState.update { it.copy(saved = true, statusMessage = "Saved") }
        }
    }

    private suspend fun restorePersona(entity: RpPersonaEntity) {
        db.roleplayDao().upsertPersona(entity)
        if (_uiState.value.id != entity.id) return
        base = entity
        _uiState.update {
            it.copy(
                name = entity.name,
                description = entity.description,
                isDefault = entity.isDefault,
                saved = true,
                statusMessage = "Restored",
            )
        }
    }
}
