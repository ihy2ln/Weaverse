package com.ihy2ln.weaverse.feature.roleplay.personas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.data.db.entities.RpPersonaEntity
import com.ihy2ln.weaverse.feature.shell.WorkspaceHistory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class PersonaCategoryGroup(
    val name: String,
    val expanded: Boolean,
    val entries: List<RpPersonaEntity>,
)

data class PersonasUiState(
    val groups: List<PersonaCategoryGroup> = emptyList(),
    val pendingOpenId: String? = null,
)

@HiltViewModel
class PersonasViewModel @Inject constructor(
    private val db: WeaverseDatabase,
    private val workspaceHistory: WorkspaceHistory,
) : ViewModel() {
    private val collapsed = MutableStateFlow<Set<String>>(emptySet())
    private val _pendingOpenId = MutableStateFlow<String?>(null)

    val uiState: StateFlow<PersonasUiState> = combine(
        db.roleplayDao().observePersonas(),
        collapsed,
        _pendingOpenId,
    ) { personas, collapsedIds, pendingOpen ->
        val name = "Personas"
        PersonasUiState(
            groups = listOf(
                PersonaCategoryGroup(
                    name = name,
                    expanded = name !in collapsedIds,
                    entries = personas,
                ),
            ),
            pendingOpenId = pendingOpen,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PersonasUiState())

    val personas: StateFlow<List<RpPersonaEntity>> = db.roleplayDao()
        .observePersonas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun toggleCategory(name: String) {
        collapsed.update { set ->
            if (name in set) set - name else set + name
        }
    }

    fun addPersona() {
        viewModelScope.launch {
            val id = "rpp-${UUID.randomUUID()}"
            val entity = RpPersonaEntity(
                id = id,
                name = "New persona",
                description = "",
                isDefault = false,
            )
            db.roleplayDao().upsertPersona(entity)
            workspaceHistory.record(
                undo = { db.roleplayDao().deletePersona(id) },
                redo = { db.roleplayDao().upsertPersona(entity) },
            )
            collapsed.update { it - "Personas" }
            _pendingOpenId.value = id
        }
    }

    fun removePersona(id: String) {
        viewModelScope.launch {
            val existing = db.roleplayDao().getPersona(id) ?: return@launch
            db.roleplayDao().deletePersona(id)
            workspaceHistory.record(
                undo = { db.roleplayDao().upsertPersona(existing) },
                redo = { db.roleplayDao().deletePersona(id) },
            )
        }
    }

    fun consumePendingOpen() {
        _pendingOpenId.value = null
    }
}
