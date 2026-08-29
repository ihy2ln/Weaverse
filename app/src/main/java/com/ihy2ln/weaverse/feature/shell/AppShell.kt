package com.ihy2ln.weaverse.feature.shell

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.ihy2ln.weaverse.core.ui.LocalPromptShortcutHandler
import com.ihy2ln.weaverse.core.ui.PromptShortcutKind
import com.ihy2ln.weaverse.core.ui.components.CreateWorkDialog
import com.ihy2ln.weaverse.core.ui.components.CreateWorkVocabulary
import com.ihy2ln.weaverse.core.ui.components.InkSegmentedPill
import com.ihy2ln.weaverse.core.ui.components.InkTextButton
import com.ihy2ln.weaverse.core.ui.components.SegmentedOption
import com.ihy2ln.weaverse.core.ui.components.WorkspaceChrome
import com.ihy2ln.weaverse.core.ui.components.VerticalResizeHandle
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkTokens
import com.ihy2ln.weaverse.core.ui.util.resolveSectionColor
import com.ihy2ln.weaverse.feature.export.ExportImportScreen
import com.ihy2ln.weaverse.feature.library.LibraryScreen
import com.ihy2ln.weaverse.feature.library.WorkShelfKind
import com.ihy2ln.weaverse.feature.library.WorkShelfScreen
import com.ihy2ln.weaverse.feature.media.MediaGalleryScreen
import com.ihy2ln.weaverse.feature.media.PicturesRailScreen
import com.ihy2ln.weaverse.feature.notes.NotesRailScreen
import com.ihy2ln.weaverse.feature.notes.NotesScreen
import com.ihy2ln.weaverse.feature.notes.NotesViewModel
import com.ihy2ln.weaverse.feature.novel.chat.WorkshopChatScreen
import com.ihy2ln.weaverse.feature.prompt.GlobalPromptOverlay
import com.ihy2ln.weaverse.feature.prompt.GlobalPromptViewModel
import com.ihy2ln.weaverse.feature.prompt.PromptEntryKind
import com.ihy2ln.weaverse.feature.prompt.PromptInsertContext
import com.ihy2ln.weaverse.feature.novel.chat.WorkshopThreadsRail
import com.ihy2ln.weaverse.feature.novel.codex.CodexEntryDetailScreen
import com.ihy2ln.weaverse.feature.novel.codex.CodexRailScreen
import com.ihy2ln.weaverse.feature.novel.codex.CodexViewModel
import com.ihy2ln.weaverse.feature.novel.manuscript.ManuscriptRailScreen
import com.ihy2ln.weaverse.feature.novel.plan.PlanScreen
import com.ihy2ln.weaverse.feature.novel.plan.PlanVocabulary
import com.ihy2ln.weaverse.feature.novel.read.ReaderScreen
import com.ihy2ln.weaverse.feature.novel.review.ReviewScreen
import com.ihy2ln.weaverse.feature.novel.snippets.SnippetsRailScreen
import com.ihy2ln.weaverse.feature.novel.write.WriteScreen
import com.ihy2ln.weaverse.feature.prompts.PromptsScreen
import com.ihy2ln.weaverse.feature.roleplay.characters.CharacterDetailScreen
import com.ihy2ln.weaverse.feature.roleplay.chat.AdventurePlayScreen
import com.ihy2ln.weaverse.feature.roleplay.chat.RoleplayChatChrome
import com.ihy2ln.weaverse.feature.roleplay.chat.RoleplayChatDetailScreen
import com.ihy2ln.weaverse.feature.roleplay.chat.RoleplayChatsScreen
import com.ihy2ln.weaverse.feature.roleplay.chat.roleplayModeSubtitle
import com.ihy2ln.weaverse.feature.roleplay.friends.FriendsScreen
import com.ihy2ln.weaverse.feature.roleplay.lorebook.LorebookScreen
import com.ihy2ln.weaverse.feature.roleplay.personas.PersonaDetailScreen
import com.ihy2ln.weaverse.feature.roleplay.party.InventoryScreen
import com.ihy2ln.weaverse.feature.roleplay.party.PartyScreen
import com.ihy2ln.weaverse.feature.roleplay.town.TownScreen
import com.ihy2ln.weaverse.feature.roleplay.personas.PersonasScreen
import com.ihy2ln.weaverse.feature.roleplay.presets.PresetsScreen
import com.ihy2ln.weaverse.feature.search.GlobalSearchScreen
import com.ihy2ln.weaverse.feature.search.SearchResultType
import com.ihy2ln.weaverse.feature.settings.SettingsScreen
import java.io.File

