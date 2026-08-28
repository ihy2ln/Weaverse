package com.ihy2ln.weaverse.feature.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.core.ui.components.CreateWorkVocabulary
import com.ihy2ln.weaverse.core.ui.components.CampaignRulesetTemplates
import com.ihy2ln.weaverse.core.ui.components.NewWorkDetails
import com.ihy2ln.weaverse.core.ui.components.WorkCharacterOption
import com.ihy2ln.weaverse.core.text.Document
import com.ihy2ln.weaverse.core.text.toJson
import com.ihy2ln.weaverse.data.db.entities.RpChatEntity
import com.ihy2ln.weaverse.data.db.entities.RpPageMeta
import com.ihy2ln.weaverse.data.db.entities.RpMessageEntity
import com.ihy2ln.weaverse.data.db.entities.encodePages
import com.ihy2ln.weaverse.core.media.MediaRepository
import com.ihy2ln.weaverse.data.db.entities.BookEntity
import com.ihy2ln.weaverse.data.db.entities.SeriesEntity
import com.ihy2ln.weaverse.data.repo.BookRepository
import com.ihy2ln.weaverse.data.repo.SeriesRepository
import com.ihy2ln.weaverse.data.settings.SettingsRepository
import com.ihy2ln.weaverse.feature.prompt.PromptEntryBus
import com.ihy2ln.weaverse.feature.prompt.PromptEntryKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class ShellBookInfo(
    val book: BookEntity? = null,
    val series: SeriesEntity? = null,
    val backgroundPath: String? = null,
)

