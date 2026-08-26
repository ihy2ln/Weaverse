package com.ihy2ln.weaverse.feature.roleplay.lorebook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.data.repo.CodexRepository
import com.ihy2ln.weaverse.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoreEntryUi(
    val id: String,
    val name: String,
    val category: String,
    val summary: String,
)

data class AdventureLoreUiState(
    val entries: List<LoreEntryUi> = emptyList(),
    val loading: Boolean = true,
)

/**
 * Lore for the **active adventure only**, not the whole library.
 *
 * A codex entry belongs to a scope, and a fresh adventure owns none — so this
 * starts empty on purpose. The full codex is still reachable under Extra →
 * Codex; duplicating hundreds of entries into a brand new adventure would bury
 * the handful that actually matter at the start.
 */
@HiltViewModel
class AdventureLoreViewModel @Inject constructor(
    private val codexRepository: CodexRepository,
    settings: SettingsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AdventureLoreUiState())
    val uiState: StateFlow<AdventureLoreUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                codexRepository.observeAllCategories(),
                codexRepository.observeAllEntries(),
                settings.preferences.map { it.selectedBookId },
            ) { categories, entries, adventureId ->
                val categoryName = categories.associate { it.id to it.name }
                AdventureLoreUiState(
                    entries = entries
                        .filter { it.scopeId == adventureId && !it.disabled }
                        .sortedBy { it.name.lowercase() }
                        .map { entry ->
                            LoreEntryUi(
                                id = entry.id,
                                name = entry.name,
                                category = categoryName[entry.categoryId].orEmpty(),
                                summary = entry.plainText
                                    .lineSequence()
                                    .firstOrNull { it.isNotBlank() }
                                    ?.trim()
                                    .orEmpty()
                                    .take(140),
                            )
                        },
                    loading = false,
                )
            }.collect { _uiState.value = it }
        }
    }
}
