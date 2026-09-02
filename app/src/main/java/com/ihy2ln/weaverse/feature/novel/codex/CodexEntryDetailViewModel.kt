package com.ihy2ln.weaverse.feature.novel.codex

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.core.media.CodexMediaIds
import com.ihy2ln.weaverse.core.media.MediaRepository
import com.ihy2ln.weaverse.core.roleplay.avatarColorHexFor
import com.ihy2ln.weaverse.core.text.decodeAliases
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.data.db.entities.CodexEntryEntity
import com.ihy2ln.weaverse.data.db.entities.RpCharacterEntity
import com.ihy2ln.weaverse.data.db.entities.RpItem
import com.ihy2ln.weaverse.data.db.entities.decodeItems
import com.ihy2ln.weaverse.data.db.entities.encodeItems
import com.ihy2ln.weaverse.data.repo.CodexRepository
import com.ihy2ln.weaverse.feature.roleplay.characters.decodeRpgSheet
import com.ihy2ln.weaverse.feature.shell.WorkspaceHistory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CodexMediaItem(
    val id: String,
    val path: String,
    val isVideo: Boolean,
    val isAudio: Boolean = false,
)

/**
 * A codex entry is a sheet. Characters use the RPG Roster sheet itself, so the
 * state here is mostly the link to it; every other kind carries its own
 * template in [sheet], plus the codex-only bookkeeping (aliases, mention
 * tracking, extra media) no sheet has a place for.
 */
data class CodexEntryDetailUiState(
    val id: String = "",
    val name: String = "",
    val categoryName: String = "",
    val kind: CodexEntryKind = CodexEntryKind.Other,
    val sheet: CodexSheetData = CodexSheetData(),
    val portraitPath: String = "",
    val avatarColorHex: String = "",
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
    /** Roster character holding this entry's sheet and pack, for kinds that carry. */
    val rosterCharacterId: String? = null,
    /** "Fighter 3 · 10/10 HP · AC 10 · 4 carried" for the entry header. */
    val rosterSummary: String = "",
    val loading: Boolean = true,
) {
    /** Characters, places and objects hold things; a legend does not. */
    val carriesInventory: Boolean
        get() = kind.ledgerVocabulary() != null
}

