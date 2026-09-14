package com.ihy2ln.weaverse.feature.novel.codex

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.core.media.MediaRepository
import com.ihy2ln.weaverse.core.ui.theme.CodexCategoryColors
import com.ihy2ln.weaverse.core.ui.theme.toHexString
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.data.db.entities.CodexCategoryEntity
import com.ihy2ln.weaverse.data.db.entities.CodexEntryEntity
import com.ihy2ln.weaverse.data.repo.CodexRepository
import com.ihy2ln.weaverse.feature.roleplay.textgame.adamsHavenCardCatalog
import com.ihy2ln.weaverse.feature.shell.WorkspaceHistory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CodexCategoryGroup(
    val category: CodexCategoryEntity,
    val entries: List<CodexEntryUi>,
    val expanded: Boolean = true,
)

data class CodexUiState(
    val scope: String = "All",
    val bookId: String = "",
    val entries: List<CodexEntryEntity> = emptyList(),
    val grouped: List<CodexCategoryGroup> = emptyList(),
)

@HiltViewModel
class CodexViewModel @Inject constructor(
    private val codexRepository: CodexRepository,
    private val workspaceHistory: WorkspaceHistory,
    private val mediaRepository: MediaRepository,
    private val rosterLink: CodexRosterLink,
    private val codexQuickAdd: CodexQuickAdd,
    private val db: WeaverseDatabase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CodexUiState())
    val uiState: StateFlow<CodexUiState> = _uiState.asStateFlow()
    private val collapsed = MutableStateFlow<Set<String>>(emptySet())

    init {
        viewModelScope.launch {
            codexRepository.ensureGlobalAndMigrate()
            codexRepository.ensureWorldsCategory()
            dedupeCategories()
            attachBundledCardArt()
            combine(
                codexRepository.observeAllCategories(),
                codexRepository.observeAllEntries(),
                collapsed,
                db.roleplayDao().observeCharacters(),
                mediaRepository.observeAll(),
            ) { categories, entries, collapsedIds, characters, media ->
                val mediaById = media.associateBy { it.id }
                val linked = rosterLink.linkedByEntryId(characters)
                // One group per name: never show duplicate categories, even if
                // older data still carries same-named rows.
                val uniqueCategories = categories
                    .groupBy { it.name.trim().lowercase() }
                    .map { (_, same) -> same.first() }
                val grouped = uniqueCategories.map { cat ->
                    CodexCategoryGroup(
                        category = cat,
                        entries = entries
                            .filter { it.categoryId == cat.id }
                            .map { rosterLink.decorate(it, cat.name, linked, mediaById) },
                        expanded = cat.id !in collapsedIds,
                    )
                }
                CodexUiState(
                    scope = "All",
                    bookId = "",
                    entries = entries,
                    grouped = grouped,
                )
            }.collect { _uiState.value = it }
        }
    }

    /**
     * Deletes same-named category rows, folding their entries into the
     * surviving category — keeps the codex free of "Characters" ×5 clutter.
     */
    private suspend fun dedupeCategories() {
        val categories = db.codexDao().getAllCategories()
        val remap = linkedMapOf<String, String>()
        categories
            .groupBy { it.name.trim().lowercase() }
            .forEach { (_, group) ->
                val keep = group.first()
                group.drop(1).forEach { extra ->
                    remap[extra.id] = keep.id
                    db.codexDao().deleteCategory(extra.id)
                }
            }
        if (remap.isEmpty()) return
        db.codexDao().getAllEntries().forEach { entry ->
            val target = remap[entry.categoryId] ?: return@forEach
            db.codexDao().upsertEntry(
                entry.copy(
                    categoryId = target,
                    scopeType = com.ihy2ln.weaverse.data.repo.CodexScopes.TYPE,
                    scopeId = com.ihy2ln.weaverse.data.repo.CodexScopes.ID,
                ),
            )
        }
    }

    fun setScope(scope: String) {
        // Kept so older UI callers compile; Codex is always global.
    }

    fun toggleCategory(categoryId: String) {
        collapsed.update { current ->
            if (categoryId in current) current - categoryId else current + categoryId
        }
    }

    fun addEntry(categoryId: String) {
        viewModelScope.launch {
            val entity = codexRepository.addEntry(categoryId)
            workspaceHistory.record(
                undo = { codexRepository.deleteEntry(entity.id) },
                redo = { codexRepository.saveEntry(entity) },
            )
            collapsed.update { it - categoryId }
            // Pre-fill the fresh entry with AI-generated sheet content so the
            // user edits rather than types everything from scratch. Silent
            // no-op when no API key is configured.
            runCatching { codexQuickAdd.fillExisting(entity.id) }
        }
    }

    /** Creates a user-defined category, tinted with the next codex palette color. */
    fun addCategory(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            val colorHex = CodexCategoryColors[
                _uiState.value.grouped.size % CodexCategoryColors.size,
            ].toHexString()
            codexRepository.createCategory(trimmed, colorHex)
        }
    }

    fun removeEntry(entryId: String) {
        viewModelScope.launch {
            val existing = codexRepository.getEntry(entryId) ?: return@launch
            codexRepository.deleteEntry(entryId)
            workspaceHistory.record(
                undo = { codexRepository.saveEntry(existing) },
                redo = { codexRepository.deleteEntry(entryId) },
            )
        }
    }

    /**
     * One-time: installs the bundled Adams Haven card art into the Pictures
     * library and attaches each card to the codex entry whose name matches the
     * card slug — so entries show their art fully offline, no gallery pick.
     */
    private suspend fun attachBundledCardArt() {
        val mediaIdBySlug = adamsHavenCardCatalog().associate { card ->
            val slug = card.id.substringAfter('/')
            slug to runCatching {
                mediaRepository.registerBundledImage(
                    assetPath = card.artAssetPath,
                    id = card.mediaId,
                    relativePath = "images/adams_haven/${card.category}/$slug.png",
                    width = 941,
                    height = 1672,
                ).id
            }.getOrNull()
        }
        db.codexDao().getAllEntries().forEach { entry ->
            if (!entry.imageMediaId.isNullOrBlank()) return@forEach
            val mediaId = mediaIdBySlug[slugify(entry.name)] ?: return@forEach
            codexRepository.setEntryMediaIds(entry.id, listOf(mediaId))
        }
    }

    private fun slugify(name: String): String = name.lowercase()
        .map { if (it.isLetterOrDigit()) it else '-' }
        .joinToString("")
        .replace(Regex("-+"), "-")
        .trim('-')
}