@HiltViewModel
class AppShellViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val bookRepository: BookRepository,
    seriesRepository: SeriesRepository,
    mediaRepository: MediaRepository,
    private val db: com.ihy2ln.weaverse.data.db.WeaverseDatabase,
    private val promptEntryBus: PromptEntryBus,
    private val workspaceHistory: WorkspaceHistory,
) : ViewModel() {
    val preferences = settings.preferences

    val campaignCharacterOptions: StateFlow<List<WorkCharacterOption>> = combine(
        db.roleplayDao().observePersonas(),
        db.roleplayDao().observeCharacters(),
        db.codexDao().observeAllCategories(),
        db.codexDao().observeAllEntries(),
    ) { personas, roster, categories, entries ->
        val characterCategoryIds = categories
            .filter { it.name.equals("Characters", ignoreCase = true) }
            .map { it.id }
            .toSet()
        buildList {
            personas.forEach { add(WorkCharacterOption("persona:${it.id}", it.name, "You")) }
            roster.forEach { add(WorkCharacterOption("roster:${it.id}", it.name, "Roster")) }
            entries.filter { it.categoryId in characterCategoryIds }.forEach {
                add(WorkCharacterOption("codex:${it.id}", it.name, "Codex"))
            }
        }.distinctBy { it.name.trim().lowercase() to it.source }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Creates a novel, campaign or storyboard. All three are a manuscript
     * underneath — a storyboard additionally gets a chat pinned to the comic
     * canvas, which is the thing its pages hang off.
     */
    fun createWork(
        vocabulary: CreateWorkVocabulary,
        details: NewWorkDetails,
        onCreated: (bookId: String, chatId: String?) -> Unit,
    ) {
        viewModelScope.launch {
            val book = bookRepository.createBook(
                title = details.title,
                genre = details.genre,
                pov = details.pov,
                tense = details.tense,
                styleGuide = details.styleGuide,
                workType = when (vocabulary) {
                    CreateWorkVocabulary.Campaign -> "campaign"
                    CreateWorkVocabulary.Storyboard -> "storyboard"
                    else -> "novel"
                },
            )
            settings.setSelectedBookId(book.id)
            var chatId: String? = null
            if (vocabulary == CreateWorkVocabulary.Storyboard) {
                val now = System.currentTimeMillis()
                val id = "rp-chat-${java.util.UUID.randomUUID()}"
                db.roleplayDao().upsertChat(
                    RpChatEntity(
                        id = id,
                        characterId = null,
                        personaId = "persona-default",
                        title = details.title,
                        displayMode = "roleplay",
                        pagesJson = encodePages(listOf(RpPageMeta(id = "page-1", order = 0))),
                        createdAt = now,
                        updatedAt = now,
                        bookId = book.id,
                    ),
                )
                chatId = id
            } else if (vocabulary == CreateWorkVocabulary.Campaign) {
                chatId = createCampaignSession(book, details)
            }
            onCreated(book.id, chatId)
        }
    }

    /** Opens the one play session owned by a campaign, creating it for legacy campaigns. */
    fun openCampaign(bookId: String, onReady: (String) -> Unit) {
        viewModelScope.launch {
            settings.setSelectedBookId(bookId)
            val existing = db.roleplayDao().getChats().firstOrNull {
                it.bookId == bookId && it.displayMode == "dungeonMaster"
            }
            val chatId = existing?.id ?: bookRepository.getBook(bookId)?.let { book ->
                createCampaignSession(
                    book,
                    NewWorkDetails(
                        title = book.title,
                        genre = book.genre,
                        pov = book.pov,
                        tense = book.tense,
                        styleGuide = book.styleGuide,
                    ),
                )
            } ?: return@launch
            onReady(chatId)
        }
    }

    private suspend fun createCampaignSession(
        book: BookEntity,
        details: NewWorkDetails,
    ): String {
        val now = System.currentTimeMillis()
        val id = "rp-campaign-${java.util.UUID.randomUUID()}"
        val selectedPersonaId = details.mainCharacters
            .firstOrNull { it.id.startsWith("persona:") }
            ?.id?.substringAfter(':')
            ?: db.roleplayDao().getPersonas().firstOrNull { it.isDefault }?.id
            ?: db.roleplayDao().getPersonas().firstOrNull()?.id
            ?: "persona-default"
        val mainCharacters = details.mainCharacters.joinToString(", ") { it.name }
            .ifBlank { details.pov.ifBlank { "Player-created party" } }
        val userIsDungeonMaster = details.campaignRoleId == "dm" ||
            details.styleGuide.contains("The user is the Dungeon Master", ignoreCase = true)
        val setup = buildString {
            appendLine("Campaign: ${details.title}")
            appendLine("Setting: ${details.genre.ifBlank { "Open fantasy setting" }}")
            appendLine("Main character(s): $mainCharacters")
            appendLine("Narrative tense: ${details.tense.ifBlank { "Past tense" }}")
            appendLine("Narrative point of view: ${details.narrativePov.ifBlank { "Third-person multiple" }}")
            appendLine("Player role: ${if (userIsDungeonMaster) "Dungeon Master" else "Adventurer"}")
            val rulesetLabel = CampaignRulesetTemplates
                .firstOrNull { it.id == details.rulesetId }
                ?.label
                ?: "Custom / systemless"
            appendLine("Rules system: $rulesetLabel")
            if (details.styleGuide.isNotBlank()) append(details.styleGuide)
        }.trim()
        db.roleplayDao().upsertChat(
            RpChatEntity(
                id = id,
                // The player controls selected protagonists; the game master must
                // not impersonate a roster character as the chat's speaker.
                characterId = null,
                personaId = selectedPersonaId,
                title = details.title,
                authorsNote = setup,
                displayMode = "dungeonMaster",
                createdAt = now,
                updatedAt = now,
                bookId = book.id,
            ),
        )
        val opening = buildString {
            append("The adventure begins in ${details.genre.ifBlank { "an uncharted world" }}. ")
            if (userIsDungeonMaster) {
                append("$mainCharacters are the AI-controlled player party. ")
                append("Describe the opening scene, world response, or ruling below; the party will decide what they do.")
            } else {
                append("$mainCharacters stand at the threshold of the first scene. ")
                append("Describe what they do in the action box below; the game master will turn each choice into the next part of the story.")
            }
        }
        db.roleplayDao().upsertMessage(
            RpMessageEntity(
                id = "rpm-${java.util.UUID.randomUUID()}",
                chatId = id,
                swipeGroupId = "sw-${java.util.UUID.randomUUID()}",
                swipeIndex = 0,
                isActiveSwipe = true,
                role = "char",
                contentJson = Document.fromPlainText(opening).toJson(),
                createdAt = now,
                displayMode = "dungeonMaster",
            ),
        )
        return id
    }
    val historyState = workspaceHistory.state

    fun undo() {
        viewModelScope.launch { workspaceHistory.undo() }
    }

    fun redo() {
        viewModelScope.launch { workspaceHistory.redo() }
    }

    fun openPrompt(kind: PromptEntryKind) {
        promptEntryBus.requestOpen(kind)
    }

    val shellInfo: StateFlow<ShellBookInfo> = combine(
        settings.preferences,
        bookRepository.observeBooks(),
        seriesRepository.observeSeries(),
        mediaRepository.observeAll(),
    ) { prefs, books, seriesList, media ->
        val book = books.find { it.id == prefs.selectedBookId } ?: books.firstOrNull()
        val series = book?.seriesId?.let { id -> seriesList.find { it.id == id } }
        val bg = prefs.backgroundMediaId.takeIf { it.isNotBlank() }
            ?.let { id -> media.find { it.id == id && it.type == "image" } }
            ?.let { entity ->
                mediaRepository.resolveFile(entity).takeIf(File::exists)?.absolutePath
            }
        ShellBookInfo(book = book, series = series, backgroundPath = bg)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShellBookInfo())

    fun setRailWidthDp(width: Float) {
        viewModelScope.launch { settings.setRailWidthDp(width) }
    }

    fun toggleRailCollapsed() {
        viewModelScope.launch {
            val current = settings.preferences.first()
            settings.setRailCollapsed(!current.layout.railCollapsed)
        }
    }

    fun setRailCollapsed(collapsed: Boolean) {
        viewModelScope.launch { settings.setRailCollapsed(collapsed) }
    }

    fun toggleDestBarCollapsed() {
        viewModelScope.launch {
            val prefs = settings.preferences.first()
            settings.setDestBarCollapsed(!prefs.layout.destBarCollapsed)
        }
    }

    fun setDestBarHeightDp(height: Float) {
        viewModelScope.launch { settings.setDestBarHeightDp(height) }
    }

    fun setDestBarCollapsed(collapsed: Boolean) {
        viewModelScope.launch { settings.setDestBarCollapsed(collapsed) }
    }

    fun setSelectedBookId(bookId: String) {
        viewModelScope.launch { settings.setSelectedBookId(bookId) }
    }

    fun setWorkspaceButtonOrder(ids: List<String>) {
        viewModelScope.launch { settings.setWorkspaceButtonOrder(ids) }
    }

    fun setModeButtonOrder(mode: AppMode, ids: List<String>) {
        viewModelScope.launch { settings.setModeButtonOrder(mode.name, ids) }
    }
}
