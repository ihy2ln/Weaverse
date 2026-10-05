package com.ihy2ln.weaverse.feature.games

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.ai.AiGenerationService
import com.ihy2ln.weaverse.core.media.CodexMediaIds
import com.ihy2ln.weaverse.core.text.documentFromJson
import com.ihy2ln.weaverse.core.text.plainText
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A Codex character, and their card set for the game if they have one. */
data class NovelCharacterRow(
    val entryId: String,
    val name: String,
    val mediaId: String?,
    /** The Codex picture on disk, for the list. */
    val picture: String?,
    val novel: String,
    val description: String,
    val set: CardGameCharacter?,
    val builtin: BuiltinCast.Member?,
)

data class GamesUiState(
    val loading: Boolean = true,
    val characters: List<NovelCharacterRow> = emptyList(),
    val building: Set<String> = emptySet(),
    val errors: Map<String, String> = emptyMap(),
    val editing: String? = null,
    val message: String = "",
)

/**
 * Games mode: the bridge from the novels into the card game (Adventure and Card Battle). Every Codex
 * character can get a card set (written by the AI from their Codex entry, numbers from [CardSetBuilder]); the ones
 * switched on are handed to the game each time it starts.
 */
@HiltViewModel
class GamesViewModel @Inject constructor(
    private val db: WeaverseDatabase,
    private val store: CardGameStore,
    private val ai: AiGenerationService,
) : ViewModel() {
    private val _uiState = MutableStateFlow(GamesUiState())
    val uiState: StateFlow<GamesUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            val categories = db.codexDao().getAllCategories().filter { it.name.equals("Characters", ignoreCase = true) }.map { it.id }.toSet()
            val books = db.bookDao().getAll()
            val sets = store.load().characters
            val rows = db.codexDao().getAllEntries()
                .filter { it.categoryId in categories && !it.disabled && it.name.isNotBlank() }
                .map { entry ->
                    val novel = books.firstOrNull { b -> entry.scopeId == b.id || entry.sheetJson.contains(b.id) }?.title.orEmpty()
                    val text = documentFromJson(entry.docJson).plainText().ifBlank { entry.plainText }
                    NovelCharacterRow(
                        entryId = entry.id, name = entry.name.trim(), mediaId = CodexMediaIds.parse(entry.imageMediaId).firstOrNull(),
                        picture = store.picturePath(CodexMediaIds.parse(entry.imageMediaId).firstOrNull()),
                        novel = novel, description = text, set = sets.firstOrNull { it.codexId == entry.id },
                        builtin = BuiltinCast.match(entry.name),
                    )
                }
                .sortedWith(compareBy<NovelCharacterRow>({ it.builtin != null }, { it.set == null }, { it.name.lowercase() }))
            _uiState.update { it.copy(loading = false, characters = rows) }
        }
    }

    /** Has the AI write this character's card set (6 cards + 2 ultimates); replaces an older set. */
    fun build(entryId: String) {
        val row = _uiState.value.characters.firstOrNull { it.entryId == entryId } ?: return
        if (entryId in _uiState.value.building) return
        _uiState.update { it.copy(building = it.building + entryId, errors = it.errors - entryId, message = "") }
        viewModelScope.launch {
            try {
                val id = row.set?.id ?: CardSetBuilder.characterId(row.name, entryId)
                val portrait = store.copyPortrait(id, row.mediaId)
                val answer = ai.complete(
                    userMessage = CardSetBuilder.prompt(row.name, row.description, row.novel.ifBlank { "their novel" }),
                    maxTokens = 1800,
                    temperature = .8,
                ).text
                val set = CardSetBuilder.fromAiAnswer(answer, id, row.name, row.novel, entryId, portrait)
                store.upsert(set.copy(inGame = row.set?.inGame ?: true))
                _uiState.update { it.copy(message = "${row.name} is ready to play: 6 cards and 2 ultimates.") }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (failure: Exception) {
                _uiState.update { it.copy(errors = it.errors + (entryId to (failure.message ?: "The AI could not make the cards."))) }
            } finally {
                _uiState.update { it.copy(building = it.building - entryId) }
                refresh()
            }
        }
    }

    /** A plain set from the character's role alone, for when no AI model is set up. */
    fun buildWithoutAi(entryId: String, role: String) {
        val row = _uiState.value.characters.firstOrNull { it.entryId == entryId } ?: return
        viewModelScope.launch {
            val id = row.set?.id ?: CardSetBuilder.characterId(row.name, entryId)
            val portrait = store.copyPortrait(id, row.mediaId)
            val set = CardSetBuilder.build(id, row.name, "", role, "Neutral", 4, role == "Ranger" || role == "Support", "",
                row.novel, entryId, portrait, emptyList(), emptyList())
            store.upsert(set)
            _uiState.update { it.copy(errors = it.errors - entryId, message = "${row.name} got a starter set; edit the cards to make them theirs.") }
            refresh()
        }
    }

    fun setInGame(entryId: String, inGame: Boolean) = update(entryId) { it.copy(inGame = inGame) }

    fun restyle(entryId: String, role: String, element: String, magic: Boolean) =
        update(entryId) { CardSetBuilder.restyle(it, role, element, magic) }

    fun editCard(entryId: String, cardId: String, name: String, text: String, effect: String, ep: Int, status: String) =
        update(entryId) { set ->
            val card = (set.cards + set.ultimates).firstOrNull { it.id == cardId } ?: return@update set
            CardSetBuilder.edit(set, card, name.ifBlank { card.name }, text, effect, ep, status)
        }

    fun refreshPicture(entryId: String) {
        val row = _uiState.value.characters.firstOrNull { it.entryId == entryId } ?: return
        val set = row.set ?: return
        viewModelScope.launch {
            val portrait = store.copyPortrait(set.id, row.mediaId)
            store.upsert(set.copy(portrait = portrait))
            refresh()
        }
    }

    fun delete(entryId: String) {
        val set = _uiState.value.characters.firstOrNull { it.entryId == entryId }?.set ?: return
        viewModelScope.launch {
            store.remove(set.id)
            _uiState.update { it.copy(editing = null) }
            refresh()
        }
    }

    fun openEditor(entryId: String?) = _uiState.update { it.copy(editing = entryId) }

    private fun update(entryId: String, change: (CardGameCharacter) -> CardGameCharacter) {
        val set = _uiState.value.characters.firstOrNull { it.entryId == entryId }?.set ?: return
        viewModelScope.launch {
            store.upsert(change(set))
            refresh()
        }
    }
}
