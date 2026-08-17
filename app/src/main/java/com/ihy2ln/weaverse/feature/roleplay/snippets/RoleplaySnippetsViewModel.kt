package com.ihy2ln.weaverse.feature.roleplay.snippets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.data.db.entity.RpCharacterEntity
import com.ihy2ln.weaverse.data.db.entity.ScopeType
import com.ihy2ln.weaverse.data.db.entity.SnippetEntity
import com.ihy2ln.weaverse.data.repo.RoleplayRepository
import com.ihy2ln.weaverse.data.repo.SnippetLabelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Backs the rail's Snippets tab in roleplay mode (Revision 02 §1.4): reusable bits of prose
 * scoped to one character — same [SnippetEntity]/[SnippetLabelRepository] Novel mode's own
 * Snippets tab uses, just [ScopeType.Character] instead of [ScopeType.Book]. A standalone
 * ViewModel rather than a generalized `SnippetsViewModel` for the same reason
 * `RoleplayCodexViewModel` is standalone, not a generalized `CodexViewModel` — Novel's is
 * constructor-injected hardcoded to Book scope, and threading an optional scope through it was
 * judged riskier than a small parallel ViewModel. No export/import here yet — `SnippetBackupService`
 * is Book-scoped only; scoping it to Character too is future work. */
@HiltViewModel
class RoleplaySnippetsViewModel @Inject constructor(
    private val snippetLabelRepository: SnippetLabelRepository,
    roleplayRepository: RoleplayRepository,
) : ViewModel() {
    val characters: StateFlow<List<RpCharacterEntity>> = roleplayRepository.observeCharacters()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedCharacterId = MutableStateFlow<String?>(null)

    val selectedCharacterId: StateFlow<String?> = combine(characters, _selectedCharacterId) { list, selectedId ->
        selectedId ?: list.firstOrNull()?.id
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun selectCharacter(characterId: String) {
        _selectedCharacterId.value = characterId
    }

    val snippets: StateFlow<List<SnippetEntity>> = selectedCharacterId.filterNotNull()
        .flatMapLatest { characterId -> snippetLabelRepository.observeSnippets(ScopeType.Character, characterId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createSnippet(title: String) {
        val characterId = selectedCharacterId.value ?: return
        viewModelScope.launch {
            snippetLabelRepository.upsertSnippet(SnippetEntity(scopeType = ScopeType.Character, scopeId = characterId, title = title, body = ""))
        }
    }

    fun updateSnippet(snippet: SnippetEntity) {
        viewModelScope.launch { snippetLabelRepository.upsertSnippet(snippet) }
    }

    fun deleteSnippet(snippet: SnippetEntity) {
        viewModelScope.launch { snippetLabelRepository.deleteSnippet(snippet) }
    }

    fun togglePinned(snippet: SnippetEntity) {
        viewModelScope.launch { snippetLabelRepository.upsertSnippet(snippet.copy(pinned = !snippet.pinned)) }
    }
}
