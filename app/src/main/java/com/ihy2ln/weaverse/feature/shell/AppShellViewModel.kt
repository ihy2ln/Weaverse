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
import com.ihy2ln.weaverse.data.db.entities.RpCharacterEntity
import com.ihy2ln.weaverse.data.db.entities.encodePages
import com.ihy2ln.weaverse.data.db.entities.decodePages
import com.ihy2ln.weaverse.core.text.withGridPlacement
import com.ihy2ln.weaverse.core.media.MediaRepository
import com.ihy2ln.weaverse.data.db.entities.BookEntity
import com.ihy2ln.weaverse.data.db.entities.SeriesEntity
import com.ihy2ln.weaverse.data.repo.BookRepository
import com.ihy2ln.weaverse.data.repo.SeriesRepository
import com.ihy2ln.weaverse.data.settings.SettingsRepository
import com.ihy2ln.weaverse.feature.prompt.PromptEntryBus
import com.ihy2ln.weaverse.feature.prompt.PromptEntryKind
import com.ihy2ln.weaverse.feature.roleplay.chat.adventureStartupPrompt
import com.ihy2ln.weaverse.feature.roleplay.characters.RpgCharacterSheet
import com.ihy2ln.weaverse.feature.roleplay.characters.encodeRpgSheet
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
    val backgroundVideoPath: String? = null,
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
    private val chatRoomSeeder: com.ihy2ln.weaverse.feature.chatting.ChatRoomSeeder,
    private val mangaImporter: com.ihy2ln.weaverse.core.media.MangaFileImporter,
) : ViewModel() {
    val preferences = settings.preferences

    val campaignCharacterOptions: StateFlow<List<WorkCharacterOption>> = combine(
        db.roleplayDao().observePersonas(),
        db.roleplayDao().observeCharacters(),
        db.codexDao().observeAllCategories(),
        db.codexDao().observeAllEntries(),
    ) { personas, roster, categories, entries ->
        val playerNames = personas.map { it.name.trim().lowercase() }.toSet()
        val characterCategoryIds = categories
            .filter { it.name.equals("Characters", ignoreCase = true) }
            .map { it.id }
            .toSet()
        buildList {
            personas.forEach { add(WorkCharacterOption("persona:${it.id}", it.name, "You")) }
            roster.filterNot {
                it.defaultCodexId?.startsWith("persona:") == true ||
                    it.name.trim().lowercase() in playerNames
            }.forEach { add(WorkCharacterOption("roster:${it.id}", it.name, "Roster")) }
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
                    CreateWorkVocabulary.TextGame -> "text_game"
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
                if (details.mangaFileUri.isNotBlank()) {
                    // Whole manga/comic file: every page becomes a full-page panel
                    // on its own storyboard page, ready for panel separation.
                    runCatching {
                        importPagesIntoChat(id, android.net.Uri.parse(details.mangaFileUri))
                    }
                }
            } else if (vocabulary.campaignSpecific) {
                chatId = createCampaignSession(
                    book,
                    details,
                    textGame = vocabulary == CreateWorkVocabulary.TextGame,
                )
            }
            if (vocabulary != CreateWorkVocabulary.Storyboard) {
                // Every new novel/campaign gets its Discord rooms right away.
                chatRoomSeeder.ensureRoomsForBook(book)
            }
            onCreated(book.id, chatId)
        }
    }

    /**
     * Attaches imported manga pages to a storyboard chat: one storyboard page
     * and one full-page panel message per imported page, in order. Runs on
     * IO and persists progressively so opening the storyboard mid-import
     * already shows the pages that landed.
     */
    private suspend fun importPagesIntoChat(chatId: String, uri: android.net.Uri) {
        val chat = db.roleplayDao().getChat(chatId) ?: return
        val pageMetas = decodePages(chat.pagesJson).toMutableList()
        // A newly-created storyboard starts with one placeholder page. A
        // whole-book import replaces it so the first visible tab is page 1.
        if (pageMetas.size == 1 && pageMetas.first().id == "page-1") {
            pageMetas.clear()
        }
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            mangaImporter.importPages(
                uri = uri,
                onProgress = {},
            ) { media, label ->
                val pageId = "page-${java.util.UUID.randomUUID()}"
                val now = System.currentTimeMillis()
                pageMetas.add(
                    RpPageMeta(
                        id = pageId,
                        order = (pageMetas.maxOfOrNull { it.order } ?: -1) + 1,
                        title = label,
                    ),
                )
                val block = com.ihy2ln.weaverse.core.text.MediaBlock(
                    id = "mb-${java.util.UUID.randomUUID()}",
                    mediaId = media.id,
                    kind = com.ihy2ln.weaverse.core.text.MediaKind.Image,
                    pageId = pageId,
                ).withGridPlacement(0, 0, 12, 12, 12)
                db.roleplayDao().upsertMessage(
                    RpMessageEntity(
                        id = "rpm-$now-${pageMetas.size}",
                        chatId = chatId,
                        swipeGroupId = "sw-$now-${pageMetas.size}",
                        swipeIndex = 0,
                        isActiveSwipe = true,
                        role = "user",
                        contentJson = com.ihy2ln.weaverse.core.text.Document(listOf(block)).toJson(),
                        createdAt = now,
                        displayMode = "roleplay",
                    ),
                )
                db.roleplayDao().upsertChat(
                    chat.copy(pagesJson = encodePages(pageMetas), updatedAt = now),
                )
            }
        }
    }

    /** Opens the one play session owned by a campaign, creating it for legacy campaigns. */
    fun openCampaign(bookId: String, onReady: (String) -> Unit) {        viewModelScope.launch {
            settings.setSelectedBookId(bookId)
            val existing = db.roleplayDao().getChats().firstOrNull {
                it.bookId == bookId && it.displayMode == "dungeonMaster"
            }
            existing?.personaId?.let { ensurePlayerSheet(it, System.currentTimeMillis()) }
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
        textGame: Boolean = false,
    ): String {
        val now = System.currentTimeMillis()
        val id = "rp-campaign-${java.util.UUID.randomUUID()}"
        val effectiveCharacters = details.mainCharacters.map { option ->
            if (!option.id.startsWith("persona:")) return@map option
            val personaId = option.id.substringAfter(':')
            ensurePlayerSheet(personaId, now) ?: option
        }
        effectiveCharacters
            .filter { it.id.startsWith("roster:") }
            .forEach { option ->
                db.roleplayDao().getCharacter(option.id.substringAfter(':'))?.let { character ->
                    if (!character.inParty) db.roleplayDao().upsertCharacter(character.copy(inParty = true))
                }
            }
        val selectedPersonaId = details.mainCharacters
            .firstOrNull { it.id.startsWith("persona:") }
            ?.id?.substringAfter(':')
            ?: db.roleplayDao().getPersonas().firstOrNull { it.isDefault }?.id
            ?: db.roleplayDao().getPersonas().firstOrNull()?.id
            ?: "persona-default"
        val mainCharacters = effectiveCharacters.joinToString(", ") { it.name }
            .ifBlank { if (textGame) "Unnamed Summoner / MC" else "None selected — guided character creation required" }
        val userIsDungeonMaster = details.campaignRoleId == "dm" ||
            details.styleGuide.contains("The user is the Dungeon Master", ignoreCase = true)
        val setup = buildString {
            appendLine("${if (textGame) "Text Game session" else "Campaign"}: ${details.title}")
            appendLine("Setting: ${details.genre.ifBlank { "Open fantasy setting" }}")
            appendLine("Main character(s): $mainCharacters")
            appendLine(
                "Main character IDs: " + effectiveCharacters
                    .joinToString(", ") { it.id }
                    .ifBlank { "none" },
            )
            appendLine("Narrative tense: ${if (textGame) "Present tense" else details.tense.ifBlank { "Past tense" }}")
            appendLine("Narrative point of view: ${if (textGame) "First-person Summoner" else details.narrativePov.ifBlank { "Third-person multiple" }}")
            appendLine("Player role: ${if (textGame) "Summoner / MC" else if (userIsDungeonMaster) "Dungeon Master" else "Adventurer"}")
            if (textGame) appendLine("Text Game difficulty: ${details.difficultyId}")
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
                displayMode = if (textGame) "textGame" else "dungeonMaster",
                createdAt = now,
                updatedAt = now,
                bookId = book.id,
            ),
        )
        if (textGame) return id
        val opening = adventureStartupPrompt(
            userIsDungeonMaster = userIsDungeonMaster,
            needsCharacter = details.mainCharacters.isEmpty() && !userIsDungeonMaster,
        )
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

    /** Migrates each player persona to one stable, initially blank tabletop character sheet. */
    private suspend fun ensurePlayerSheet(personaId: String, now: Long): WorkCharacterOption? {
        val persona = db.roleplayDao().getPersona(personaId) ?: return null
        val sheetId = "rpc-player-$personaId"
        val sheet = db.roleplayDao().getCharacter(sheetId) ?: RpCharacterEntity(
            id = sheetId,
            name = persona.name,
            avatarMediaId = persona.avatarMediaId,
            description = persona.description,
            tagsJson = "[\"Player\"]",
            extensionsJson = encodeRpgSheet("{}", RpgCharacterSheet()),
            defaultCodexId = "persona:$personaId",
            inParty = true,
            createdAt = now,
        )
        db.roleplayDao().upsertCharacter(sheet.copy(inParty = true))
        return WorkCharacterOption("roster:$sheetId", persona.name, "Player roster")
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
        val bgEntity = prefs.backgroundMediaId.takeIf { it.isNotBlank() }
            ?.let { id -> media.find { it.id == id } }
        val bgPath = bgEntity?.let { mediaRepository.resolveFile(it).takeIf(File::exists)?.absolutePath }
        val bg = bgPath.takeIf { bgEntity?.type == "image" }
        val bgVideo = bgPath.takeIf { bgEntity?.type == "video" }
        ShellBookInfo(book = book, series = series, backgroundPath = bg, backgroundVideoPath = bgVideo)
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