@Composable
fun AppShell(
    modifier: Modifier = Modifier,
    shellViewModel: AppShellViewModel = hiltViewModel(),
    notesViewModel: NotesViewModel = hiltViewModel(),
    codexViewModel: CodexViewModel = hiltViewModel(),
    promptViewModel: GlobalPromptViewModel = hiltViewModel(),
) {
    var mode by rememberSaveable { mutableStateOf(AppMode.Novel.name) }
    var novelDest by rememberSaveable { mutableStateOf(NovelDestination.Bookshelf.name) }
    var rpDest by rememberSaveable { mutableStateOf(RoleplayDestination.Campaign.name) }
    var chatDest by rememberSaveable { mutableStateOf(ChattingDestination.Chats.name) }
    var storyboardDest by rememberSaveable { mutableStateOf(StoryboardDestination.Window.name) }
    var storyboardChatId by rememberSaveable { mutableStateOf<String?>(null) }
    var creatingWork by remember { mutableStateOf<CreateWorkVocabulary?>(null) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var showLibrary by rememberSaveable { mutableStateOf(false) }
    var showExport by rememberSaveable { mutableStateOf(false) }
    var workspaceFocus by rememberSaveable { mutableStateOf(WorkspaceFocus.Story.name) }
    var chromeTool by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedRpChatId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedCodexEntryId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedCharacterId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedPersonaId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedInventoryCarrierId by rememberSaveable { mutableStateOf<String?>(null) }
    var notesDetailOpen by rememberSaveable { mutableStateOf(false) }
    var codexPanelHeightDp by rememberSaveable { mutableStateOf(48f) }
    var lastExpandedCodexPanelHeightDp by rememberSaveable { mutableStateOf(240f) }
    var selectedSceneId by rememberSaveable { mutableStateOf("scene-1") }
    var writeJumpKind by rememberSaveable { mutableStateOf(WriteJumpKind.Scene.name) }
    var selectedThreadId by rememberSaveable { mutableStateOf("thread-1") }
    var rpChrome by remember { mutableStateOf<RoleplayChatChrome?>(null) }
    var rpModeBarCollapsed by rememberSaveable { mutableStateOf(false) }

    val prefs by shellViewModel.preferences.collectAsState(
        initial = com.ihy2ln.weaverse.data.settings.UserPreferences(),
    )
    val historyState by shellViewModel.historyState.collectAsState(
        initial = WorkspaceHistoryState(),
    )
    val shellInfo by shellViewModel.shellInfo.collectAsState()
    val campaignCharacterOptions by shellViewModel.campaignCharacterOptions.collectAsState()
    val notesState by notesViewModel.uiState.collectAsState()
    val codexState by codexViewModel.uiState.collectAsState()
    val promptUi by promptViewModel.uiState.collectAsState()
    val promptOverlayOpen = promptUi.kind != null

    fun openGlobalPromptIfNeeded(kind: PromptEntryKind): Boolean {
        shellViewModel.openPrompt(kind)
        return true
    }
    creatingWork?.let { vocabulary ->
        CreateWorkDialog(
            vocabulary = vocabulary,
            characterOptions = if (vocabulary == CreateWorkVocabulary.Campaign) {
                campaignCharacterOptions
            } else {
                emptyList()
            },
            onDismiss = { creatingWork = null },
            onCreate = { details ->
                // Campaigns and storyboards are both manuscripts underneath, so one
                // path creates all three and only the landing screen differs.
                shellViewModel.createWork(vocabulary, details) { bookId, chatId ->
                    showLibrary = false
                    when (vocabulary) {
                        CreateWorkVocabulary.Storyboard -> {
                            mode = AppMode.Storyboard.name
                            storyboardChatId = chatId
                            storyboardDest = if (
                                details.tense.equals("Comic", true) ||
                                details.tense.equals("Webtoon", true) ||
                                details.pov.equals("Left to right", true)
                            ) StoryboardDestination.Comic.name else StoryboardDestination.Manga.name
                        }
                        CreateWorkVocabulary.Campaign -> {
                            mode = AppMode.Roleplay.name
                            rpDest = RoleplayDestination.Chats.name
                            selectedRpChatId = chatId
                        }
                        else -> {
                            mode = AppMode.Novel.name
                            novelDest = NovelDestination.Plan.name
                        }
                    }
                }
            },
        )
    }

    val tokens = inkTokens()
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.screenHeightDp > configuration.screenWidthDp
    val maxCodexPanelHeightDp = (configuration.screenHeightDp * 0.45f).coerceIn(180f, 480f)
    val bgColor = resolveSectionColor(prefs.appearance.chrome, tokens.background)
    val railColor = resolveSectionColor(prefs.appearance.rail, tokens.panel)
    val contentColor = resolveSectionColor(prefs.appearance.content, tokens.background)
    val brightnessDim = 1f - (prefs.appBrightnessPercent.coerceIn(5, 100) / 100f)

    val bookTitle = shellInfo.book?.title ?: "Weaverse"
    val seriesTitle = shellInfo.series?.title ?: "Library"
    val inRpChat = (selectedRpChatId != null || storyboardChatId != null) && rpChrome != null
    val inNotes = mode == AppMode.Notes.name
    val toolbarTitle = when {
        inRpChat -> rpChrome!!.title
        inNotes -> "Notes"
        else -> bookTitle
    }
    val toolbarSubtitle = when {
        inRpChat -> roleplayModeSubtitle(rpChrome!!.displayMode)
        inNotes -> "Shared notes · every book & mode"
        else -> "$seriesTitle · Codex & Prompts stay shared"
    }

    val shellFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { shellFocus.requestFocus() }
    }
    CompositionLocalProvider(
        LocalPromptShortcutHandler provides { shortcut ->
            shellViewModel.openPrompt(
                when (shortcut) {
                    PromptShortcutKind.Ai -> PromptEntryKind.Ai
                    PromptShortcutKind.Manual -> PromptEntryKind.Manual
                },
            )
        },
    ) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .focusRequester(shellFocus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Slash -> openGlobalPromptIfNeeded(PromptEntryKind.Ai)
                    Key.Backslash -> openGlobalPromptIfNeeded(PromptEntryKind.Manual)
                    else -> false
                }
            },
    ) {
        shellInfo.backgroundPath?.let { path ->
            AsyncImage(
                model = File(path),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = 1f,
            )
        }
        Column(modifier = Modifier.fillMaxSize().background(bgColor)) {
            val currentMode = runCatching { AppMode.valueOf(mode) }.getOrDefault(AppMode.Novel)
            val defaultModeOptions = when (currentMode) {
                AppMode.Novel -> NovelDestination.entries.map { SegmentedOption(it.name, it.label) }
                AppMode.Roleplay -> RoleplayDestination.entries.map { SegmentedOption(it.name, it.label) }
                AppMode.Chatting -> ChattingDestination.entries.map { SegmentedOption(it.name, it.label) }
                AppMode.Storyboard -> StoryboardDestination.entries.map { SegmentedOption(it.name, it.label) }
                AppMode.Notes -> NotesDestination.entries.map { SegmentedOption(it.name, it.label) }
            }
            val savedModeOrder = when (currentMode) {
                AppMode.Novel -> prefs.navigationOrder.novel
                AppMode.Roleplay -> prefs.navigationOrder.roleplay
                AppMode.Chatting -> prefs.navigationOrder.chatting
                AppMode.Storyboard -> prefs.navigationOrder.storyboard
                AppMode.Notes -> prefs.navigationOrder.notes
            }
            val modeOptions = applySavedOrder(defaultModeOptions, savedModeOrder) { it.id }
            val workspaceOptions = applySavedOrder(
                listOf(
                    SegmentedOption(AppMode.Novel.name, AppMode.Novel.label),
                    SegmentedOption(AppMode.Roleplay.name, AppMode.Roleplay.label),
                    SegmentedOption(AppMode.Chatting.name, AppMode.Chatting.label),
                    SegmentedOption(AppMode.Storyboard.name, AppMode.Storyboard.label),
                    SegmentedOption(AppMode.Notes.name, AppMode.Notes.label),
                ),
                prefs.navigationOrder.workspaces,
            ) { it.id }
            val modeId = when (currentMode) {
                AppMode.Novel -> novelDest
                AppMode.Roleplay -> rpDest
                AppMode.Chatting -> chatDest
                AppMode.Storyboard -> storyboardDest
                AppMode.Notes -> NotesDestination.Board.name
            }
            val chromeTitle = when {
                showSettings -> "Settings"
                showExport -> "Import / Export"
                showSearch -> "Search"
                showLibrary -> "Library"
                selectedCodexEntryId != null -> "Codex"
                selectedCharacterId != null -> "Character"
                selectedPersonaId != null -> "Persona"
                else -> toolbarTitle
            }
            val chromeSubtitle = when {
                showLibrary -> listOf(bookTitle, seriesTitle).filter { it.isNotBlank() }.distinct().joinToString(" · ")
                showSettings -> "Weaverse"
                showExport -> "Novels · Roleplay · Notes"
                showSearch -> "Weaverse"
                selectedCodexEntryId != null || chromeTool == RailTab.Codex.name ->
                    "Shared · ${codexState.entries.size} entries · every book & mode"
                else -> toolbarSubtitle
            }
            WorkspaceChrome(
                bookTitle = chromeTitle,
                seriesTitle = chromeSubtitle,
                workspaceOptions = workspaceOptions,
                workspaceId = mode,
                modeOptions = modeOptions,
                modeId = modeId,
                focusOptions = emptyList(),
                focusId = workspaceFocus,
                // "Extra" row: the app-wide tools that are not tied to one workspace.
                toolOptions = workspaceChromeTools().map { SegmentedOption(it.name, it.label) },
                activeToolId = chromeTool,
                onLibrary = {
                    if (showLibrary) {
                        if (shellInfo.book != null) showLibrary = false
                    } else {
                        showSettings = false
                        showExport = false
                        showSearch = false
                        selectedCodexEntryId = null
                        selectedCharacterId = null
                        selectedPersonaId = null
                        showLibrary = true
                    }
                },
                onSettings = { showSettings = !showSettings },
                onImport = { showExport = true },
                onExport = { showExport = true },
                canUndo = historyState.canUndo,
                canRedo = historyState.canRedo,
                onUndo = shellViewModel::undo,
                onRedo = shellViewModel::redo,
                onTool = { id ->
                    showLibrary = false
                    showSettings = false
                    showExport = false
                    showSearch = false
                    if (id == RailTab.Pictures.name) {
                        chromeTool = null
                        // Toggle, since the Focus chip that used to switch back is gone.
                        workspaceFocus = if (workspaceFocus == WorkspaceFocus.Pictures.name) {
                            WorkspaceFocus.Story.name
                        } else {
                            WorkspaceFocus.Pictures.name
                        }
                    } else {
                        chromeTool = id
                        if (id == RailTab.Notes.name) notesDetailOpen = false
                        if (id != null) workspaceFocus = WorkspaceFocus.Story.name
                    }
                },
                onWorkspace = { next ->
                    showLibrary = false
                    showSettings = false
                    showExport = false
                    showSearch = false
                    mode = next
                    chromeTool = null
                    selectedRpChatId = null
                    selectedCodexEntryId = null
                    selectedCharacterId = null
                    selectedPersonaId = null
                    notesDetailOpen = false
                    storyboardChatId = null
                    rpChrome = null
                    when (next) {
                        AppMode.Novel.name -> novelDest = NovelDestination.Bookshelf.name
                        AppMode.Roleplay.name -> rpDest = RoleplayDestination.Campaign.name
                        AppMode.Chatting.name -> chatDest = ChattingDestination.Chats.name
                        AppMode.Storyboard.name -> storyboardDest = StoryboardDestination.Window.name
                    }
                    if (next != AppMode.Notes.name) {
                        workspaceFocus = WorkspaceFocus.Story.name
                    }
                },
                onMode = { id ->
                    showLibrary = false
                    showSettings = false
                    chromeTool = null
                    workspaceFocus = WorkspaceFocus.Story.name
                    when (currentMode) {
                        AppMode.Novel -> novelDest = id
                        AppMode.Roleplay -> {
                            rpDest = id
                            selectedRpChatId = null
                            rpChrome = null
                        }
                        AppMode.Chatting -> {
                            chatDest = id
                            selectedRpChatId = null
                            rpChrome = null
                        }
                        AppMode.Storyboard -> {
                            storyboardDest = id
                            if (id == StoryboardDestination.Window.name) storyboardChatId = null
                        }
                        AppMode.Notes -> notesDetailOpen = false
                    }
                },
                onFocus = { workspaceFocus = it; chromeTool = null },
                onWorkspaceOrderChange = shellViewModel::setWorkspaceButtonOrder,
                onModeOrderChange = { shellViewModel.setModeButtonOrder(currentMode, it) },
            )
            when {
                showSettings -> SettingsScreen(modifier = Modifier.weight(1f).fillMaxSize())
                showExport -> ExportImportScreen(modifier = Modifier.weight(1f).fillMaxSize())
                showSearch -> GlobalSearchScreen(
                    onResultClick = { result ->
                        showSearch = false
                        showLibrary = false
                        when (result.type) {
                            SearchResultType.Scene -> {
                                mode = AppMode.Novel.name
                                selectedSceneId = result.id
                                novelDest = NovelDestination.Write.name
                            }
                            SearchResultType.Codex -> {
                                selectedCodexEntryId = result.id
                                mode = AppMode.Novel.name
                            }
                            SearchResultType.WorkshopChat -> {
                                mode = AppMode.Novel.name
                                selectedThreadId = result.id
                                novelDest = NovelDestination.Chat.name
                            }
                            SearchResultType.RoleplayChat -> {
                                mode = AppMode.Roleplay.name
                                selectedRpChatId = result.id
                                rpDest = RoleplayDestination.Chats.name
                            }
                            SearchResultType.Snippet -> mode = AppMode.Novel.name
                        }
                    },
                    modifier = Modifier.weight(1f).fillMaxSize(),
                )
                showLibrary -> LibraryScreen(
                    onOpenMode = { modeId ->
                        showLibrary = false
                        mode = modeId
                        chromeTool = null
                    },
                    onOpenBook = { _, sceneId ->
                        if (sceneId != null) selectedSceneId = sceneId
                        showLibrary = false
                        novelDest = NovelDestination.Plan.name
                        mode = AppMode.Novel.name
                    },
                    onWriteBook = { _, sceneId ->
                        if (sceneId != null) selectedSceneId = sceneId
                        writeJumpKind = WriteJumpKind.Scene.name
                        showLibrary = false
                        novelDest = NovelDestination.Write.name
                        mode = AppMode.Novel.name
                    },
                    onReadBook = { _, sceneId ->
                        if (sceneId != null) selectedSceneId = sceneId
                        showLibrary = false
                        novelDest = NovelDestination.Read.name
                        mode = AppMode.Novel.name
                    },
                    onOpenExport = { showExport = true },
                    modifier = Modifier.weight(1f).fillMaxSize(),
                )
                selectedCodexEntryId != null -> Column(Modifier.weight(1f).fillMaxSize()) {
                    val codexPanelExpanded = codexPanelHeightDp > 72f
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height((codexPanelHeightDp + 12f).dp),
                    ) {
                        CodexRailScreen(
                            viewModel = codexViewModel,
                            onEntryClick = { selectedCodexEntryId = it },
                            selectedEntryId = selectedCodexEntryId,
                            compact = !codexPanelExpanded,
                            showSharedSummary = false,
                            modifier = Modifier.fillMaxWidth().weight(1f),
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(12.dp)
                                .background(inkTokens().panel)
                                .pointerInput(maxCodexPanelHeightDp) {
                                    detectVerticalDragGestures { change, dragAmount ->
                                        change.consume()
                                        val next = (codexPanelHeightDp + dragAmount / density.density)
                                            .coerceIn(48f, maxCodexPanelHeightDp)
                                        codexPanelHeightDp = next
                                        if (next > 72f) lastExpandedCodexPanelHeightDp = next
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(46.dp)
                                    .height(4.dp)
                                    .background(
                                        inkTokens().secondaryText.copy(alpha = 0.55f),
                                        RoundedCornerShape(2.dp),
                                    ),
                            )
                        }
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        CodexEntryDetailScreen(
                            entryId = selectedCodexEntryId!!,
                            codexPanelExpanded = codexPanelExpanded,
                            onToggleCodexPanel = {
                                if (codexPanelExpanded) {
                                    lastExpandedCodexPanelHeightDp = codexPanelHeightDp
                                    codexPanelHeightDp = 48f
                                } else {
                                    codexPanelHeightDp = lastExpandedCodexPanelHeightDp
                                        .coerceIn(96f, maxCodexPanelHeightDp)
                                }
                            },
                        )
                    }
                }
                selectedCharacterId != null -> Box(Modifier.weight(1f).fillMaxSize()) {
                    CharacterDetailScreen(characterId = selectedCharacterId!!, onBack = { selectedCharacterId = null })
                }
                selectedPersonaId != null -> Box(Modifier.weight(1f).fillMaxSize()) {
                    PersonaDetailScreen(personaId = selectedPersonaId!!, onBack = { selectedPersonaId = null })
                }
                else -> {
            if (inRpChat && rpChrome!!.showSwitcher) {
                RoleplayDisplayModeBar(
                    displayMode = rpChrome!!.displayMode,
                    onSelect = rpChrome!!.onDisplayMode,
                    collapsed = rpModeBarCollapsed,
                    onToggleCollapsed = { rpModeBarCollapsed = !rpModeBarCollapsed },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(modifier = Modifier.fillMaxSize()) {
                val hideSideRail = isPortrait || chromeTool != null
                // Portrait must use railPortraitMin…Max — coerceIn(railMin, 220.dp) throws
                // IllegalArgumentException (240 > 220) and crashes S25 portrait navigation.
                val targetRailWidth = when {
                    hideSideRail -> 0.dp
                    prefs.layout.railCollapsed -> 40.dp
                    isPortrait -> prefs.layout.railWidthDp.dp.coerceIn(
                        InkSpacing.railPortraitMin,
                        InkSpacing.railPortraitMax,
                    )
                    else -> prefs.layout.railWidthDp.dp.coerceIn(InkSpacing.railMin, InkSpacing.railMax)
                }
                val railWidth by animateDpAsState(
                    targetValue = targetRailWidth,
                    animationSpec = tween(durationMillis = 220),
                    label = "shellRailWidth",
                )
                if (railWidth > 0.dp) Row(modifier = Modifier.width(railWidth).fillMaxHeight()) {
                    if (prefs.layout.railCollapsed) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(railColor)
                                .clickable { shellViewModel.setRailCollapsed(false) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "Expand rail")
                        }
                    } else {
                        RailPanel(
                            mode = AppMode.valueOf(mode),
                            selectedThreadId = selectedThreadId,
                            onThreadClick = { selectedThreadId = it; novelDest = NovelDestination.Chat.name },
                            onSceneClick = { selectedSceneId = it; novelDest = NovelDestination.Write.name },
                            onCodexEntryClick = { selectedCodexEntryId = it },
                            onOpenPictures = { workspaceFocus = WorkspaceFocus.Pictures.name },
                            onCollapse = { shellViewModel.setRailCollapsed(true) },
                            notesViewModel = notesViewModel,
                            modifier = Modifier.weight(1f).fillMaxHeight().background(railColor),
                        )
                    }
                }
                if (!hideSideRail && !prefs.layout.railCollapsed) {
                    VerticalResizeHandle(
                        onDragDelta = { deltaPx ->
                            val deltaDp = with(density) { deltaPx.toDp().value }
                            shellViewModel.setRailWidthDp(prefs.layout.railWidthDp + deltaDp)
                        },
                    )
                }
                Crossfade(
                    // Every piece of state that selects a screen must be in this key,
                    // or switching workspace/destination will not recompose the content.
                    targetState = Triple(
                        mode to chromeTool,
                        workspaceFocus,
                        listOf(
                            novelDest, rpDest, chatDest, storyboardDest,
                            selectedRpChatId, storyboardChatId,
                        ),
                    ),
                    animationSpec = tween(durationMillis = 120),
                    label = "modeSwitch",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(contentColor),
                ) { (modeAndTool, focus, dests) ->
                    val (currentMode, tool) = modeAndTool
                    val nd = dests[0] ?: NovelDestination.Bookshelf.name
                    val rd = dests[1] ?: RoleplayDestination.Chats.name
                    val cd = dests[2] ?: ChattingDestination.Friends.name
                    val sd = dests[3] ?: StoryboardDestination.Manga.name
                    val chatId = dests[4]
                    val boardId = dests[5]
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (tool != null) {
                            when (runCatching { RailTab.valueOf(tool) }.getOrNull()) {
                                RailTab.Codex -> CodexRailScreen(
                                    viewModel = codexViewModel,
                                    onEntryClick = { selectedCodexEntryId = it },
                                    showSharedSummary = false,
                                    modifier = Modifier.fillMaxSize(),
                                )
                                RailTab.Prompts -> PromptsScreen(modifier = Modifier.fillMaxSize())
                                RailTab.Notes -> NotesWorkspaceScreen(
                                    viewModel = notesViewModel,
                                    detailOpen = notesDetailOpen,
                                    onDetailOpen = { notesDetailOpen = true },
                                    modifier = Modifier.fillMaxSize(),
                                )
                                RailTab.Snippets -> SnippetsRailScreen()
                                RailTab.Chats -> WorkshopThreadsRail(
                                    selectedThreadId = selectedThreadId,
                                    onThreadClick = { id ->
                                        selectedThreadId = id
                                        novelDest = NovelDestination.Chat.name
                                        chromeTool = null
                                    },
                                )
                                RailTab.Pictures -> MediaGalleryScreen(modifier = Modifier.fillMaxSize())
                                RailTab.Manuscript -> ManuscriptRailScreen(onSceneClick = {
                                    selectedSceneId = it
                                    novelDest = NovelDestination.Write.name
                                    chromeTool = null
                                })
                                null -> Unit
                            }
                            return@Crossfade
                        }
                        if (focus == WorkspaceFocus.Pictures.name) {
                            MediaGalleryScreen(modifier = Modifier.fillMaxSize())
                            return@Crossfade
                        }
                        when (currentMode) {
                            AppMode.Novel.name -> when (novelDestinationOf(nd)) {
                                NovelDestination.Bookshelf -> WorkShelfScreen(
                                    kind = WorkShelfKind.Novel,
                                    onCreate = { creatingWork = CreateWorkVocabulary.Novel },
                                    onExport = { bookId ->
                                        shellViewModel.setSelectedBookId(bookId)
                                        showExport = true
                                    },
                                    onOpen = { card ->
                                        card.bookId?.let(shellViewModel::setSelectedBookId)
                                        novelDest = NovelDestination.Plan.name
                                    },
                                )
                                NovelDestination.Plan -> PlanScreen(
                                    onWrite = { sceneId, kind ->
                                        selectedSceneId = sceneId
                                        writeJumpKind = kind.name
                                        novelDest = NovelDestination.Write.name
                                        chromeTool = null
                                    },
                                )
                                NovelDestination.Write -> WriteScreen(
                                    sceneId = selectedSceneId,
                                    jumpKind = writeJumpKind,
                                    onOpenCodexEntry = { selectedCodexEntryId = it },
                                )
                                NovelDestination.Read -> ReaderScreen()
                                NovelDestination.Chat -> WorkshopChatScreen(threadId = selectedThreadId)
                                NovelDestination.Review -> ReviewScreen()
                            }
                            AppMode.Notes.name -> NotesWorkspaceScreen(
                                viewModel = notesViewModel,
                                detailOpen = notesDetailOpen,
                                onDetailOpen = { notesDetailOpen = true },
                                modifier = Modifier.fillMaxSize(),
                            )
                            AppMode.Chatting.name -> when (chattingDestinationOf(cd)) {
                                ChattingDestination.Friends -> FriendsScreen(
                                    onOpenChat = {
                                        selectedRpChatId = it
                                        chatDest = ChattingDestination.Chats.name
                                    },
                                )
                                ChattingDestination.Chats -> {
                                    if (chatId != null) {
                                        RoleplayChatDetailScreen(
                                            chatId = chatId,
                                            onBack = {
                                                selectedRpChatId = null
                                                rpChrome = null
                                            },
                                            onChromeChange = { rpChrome = it },
                                            onOpenAiPrompt = { shellViewModel.openPrompt(PromptEntryKind.Ai) },
                                            onOpenManualPrompt = { shellViewModel.openPrompt(PromptEntryKind.Manual) },
                                            promptOverlayOpen = promptOverlayOpen,
                                            // Chatting owns the messenger view only.
                                            forceDisplayMode = "messenger",
                                            showModeSwitcher = false,
                                        )
                                    } else {
                                        RoleplayChatsScreen(
                                            onChatClick = { selectedRpChatId = it },
                                            showFilters = true,
                                            onNewChat = { chatDest = ChattingDestination.Friends.name },
                                        )
                                    }
                                }
                            }
                            AppMode.Storyboard.name -> {
                                if (storyboardDestinationOf(sd) == StoryboardDestination.Window) {
                                    WorkShelfScreen(
                                        kind = WorkShelfKind.Storyboard,
                                        onCreate = { creatingWork = CreateWorkVocabulary.Storyboard },
                                        onOpen = { card ->
                                            card.bookId?.let(shellViewModel::setSelectedBookId)
                                            storyboardChatId = card.chatId
                                            storyboardDest = card.preferredStoryboardMode
                                        },
                                    )
                                } else if (boardId != null) {
                                    RoleplayChatDetailScreen(
                                        chatId = boardId,
                                        onBack = {
                                            storyboardChatId = null
                                            rpChrome = null
                                            storyboardDest = StoryboardDestination.Window.name
                                        },
                                        onChromeChange = { rpChrome = it },
                                        onOpenAiPrompt = { shellViewModel.openPrompt(PromptEntryKind.Ai) },
                                        onOpenManualPrompt = { shellViewModel.openPrompt(PromptEntryKind.Manual) },
                                        promptOverlayOpen = promptOverlayOpen,
                                        // Storyboard is always the comic canvas.
                                        forceDisplayMode = "roleplay",
                                        showModeSwitcher = false,
                                        rightToLeft = storyboardDestinationOf(sd) == StoryboardDestination.Manga,
                                    )
                                } else {
                                    WorkShelfScreen(
                                        kind = WorkShelfKind.Storyboard,
                                        onCreate = { creatingWork = CreateWorkVocabulary.Storyboard },
                                        onOpen = { card -> storyboardChatId = card.chatId },
                                    )
                                }
                            }
                            else -> when (roleplayDestinationOf(rd)) {
                                RoleplayDestination.Chats -> {
                                    if (chatId != null) {
                                        AdventurePlayScreen(
                                            chatId = chatId,
                                            onChromeChange = { rpChrome = it },
                                        )
                                    } else {
                                        WorkShelfScreen(
                                            kind = WorkShelfKind.Campaign,
                                            onCreate = { creatingWork = CreateWorkVocabulary.Campaign },
                                            onOpen = { card ->
                                                card.bookId?.let { bookId ->
                                                    shellViewModel.openCampaign(bookId) { sessionId ->
                                                        selectedRpChatId = sessionId
                                                    }
                                                }
                                            },
                                        )
                                    }
                                }
                                RoleplayDestination.Campaign -> WorkShelfScreen(
                                    kind = WorkShelfKind.Campaign,
                                    onCreate = { creatingWork = CreateWorkVocabulary.Campaign },
                                    onOpen = { card ->
                                        card.bookId?.let { bookId ->
                                            shellViewModel.openCampaign(bookId) { sessionId ->
                                                selectedRpChatId = sessionId
                                                rpDest = RoleplayDestination.Chats.name
                                            }
                                        }
                                    },
                                )
                                RoleplayDestination.Characters -> PartyScreen(
                                    onOpenPersona = { selectedPersonaId = it },
                                    onOpenCharacter = { selectedCharacterId = it },
                                    onOpenInventory = { carrierId ->
                                        selectedInventoryCarrierId = carrierId
                                        rpDest = RoleplayDestination.Inventory.name
                                    },
                                )
                                RoleplayDestination.Town -> TownScreen()
                                RoleplayDestination.Inventory -> InventoryScreen(
                                    initialCarrierId = selectedInventoryCarrierId,
                                )
                                RoleplayDestination.Codex -> LorebookScreen(
                                    onEntryClick = { selectedCodexEntryId = it },
                                )
                                RoleplayDestination.Presets -> PresetsScreen()
                            }
                        }
                    }
                }
                }
            }
        }
        }
        if (brightnessDim > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = brightnessDim)),
            )
        }
        val activeWritingDestination = when (runCatching { AppMode.valueOf(mode) }.getOrDefault(AppMode.Novel)) {
            AppMode.Novel -> novelDestinationOf(novelDest) == NovelDestination.Write
            AppMode.Roleplay -> roleplayDestinationOf(rpDest) == RoleplayDestination.Chats &&
                selectedRpChatId != null
            AppMode.Chatting -> chattingDestinationOf(chatDest) == ChattingDestination.Chats &&
                selectedRpChatId != null
            AppMode.Storyboard -> storyboardDestinationOf(storyboardDest) != StoryboardDestination.Window &&
                storyboardChatId != null
            AppMode.Notes -> false
        }
        GlobalPromptOverlay(
            context = PromptInsertContext(
                mode = runCatching { AppMode.valueOf(mode) }.getOrDefault(AppMode.Novel),
                sceneId = selectedSceneId,
                rpChatId = selectedRpChatId,
                noteId = notesState.selectedId,
                bookId = shellInfo.book?.id.orEmpty(),
                workshopThreadId = selectedThreadId,
                novelDest = novelDest,
            ),
            novelDest = novelDest,
            active = activeWritingDestination &&
                !(mode == AppMode.Roleplay.name && rpChrome?.displayMode == "dungeonMaster") &&
                chromeTool == null &&
                workspaceFocus != WorkspaceFocus.Pictures.name &&
                selectedCodexEntryId == null &&
                selectedCharacterId == null &&
                selectedPersonaId == null &&
                !showLibrary && !showSettings && !showSearch && !showExport,
            modifier = Modifier.align(Alignment.BottomStart),
        )
    }
    }
}