@HiltViewModel
class CodexEntryDetailViewModel @Inject constructor(
    private val codexRepository: CodexRepository,
    private val mediaRepository: MediaRepository,
    private val workspaceHistory: WorkspaceHistory,
    private val rosterLink: CodexRosterLink,
    private val db: WeaverseDatabase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CodexEntryDetailUiState())
    val uiState: StateFlow<CodexEntryDetailUiState> = _uiState.asStateFlow()
    private var loadedId: String? = null
    private var mediaIds: MutableList<String> = mutableListOf()

    fun load(entryId: String) {
        if (loadedId == entryId) return
        loadedId = entryId
        viewModelScope.launch {
            combine(
                codexRepository.observeEntry(entryId),
                codexRepository.observeAllCategories(),
                db.roleplayDao().observeCharacters(),
                mediaRepository.observeAll(),
            ) { entry, categories, characters, media ->
                val category = categories.firstOrNull { it.id == entry?.categoryId }
                val linked = characters.firstOrNull { it.defaultCodexId == entryId }
                Quad(entry, category?.name.orEmpty(), linked, media.associateBy { it.id })
            }.collect { (entry, categoryName, linked, mediaById) ->
                if (entry == null) return@collect
                val firstLoad = _uiState.value.id != entry.id
                if (firstLoad) mediaIds = CodexMediaIds.parse(entry.imageMediaId).toMutableList()
                val stored = decodeCodexSheet(entry.sheetJson)
                val kind = _uiState.value.takeUnless { firstLoad }?.sheet?.kindOr(categoryName)
                    ?: stored.kindOr(categoryName)
                // A character entry is a roster character; create it on first open.
                if (kind == CodexEntryKind.Character && linked == null) {
                    rosterLink.ensureCharacterFor(entry.id)
                }
                if (linked != null && kind == CodexEntryKind.Character) mirrorSheetIntoEntry(entry, linked)
                val portrait = linked?.avatarMediaId
                    ?.let(mediaById::get)
                    ?.let { mediaRepository.resolveFile(it).absolutePath }
                    .orEmpty()
                    .ifBlank {
                        CodexMediaIds.parse(entry.imageMediaId).firstOrNull()
                            ?.let(mediaById::get)
                            ?.let { mediaRepository.resolveFile(it).absolutePath }
                            .orEmpty()
                    }
                _uiState.update { current ->
                    current.copy(
                        id = entry.id,
                        name = if (firstLoad || kind == CodexEntryKind.Character) {
                            linked?.name?.ifBlank { entry.name } ?: entry.name
                        } else {
                            current.name
                        },
                        categoryName = categoryName,
                        kind = kind,
                        // Never clobber edits in progress with a re-emission.
                        sheet = if (firstLoad) stored.seededFrom(kind, entry.plainText) else current.sheet,
                        portraitPath = portrait,
                        avatarColorHex = avatarColorHexFor(entry.name, entry.colorHex ?: linked?.colorHex),
                        aliasesText = if (firstLoad) {
                            decodeAliases(entry.aliasesJson).joinToString(", ")
                        } else {
                            current.aliasesText
                        },
                        alwaysInclude = if (firstLoad) entry.alwaysInclude else current.alwaysInclude,
                        trackMentions = if (firstLoad) entry.trackMentions else current.trackMentions,
                        caseSensitiveMatching = if (firstLoad) {
                            entry.caseSensitiveMatching
                        } else {
                            current.caseSensitiveMatching
                        },
                        media = resolveMedia(mediaIds),
                        rosterCharacterId = linked?.id,
                        rosterSummary = linked?.let(::summaryOf).orEmpty(),
                        loading = false,
                    )
                }
            }
        }
    }

    /** Four-way carrier for the load combine; Kotlin only ships Pair and Triple. */
    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

    /** Keeps a character entry's name/text in step with the roster sheet that owns them. */
    private suspend fun mirrorSheetIntoEntry(entry: CodexEntryEntity, character: RpCharacterEntity) {
        val name = character.name.ifBlank { entry.name }
        val text = character.description
        if (entry.name == name && entry.plainText == text) return
        codexRepository.updateEntryText(entry.id, name, text)
    }

    private fun summaryOf(character: RpCharacterEntity): String {
        val sheet = decodeRpgSheet(character.extensionsJson)
        val carried = decodeItems(character.inventoryJson).sumOf { it.quantity.coerceAtLeast(1) }
        return "${sheet.characterClass} ${sheet.level} · ${sheet.currentHp}/${sheet.maxHp} HP · " +
            "AC ${sheet.armorClass} · $carried carried"
    }

    /**
     * Places and objects only get a roster carrier once someone opens their
     * Inventory — a location holds stock, an object can be a container.
     */
    fun ensureCarrier() {
        val state = _uiState.value
        if (state.id.isBlank() || state.rosterCharacterId != null || !state.carriesInventory) return
        viewModelScope.launch {
            val character = rosterLink.ensureCharacterFor(state.id) ?: return@launch
            _uiState.update { it.copy(rosterCharacterId = character.id) }
        }
    }

    fun onName(value: String) = _uiState.update { it.copy(name = value, saved = false) }

    fun onSheet(value: CodexSheetData) = _uiState.update {
        it.copy(sheet = value, kind = value.kindOr(it.categoryName), saved = false)
    }

    fun onAliasesText(value: String) = _uiState.update { it.copy(aliasesText = value, saved = false) }
    fun onAlwaysInclude(value: Boolean) = _uiState.update { it.copy(alwaysInclude = value, saved = false) }
    fun onTrackMentions(value: Boolean) = _uiState.update { it.copy(trackMentions = value, saved = false) }
    fun onCaseSensitiveMatching(value: Boolean) =
        _uiState.update { it.copy(caseSensitiveMatching = value, saved = false) }

    fun onShowSettingsMenuChange(show: Boolean) = _uiState.update { it.copy(showSettingsMenu = show) }

    /** Clipboard text lands in the field that kind treats as its body. */
    fun onPaste(clipboardText: String) {
        if (clipboardText.isBlank()) return
        val state = _uiState.value
        if (state.kind == CodexEntryKind.Character) {
            val characterId = state.rosterCharacterId ?: return
            viewModelScope.launch {
                val character = db.roleplayDao().getCharacter(characterId) ?: return@launch
                val gap = if (character.description.isBlank() || character.description.endsWith("\n")) "" else "\n"
                db.roleplayDao().upsertCharacter(
                    character.copy(
                        description = character.description + gap + clipboardText,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
                _uiState.update { it.copy(statusMessage = "Pasted into the sheet's description") }
            }
            return
        }
        val sheet = state.sheet
        val appended = when (state.kind) {
            CodexEntryKind.Location -> sheet.copy(
                location = sheet.location.copy(description = append(sheet.location.description, clipboardText)),
            )
            CodexEntryKind.Item -> sheet.copy(
                item = sheet.item.copy(description = append(sheet.item.description, clipboardText)),
            )
            CodexEntryKind.Lore -> sheet.copy(
                lore = sheet.lore.copy(explanation = append(sheet.lore.explanation, clipboardText)),
            )
            else -> sheet.copy(
                other = sheet.other.copy(details = append(sheet.other.details, clipboardText)),
            )
        }
        _uiState.update { it.copy(sheet = appended, saved = false, statusMessage = "Pasted into the sheet") }
    }

    private fun append(current: String, addition: String): String {
        val gap = if (current.isBlank() || current.endsWith("\n")) "" else "\n"
        return current + gap + addition
    }

    /** Cog menu: create (or reuse) the roster character for this entry. */
    fun addToRoster() {
        val state = _uiState.value
        if (state.id.isBlank()) return
        viewModelScope.launch {
            val character = rosterLink.ensureCharacterFor(state.id)
            _uiState.update {
                it.copy(
                    rosterCharacterId = character?.id,
                    statusMessage = if (character != null) {
                        "Added to Roster · ${character.name}"
                    } else {
                        "Could not add to Roster"
                    },
                )
            }
        }
    }

    /** Cog menu: file this entry as an item in the linked carrier's inventory. */
    fun addToInventory() {
        val state = _uiState.value
        if (state.id.isBlank()) return
        viewModelScope.launch {
            val character = state.rosterCharacterId?.let { db.roleplayDao().getCharacter(it) }
                ?: rosterLink.ensureCharacterFor(state.id)
            if (character == null) {
                _uiState.update { it.copy(statusMessage = "Could not resolve a carrier") }
                return@launch
            }
            val item = RpItem(
                id = "item-${java.util.UUID.randomUUID()}",
                name = state.name.ifBlank { "New item" },
                notes = state.sheet.entryTextFor(state.kind).ifBlank { state.name },
            )
            db.roleplayDao().upsertCharacter(
                character.copy(
                    inventoryJson = encodeItems(decodeItems(character.inventoryJson) + item),
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            _uiState.update {
                it.copy(statusMessage = "Added \"${item.name}\" to ${character.name}'s inventory")
            }
        }
    }

    /** Cog menu: duplicate this entry, keeping category, text, sheet and media. */
    fun duplicateEntry() {
        val state = _uiState.value
        if (state.id.isBlank()) return
        viewModelScope.launch {
            val source = codexRepository.getEntry(state.id) ?: return@launch
            val copy = codexRepository.addEntry(source.categoryId, name = source.name)
            codexRepository.updateEntry(
                id = copy.id,
                name = "${source.name} (copy)",
                plainText = source.plainText,
                aliases = decodeAliases(source.aliasesJson),
                alwaysInclude = source.alwaysInclude,
                trackMentions = source.trackMentions,
                caseSensitiveMatching = source.caseSensitiveMatching,
                sheetJson = source.sheetJson,
                inventoryJson = source.inventoryJson,
            )
            if (!source.imageMediaId.isNullOrBlank()) {
                codexRepository.setEntryMediaIds(copy.id, CodexMediaIds.parse(source.imageMediaId))
            }
            _uiState.update { it.copy(statusMessage = "Copied to Codex · \"${source.name} (copy)\"") }
        }
    }

    /** Entry text for a copy — whatever this kind treats as its body. */
    suspend fun copyText(): String {
        val state = _uiState.value
        val body = if (state.kind == CodexEntryKind.Character) {
            state.rosterCharacterId?.let { db.roleplayDao().getCharacter(it)?.description }.orEmpty()
        } else {
            state.sheet.entryTextFor(state.kind)
        }
        return "${state.name}\n\n$body"
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

    /**
     * Saves the sheet and the codex settings. A character entry's prose lives on
     * the roster sheet, which saves itself; every other kind writes its template
     * here, and its body field becomes the entry text the AI reads.
     */
    fun save() {
        val state = _uiState.value
        if (state.id.isBlank()) return
        viewModelScope.launch {
            val before = codexRepository.getEntry(state.id) ?: return@launch
            val isCharacter = state.kind == CodexEntryKind.Character
            codexRepository.updateEntry(
                id = state.id,
                name = if (isCharacter) before.name else state.name.trim().ifBlank { before.name },
                plainText = if (isCharacter) before.plainText else state.sheet.entryTextFor(state.kind),
                aliases = aliasesList(),
                alwaysInclude = state.alwaysInclude,
                trackMentions = state.trackMentions,
                caseSensitiveMatching = state.caseSensitiveMatching,
                imageMediaId = CodexMediaIds.encode(mediaIds),
                clearImageMediaId = mediaIds.isEmpty(),
                sheetJson = if (isCharacter) null else encodeCodexSheet(state.sheet.copy(kind = state.kind.name)),
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
        val restored = decodeCodexSheet(entity.sheetJson)
        _uiState.update {
            it.copy(
                name = entity.name,
                sheet = restored,
                kind = restored.kindOr(it.categoryName),
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
