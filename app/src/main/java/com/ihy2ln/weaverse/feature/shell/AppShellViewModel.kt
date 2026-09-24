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
import com.ihy2ln.weaverse.core.manga.MangaDownloadRepository
import com.ihy2ln.weaverse.data.db.entities.BookEntity
import com.ihy2ln.weaverse.data.db.entities.SeriesEntity
import com.ihy2ln.weaverse.data.repo.BookRepository
import com.ihy2ln.weaverse.data.repo.SeriesRepository
import com.ihy2ln.weaverse.data.settings.SettingsRepository
import com.ihy2ln.weaverse.feature.prompt.PromptEntryBus
import com.ihy2ln.weaverse.feature.prompt.PromptEntryKind
import com.ihy2ln.weaverse.feature.roleplay.combat.RpgCombatRuleset
import com.ihy2ln.weaverse.feature.roleplay.campaign.RpgCampaignRepository
import com.ihy2ln.weaverse.feature.roleplay.campaign.RpgCampaignSetupSnapshot
import com.ihy2ln.weaverse.feature.roleplay.campaign.RpgStartupState
import com.ihy2ln.weaverse.feature.roleplay.campaign.createRpgCampaign
import com.ihy2ln.weaverse.feature.roleplay.characters.createRpgCharacterSheet
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
    private val mangaDownloadRepository: MangaDownloadRepository,
    private val startSlots: com.ihy2ln.weaverse.core.story.StartSlotStore,
) : ViewModel() {
    val preferences = settings.preferences
    @Inject lateinit var homeHistory: HomeHistory
    fun recordAccess(mode: String, kind: String, id: String, target: String = "") {
        viewModelScope.launch { homeHistory.record(mode, kind, id, target) }
    }
    fun openSceneFromSearch(sceneId: String, onOpen: () -> Unit) {
        viewModelScope.launch {
            val scene = db.manuscriptDao().getScene(sceneId) ?: return@launch
            val chapter = db.manuscriptDao().getChapter(scene.chapterId) ?: return@launch
            val act = db.manuscriptDao().getAct(chapter.actId) ?: return@launch
            settings.setSelectedBookId(act.bookId)
            homeHistory.record("Novel", "book", act.bookId, sceneId)
            onOpen()
        }
    }
    fun openBookForBrowsing(id: String, writing: Boolean, onOpen: (String) -> Unit) {
        viewModelScope.launch {
            if (db.bookDao().getById(id) == null) return@launch
            settings.setSelectedBookId(id)
            val scenes = db.manuscriptDao().getReaderScenes(id)
            val saved = if (writing) db.bookBrowsingDao().get(id)?.writeSceneId.orEmpty() else settings.readerState(id).first().lastSceneId
            val target = saved.takeIf { target -> scenes.any { it.id == target } } ?: scenes.firstOrNull()?.id.orEmpty()
            homeHistory.record("Novel", "book", id)
            onOpen(target)
        }
    }
    fun openRecent(item: HomeItem, onOpen: (HomeItem) -> Unit) {
        viewModelScope.launch {
            val current = homeHistory.items.first().firstOrNull { it.key == item.key } ?: return@launch
            current.bookId?.let { settings.setSelectedBookId(it) }
            homeHistory.record(current.mode, current.kind, current.contentId, current.target)
            onOpen(current)
        }
    }

    fun toggleFavoriteSettingTemplate(id: String) {
        viewModelScope.launch { settings.toggleFavoriteSettingTemplate(id) }
    }

    fun toggleFavoriteSettingDetail(id: String) {
        viewModelScope.launch { settings.toggleFavoriteSettingDetail(id) }
    }

    fun addSettingTemplate(name: String, section: String, theme: String, guidance: String) {
        viewModelScope.launch { settings.addSettingTemplate(name, guidance, section, theme) }
    }

    fun removeSettingTemplate(id: String) {
        viewModelScope.launch { settings.removeSettingTemplate(id) }
    }

    fun addSettingDetailTemplate(name: String, section: String, theme: String, guidance: String) {
        viewModelScope.launch { settings.addSettingDetailTemplate(name, guidance, section, theme) }
    }

    fun removeSettingDetailTemplate(id: String) {
        viewModelScope.launch { settings.removeSettingDetailTemplate(id) }
    }

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
            val accessMode = when (vocabulary) {
                CreateWorkVocabulary.Storyboard -> AppMode.Storyboard
                CreateWorkVocabulary.Campaign -> AppMode.Roleplay
                CreateWorkVocabulary.TextGame -> AppMode.Games
                else -> AppMode.Novel
            }
            homeHistory.record(
                accessMode.name,
                if (accessMode == AppMode.Games) "chat" else "book",
                if (accessMode == AppMode.Games) chatId.orEmpty() else book.id,
                if (accessMode == AppMode.Roleplay) chatId.orEmpty() else "",
            )
            onCreated(book.id, chatId)
        }
    }

    /**
     * The + menu's "From CYOA template": makes a book or campaign with no dialog and
     * marks it so its start loads the starting template and lands on verification.
     */
    fun createFromTemplate(
        vocabulary: CreateWorkVocabulary,
        onCreated: (bookId: String, chatId: String?) -> Unit,
    ) {
        createWork(
            vocabulary,
            NewWorkDetails(title = com.ihy2ln.weaverse.core.story.StartSlotStore.templateWorkTitle(null)),
        ) { bookId, chatId ->
            viewModelScope.launch {
                // A campaign's start is keyed by its session, a novel's by the book.
                startSlots.markPendingTemplate(if (vocabulary == CreateWorkVocabulary.Campaign) chatId ?: bookId else bookId)
                onCreated(bookId, chatId)
            }
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

    /**
     * Opens a completed offline chapter directly in the editable storyboard
     * canvas.  These sessions use a private work type so Projects continues
     * to mean user-created or explicitly AI-created projects.
     */
    fun createMangaEditorFromChapter(
        chapterId: String,
        focusPageIndex: Int? = null,
        onCreated: (bookId: String, chatId: String, pageId: String?) -> Unit,
        onFailure: (String) -> Unit = {},
    ) {
        viewModelScope.launch {
            runCatching {
                val chapter = db.mangaDao().getChapter(chapterId)
                    ?: error("Downloaded chapter not found")
                require(chapter.status == "completed") { "Finish downloading the chapter before editing it" }
                // Reopen the durable derived copy for this chapter instead of
                // creating another hidden edit document every time the reader
                // taps Translate/Color/Edit.
                val existing = db.roleplayDao().getChats().firstOrNull { chat ->
                    chat.displayMode == "roleplay" &&
                        decodePages(chat.pagesJson).any { it.sourceChapterId == chapterId }
                }
                if (existing != null && existing.bookId != null) {
                    settings.setSelectedBookId(existing.bookId)
                    val pageIds = decodePages(existing.pagesJson)
                        .filter { it.sourceChapterId == chapterId }
                        .sortedBy { it.order }
                        .map { it.id }
                    return@runCatching Triple(
                        existing.bookId,
                        existing.id,
                        focusPageIndex?.let(pageIds::getOrNull) ?: pageIds.firstOrNull(),
                    )
                }
                val book = bookRepository.createBook(
                    title = "${chapter.mangaTitle} · ${chapter.title}",
                    genre = "Manga edit",
                    pov = chapter.readingOrder,
                    tense = "Comic",
                    styleGuide = "Imported offline chapter editor",
                    workType = "manga_edit",
                )
                settings.setSelectedBookId(book.id)
                val now = System.currentTimeMillis()
                val chatId = "rp-chat-${java.util.UUID.randomUUID()}"
                db.roleplayDao().upsertChat(
                    RpChatEntity(
                        id = chatId,
                        characterId = null,
                        personaId = "persona-default",
                        title = "${chapter.mangaTitle} · ${chapter.title}",
                        displayMode = "roleplay",
                        pagesJson = encodePages(emptyList()),
                        createdAt = now,
                        updatedAt = now,
                        bookId = book.id,
                    ),
                )
                val imported = mangaDownloadRepository.importChapterToStoryboardResult(chatId, chapterId)
                Triple(book.id, chatId, focusPageIndex?.let(imported.pageIds::getOrNull))
            }.onSuccess { (bookId, chatId, pageId) ->
                onCreated(bookId, chatId, pageId)
            }.onFailure { error ->
                onFailure(error.message ?: "Could not open the downloaded chapter for editing")
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
            if (!textGame) {
                // Persist the stable mode id separately from the human-readable
                // style-guide text so reopening Adventure never falls back to d20.
                appendLine("Game mode: ${RpgCombatRuleset.fromId(details.gameModeId).id}")
                appendLine("Combat style: ${RpgCombatRuleset.fromId(details.gameModeId).id}")
                appendLine("Setting details preset: ${details.settingDetailId.ifBlank { "custom" }}")
                appendLine("House rules preset: ${details.houseRuleId.ifBlank { "custom" }}")
            }
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
        val modeId = RpgCombatRuleset.fromId(details.gameModeId).id
        val ruleLabel = CampaignRulesetTemplates.firstOrNull { it.id == details.rulesetId }?.label ?: "Custom / systemless"
        RpgCampaignRepository(db.roleplayDao()).saveRpgCampaign(
            createRpgCampaign(id, modeId, details.rulesetId).copy(
                startup = RpgStartupState(
                    setup = RpgCampaignSetupSnapshot(
                        title = details.title,
                        setting = details.genre.ifBlank { "Open fantasy setting" },
                        modeId = modeId,
                        ruleSystem = ruleLabel,
                        houseRules = details.styleGuide,
                        characters = mainCharacters,
                        pointOfView = details.narrativePov.ifBlank { "Third-person multiple" },
                        tense = details.tense.ifBlank { "Past tense" },
                        playerRole = if (userIsDungeonMaster) "Dungeon Master" else "Adventurer",
                    ),
                ),
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
            extensionsJson = encodeRpgSheet(
                "{}",
                createRpgCharacterSheet(name = persona.name, description = persona.description),
            ),
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