@Composable
private fun NotesWorkspaceScreen(
    viewModel: NotesViewModel,
    detailOpen: Boolean,
    onDetailOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        if (detailOpen) {
            NotesRailScreen(
                viewModel = viewModel,
                compact = true,
                onNoteOpened = onDetailOpen,
                modifier = Modifier.fillMaxWidth().height(156.dp),
            )
            NotesScreen(
                viewModel = viewModel,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        } else {
            NotesRailScreen(
                viewModel = viewModel,
                onNoteOpened = onDetailOpen,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun RoleplayDisplayModeBar(
    displayMode: String,
    onSelect: (String) -> Unit,
    collapsed: Boolean,
    onToggleCollapsed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = InkSpacing.md, vertical = InkSpacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (collapsed) {
            // Must stay reachable: hiding this bar used to be permanent, which left
            // DM and the Roleplay storyboard with no way back.
            InkTextButton(
                label = "${roleplayModeSubtitle(displayMode)} ▾",
                onClick = onToggleCollapsed,
            )
        } else {
            InkSegmentedPill(
                options = listOf(
                    SegmentedOption("messenger", "Messenger"),
                    SegmentedOption("dungeonMaster", "DM"),
                    SegmentedOption("roleplay", "Storyboard"),
                ),
                selectedId = displayMode,
                onSelect = onSelect,
            )
            InkTextButton(
                label = "Hide",
                onClick = onToggleCollapsed,
                modifier = Modifier.padding(start = InkSpacing.sm),
            )
        }
    }
}

@Composable
private fun RailPanel(
    mode: AppMode,
    selectedThreadId: String,
    onThreadClick: (String) -> Unit,
    onSceneClick: (String) -> Unit,
    onCodexEntryClick: (String) -> Unit,
    onOpenPictures: () -> Unit,
    onCollapse: () -> Unit,
    notesViewModel: NotesViewModel,
    modifier: Modifier = Modifier,
) {
    var tab by rememberSaveable(mode.name) { mutableStateOf(defaultRailTab(mode).name) }
    val tabs = railTabsFor(mode)
    LaunchedEffect(mode) {
        if (tabs.none { it.name == tab }) {
            tab = tabs.first().name
        }
    }
    Column(modifier = modifier.padding(InkSpacing.sm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Nested in rail — no outer horizontalScroll; keep pills wrapping via weight only.
            InkSegmentedPill(
                options = tabs.map { SegmentedOption(it.name, it.label) },
                selectedId = tab,
                onSelect = { tab = it },
                modifier = Modifier.weight(1f),
                scrollable = true,
            )
            IconButton(onClick = onCollapse) {
                Icon(Icons.Default.ChevronLeft, contentDescription = "Collapse rail")
            }
        }
        Box(modifier = Modifier.weight(1f).padding(top = InkSpacing.md)) {
            when (runCatching { RailTab.valueOf(tab) }.getOrDefault(tabs.first())) {
                RailTab.Codex -> CodexRailScreen(onEntryClick = onCodexEntryClick, modifier = Modifier.fillMaxSize())
                RailTab.Prompts -> PromptsScreen(modifier = Modifier.fillMaxSize())
                RailTab.Snippets -> SnippetsRailScreen()
                RailTab.Manuscript -> ManuscriptRailScreen(onSceneClick = onSceneClick)
                RailTab.Chats -> WorkshopThreadsRail(selectedThreadId, onThreadClick)
                RailTab.Notes -> NotesRailScreen(
                    viewModel = notesViewModel,
                    modifier = Modifier.fillMaxSize(),
                )
                RailTab.Pictures -> PicturesRailScreen(
                    onOpenGallery = onOpenPictures,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
