package com.ihy2ln.weaverse.feature.roleplay.codex

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.data.db.entity.CodexCategoryEntity
import com.ihy2ln.weaverse.data.db.entity.CodexEntryEntity
import com.ihy2ln.weaverse.data.db.entity.CodexEntryLoreEntity
import com.ihy2ln.weaverse.data.db.entity.RpCharacterEntity
import com.ihy2ln.weaverse.data.db.entity.ScopeType
import com.ihy2ln.weaverse.data.repo.CodexRepository
import com.ihy2ln.weaverse.data.repo.RoleplayRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs the Codex tab in roleplay mode (renamed per Revision 02 §2's ground
 * rule that there's only one shared Codex entity): a "World Info" view over
 * the *same* shared Codex tables Novel mode's rail uses, just scoped by
 * [ScopeType.Character] instead of [ScopeType.Book]. Entries need a category
 * (`CodexEntryEntity.categoryId` is a required FK), so a single default
 * "Lore" category is auto-created per character the first time their Codex
 * is opened — mirroring `ChatViewModel`'s auto-created-thread pattern from
 * Phase 10.
 *
 * Named `RoleplayCodexViewModel` rather than reusing the bare `CodexViewModel`
 * name Novel mode's `feature.novel.codex` package already has — same simple
 * name in two different packages is legal Kotlin but an easy source of a
 * wrong-package import mistake, so this one gets a distinct name instead.
 *
 * Deliberately a standalone editor rather than reusing Novel's
 * `CodexEntryEditorSheet` — that composable takes a concrete
 * `com.ihy2ln.weaverse.feature.novel.codex.CodexViewModel` (constructor-
 * injected, hardcoded to `ScopeType.Book`), and generalizing it to accept
 * either scope was judged riskier than a small, separate, purpose-built
 * sheet for what this screen actually needs (name/keys/body/constant — no
 * Mentions tab, since these entries aren't scene-linked).
 */
@HiltViewModel
class RoleplayCodexViewModel @Inject constructor(
    private val codexRepository: CodexRepository,
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

    val entries: StateFlow<List<CodexEntryEntity>> = selectedCharacterId.filterNotNull()
        .flatMapLatest { characterId -> codexRepository.observeEntriesForScope(ScopeType.Character, characterId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createEntry(name: String) {
        val characterId = selectedCharacterId.value ?: return
        viewModelScope.launch {
            val category = ensureDefaultCategory(characterId)
            codexRepository.upsertEntry(
                CodexEntryEntity(categoryId = category.id, scopeType = ScopeType.Character, scopeId = characterId, name = name),
            )
        }
    }

    private suspend fun ensureDefaultCategory(characterId: String): CodexCategoryEntity {
        val existing = codexRepository.observeCategories(ScopeType.Character, characterId).first()
        return existing.firstOrNull() ?: CodexCategoryEntity(
            scopeType = ScopeType.Character,
            scopeId = characterId,
            name = "Lore",
            colorHex = "#8B6FD1",
        ).also { codexRepository.upsertCategory(it) }
    }

    fun observeEntry(entryId: String) = codexRepository.observeEntry(entryId)

    fun updateEntry(entry: CodexEntryEntity) {
        viewModelScope.launch { codexRepository.upsertEntry(entry.copy(updatedAt = System.currentTimeMillis())) }
    }

    fun deleteEntry(entry: CodexEntryEntity) {
        viewModelScope.launch { codexRepository.deleteEntry(entry) }
    }

    suspend fun getLore(entryId: String): CodexEntryLoreEntity? = codexRepository.getLore(entryId)

    fun upsertLore(lore: CodexEntryLoreEntity) {
        viewModelScope.launch { codexRepository.upsertLore(lore) }
    }
}
