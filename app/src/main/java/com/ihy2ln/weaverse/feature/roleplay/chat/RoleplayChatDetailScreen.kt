package com.ihy2ln.weaverse.feature.roleplay.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ihy2ln.weaverse.core.text.MediaGrid
import com.ihy2ln.weaverse.core.text.PanelTemplates
import com.ihy2ln.weaverse.core.text.PanelTemplate
import com.ihy2ln.weaverse.core.text.StoryboardGridItem
import com.ihy2ln.weaverse.core.text.isStoryboardSlotOccupied
import com.ihy2ln.weaverse.data.db.entities.RpPageMeta
import com.ihy2ln.weaverse.core.ui.components.CollapsibleUsageStrip
import com.ihy2ln.weaverse.core.ui.components.EditTextAction
import com.ihy2ln.weaverse.core.ui.components.EditTextPopup
import com.ihy2ln.weaverse.core.ui.components.EditTextPopupConfig
import com.ihy2ln.weaverse.core.ui.components.InkTextButton
import com.ihy2ln.weaverse.core.ui.components.AudioMediaPlayer
import com.ihy2ln.weaverse.core.ui.components.MediaEditAction
import com.ihy2ln.weaverse.core.ui.components.MediaEditPopup
import com.ihy2ln.weaverse.core.ui.components.MediaEditPopupConfig
import com.ihy2ln.weaverse.core.ui.components.TextOverlayEditSheet
import com.ihy2ln.weaverse.core.ui.components.TextOverlayLayer
import com.ihy2ln.weaverse.feature.roleplay.friends.CharacterAvatar
import com.ihy2ln.weaverse.core.ui.components.VoiceToTextField
import com.ihy2ln.weaverse.core.ui.components.ZoomableMedia
import com.ihy2ln.weaverse.core.ui.components.mergeSpokenText
import com.ihy2ln.weaverse.feature.prompt.UnifiedPromptBar
import com.ihy2ln.weaverse.feature.prompt.PromptModelPickerDialog
import com.ihy2ln.weaverse.feature.prompt.PromptModelSelection
import com.ihy2ln.weaverse.feature.prompt.PromptWordLimit
import com.ihy2ln.weaverse.core.ui.components.rememberSpeechToText
import com.ihy2ln.weaverse.core.ui.theme.inkRadiusMd
import com.ihy2ln.weaverse.core.ui.theme.inkRadiusSm
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkTokens
import com.ihy2ln.weaverse.core.ui.util.parseHexColor
import com.ihy2ln.weaverse.core.ui.util.ScrollGutterBackdrop
import com.ihy2ln.weaverse.core.ui.util.alwaysScrollEndSpacer
import com.ihy2ln.weaverse.core.ui.util.scrollGutterPadding
import com.ihy2ln.weaverse.feature.storyboard.MangaSourceDialog
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RoleplayChatDetailScreen(
    chatId: String,
    onBack: () -> Unit,
    onChromeChange: (RoleplayChatChrome?) -> Unit = {},
    onOpenAiPrompt: () -> Unit = {},
    onOpenManualPrompt: () -> Unit = {},
    promptOverlayOpen: Boolean = false,
    /** Workspaces that own one view (Storyboard, RPG) pin the chat to that mode. */
    forceDisplayMode: String? = null,
    /** Hidden when the surrounding workspace already decides the mode. */
    showModeSwitcher: Boolean = true,
    /** Manga pages read right-to-left, so page tabs run that way too. */
    rightToLeft: Boolean = false,
    /** Imported manga opens in a focused editor instead of the general prompt workspace. */
    editorOnly: Boolean = false,
    /** Page-first creator chrome used by Storyboard projects. */
    storyboardCreator: Boolean = false,
    initialPageId: String? = null,
    initialEditorAction: String? = null,
    initialMangaChapterId: String? = null,
    viewModel: RoleplayChatViewModel = hiltViewModel(),
) {
    LaunchedEffect(chatId) { viewModel.bindChat(chatId) }
    LaunchedEffect(chatId, forceDisplayMode) {
        if (forceDisplayMode != null) viewModel.setDisplayMode(forceDisplayMode)
    }
    val state by viewModel.uiState.collectAsState()
    val clipboard = LocalClipboardManager.current
    val tokens = inkTokens()
    var popupMessageId by remember { mutableStateOf<String?>(null) }
    var editingMessageId by remember { mutableStateOf<String?>(null) }
    var editDraft by remember { mutableStateOf("") }
    val startDictateNew = rememberSpeechToText { spoken ->
        viewModel.insertUserText(spoken)
    }
    val startDictateEdit = rememberSpeechToText { spoken ->
        editDraft = mergeSpokenText(editDraft, spoken)
    }
    var promptCollapsed by rememberSaveable { mutableStateOf(false) }
    var modelsOpen by remember { mutableStateOf(false) }
    var modelSearch by rememberSaveable { mutableStateOf("") }
    var minimumWordsText by rememberSaveable { mutableStateOf(state.minimumOutputWords.toString()) }
    var maximumWordsText by rememberSaveable { mutableStateOf(state.outputWords.toString()) }
    LaunchedEffect(state.minimumOutputWords) {
        if (minimumWordsText.toIntOrNull() != state.minimumOutputWords) {
            minimumWordsText = state.minimumOutputWords.toString()
        }
    }
    LaunchedEffect(state.outputWords) {
        if (maximumWordsText.toIntOrNull() != state.outputWords) {
            maximumWordsText = state.outputWords.toString()
        }
    }
    val minWords = minimumWordsText.toIntOrNull()
    val maxWords = maximumWordsText.toIntOrNull()
    val wordRangeValid = minWords != null && maxWords != null &&
        minWords in PromptWordLimit.Minimum..PromptWordLimit.Maximum &&
        maxWords in PromptWordLimit.Minimum..PromptWordLimit.Maximum && minWords <= maxWords
    val listState = rememberLazyListState()
    val mediaFocus = remember { FocusRequester() }
    var selectedEmptySlotIndex by rememberSaveable(chatId) { mutableStateOf<Int?>(null) }
    var showGeneratedImportChoice by remember { mutableStateOf(false) }
    var generatedReplaceTargetKey by remember { mutableStateOf<String?>(null) }
    var showMangaSources by rememberSaveable(chatId) { mutableStateOf(false) }
    var initialEditorActionApplied by rememberSaveable(chatId) { mutableStateOf(false) }
    var storyboardTool by rememberSaveable(chatId) { mutableStateOf("Select") }
    var storyboardPreview by rememberSaveable(chatId) { mutableStateOf(false) }
    var storyboardPagesExpanded by rememberSaveable(chatId) { mutableStateOf(false) }

    LaunchedEffect(chatId, initialPageId, state.pages) {
        initialPageId?.let { pageId ->
            if (state.pages.any { it.id == pageId } && state.activePageId != pageId) {
                viewModel.switchPage(pageId)
            }
        }
    }
    LaunchedEffect(chatId, initialPageId, initialEditorAction, initialMangaChapterId, state.activePageId, state.pages, state.mediaPanels) {
        if (!editorOnly || initialEditorActionApplied || initialEditorAction == null) return@LaunchedEffect
        if (initialPageId != null && state.activePageId != initialPageId) return@LaunchedEffect
        when (initialEditorAction) {
            "TranslatePage" -> viewModel.translateActiveMangaPageToEnglish()
            "TranslateChapter" -> initialMangaChapterId?.let(viewModel::translateDownloadedChapter)
            "ColorPage" -> viewModel.colorizeActiveMangaPage()
            "ColorChapter" -> initialMangaChapterId?.let(viewModel::colorizeDownloadedChapter)
        }
        initialEditorActionApplied = true
    }

    LaunchedEffect(state.title, state.displayMode, showModeSwitcher) {
        onChromeChange(
            RoleplayChatChrome(
                title = state.title.ifBlank { "Chat" },
                displayMode = state.displayMode,
                onDisplayMode = viewModel::setDisplayMode,
                showSwitcher = showModeSwitcher,
            ),
        )
    }
    DisposableEffect(Unit) {
        onDispose { onChromeChange(null) }
    }

    val mediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.attachMedia(uris) else viewModel.clearMediaPickRequest()
    }

    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.attachMedia(uris)
    }

    // "Add pages" — each picked image becomes a whole storyboard page.
    val pagesPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.importPages(uris, rightToLeft = rightToLeft)
    }

    val generatedPanelPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importGeneratedPanels(
                uris = uris,
                selectedSlotIndex = selectedEmptySlotIndex,
                replaceTargetKey = generatedReplaceTargetKey,
            )
            selectedEmptySlotIndex = null
        }
        generatedReplaceTargetKey = null
    }

    LaunchedEffect(state.mediaPickRequestId) {
        if (state.mediaPickRequestId > 0L) {
            mediaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
            // Consume the request so returning to this screen never re-launches
            // the picker on its own.
            viewModel.clearMediaPickRequest()
        }
    }

    LaunchedEffect(state.audioPickRequestId) {
        if (state.audioPickRequestId > 0L) {
            audioPicker.launch(arrayOf("audio/*", "audio/mpeg", "audio/wav", "audio/x-wav"))
            viewModel.clearAudioPickRequest()
        }
    }

    LaunchedEffect(state.messages.size, state.streamingText, state.mediaPanels.size) {
        val last = state.messages.lastIndex
        if (last >= 0 && state.displayMode == "messenger") {
            runCatching { listState.animateScrollToItem(last) }
        }
    }

    LaunchedEffect(state.selectedMediaKey) {
        if (state.selectedMediaKey != null) {
            selectedEmptySlotIndex = null
            runCatching { mediaFocus.requestFocus() }
        }
    }
    LaunchedEffect(state.activePageId, state.activeTemplateId) {
        selectedEmptySlotIndex = null
    }

    val compactStyle = MaterialTheme.typography.bodySmall.copy(
        lineHeight = 18.sp,
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.None,
        ),
    )

    LaunchedEffect(state.displayMode, state.mediaPanels, state.messages.size) {
        when (state.displayMode) {
            "roleplay" -> viewModel.ensureMangaGridPlacement()
            "dungeonMaster" -> viewModel.ensureDmGridPlacement()
        }
    }

    if (editingMessageId != null) {
        AlertDialog(
            onDismissRequest = { editingMessageId = null },
            title = { Text("Edit message") },
            text = {
                VoiceToTextField(
                    value = editDraft,
                    onValueChange = { editDraft = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 10,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.editMessage(editingMessageId!!, editDraft)
                        editingMessageId = null
                    },
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editingMessageId = null }) { Text("Cancel") }
            },
        )
    }

    state.editingOverlay?.let { (msgId, blockId, overlayId) ->
        val overlay = state.mediaPanels
            .find { it.messageId == msgId && it.blockId == blockId }
            ?.overlays
            ?.find { it.id == overlayId }
        if (overlay != null) {
            TextOverlayEditSheet(
                overlay = overlay,
                onDismiss = viewModel::closeOverlayEditor,
                onSave = { viewModel.saveTextOverlay(msgId, blockId, it) },
                onDelete = { viewModel.deleteTextOverlay(msgId, blockId, overlayId) },
            )
        }
    }

    state.imageEditor?.let { editor ->
        PanelImageEditor(
            editor = editor,
            onSave = viewModel::saveEditedPanel,
            onClose = viewModel::closeImageEditor,
            onFindText = viewModel::editorFindText,
            onSetLanguage = viewModel::editorSetLanguage,
            onApplyRegions = viewModel::applyTranslatedRegions,
        )
        return
    }

    if (state.showImageGen) {
        ImageGenDialog(
            state = state,
            onPrompt = viewModel::onImageGenPrompt,
            onModel = viewModel::selectImageGenModel,
            onGenerate = viewModel::generateImageMedia,
            onDismiss = viewModel::closeImageGen,
        )
    }


    if (showGeneratedImportChoice) {
        AlertDialog(
            onDismissRequest = { showGeneratedImportChoice = false },
            title = { Text("Selected panel already has artwork") },
            text = {
                Text(
                    "Choose where the first generated image should go. Replacement changes only the selected panel; all additional images use the next free slots.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        generatedReplaceTargetKey = state.selectedMediaKey
                        selectedEmptySlotIndex = null
                        showGeneratedImportChoice = false
                        generatedPanelPicker.launch(arrayOf("image/*"))
                    },
                ) { Text("Replace selected") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        generatedReplaceTargetKey = null
                        selectedEmptySlotIndex = null
                        showGeneratedImportChoice = false
                        generatedPanelPicker.launch(arrayOf("image/*"))
                    },
                ) { Text("Use next free slot") }
            },
        )
    }

    if (state.storyboardGenerationOpen) {
        StoryboardGenerationDialog(
            state = state,
            onSource = viewModel::onStoryboardSourceChanged,
            onModel = viewModel::selectModel,
            onGenerateMissingArt = viewModel::setStoryboardGenerateMissingArt,
            onStart = viewModel::startStoryboardGeneration,
            onStop = viewModel::stopStoryboardGeneration,
            onRetry = viewModel::retryStoryboardGeneration,
            onOffline = viewModel::continueStoryboardOffline,
            onApply = viewModel::applyStoryboardDraft,
            onDismiss = viewModel::closeStoryboardGeneration,
        )
    }

    if (showMangaSources) {
        MangaSourceDialog(
            onImportChapter = { chapterId ->
                showMangaSources = false
                viewModel.importDownloadedChapter(chapterId)
            },
            onTranslateChapter = { chapterId ->
                showMangaSources = false
                viewModel.translateDownloadedChapter(chapterId)
            },
            onDismiss = { showMangaSources = false },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(inkTokens().background)
            .focusRequester(mediaFocus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Backspace, Key.Delete -> {
                        if (state.selectedMediaKey != null) {
                            viewModel.removeSelectedMedia()
                            true
                        } else {
                            false
                        }
                    }
                    else -> false
                }
            },
    ) {
        // Title + Messenger|DM|Roleplay live in AppShell WorkspaceChrome (collapsible).
        when (state.displayMode) {
            "roleplay" -> Column(modifier = Modifier.weight(1f)) {
                if (state.storyboardStatus.isNotBlank()) {
                    Text(
                        state.storyboardStatus,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = InkSpacing.md, vertical = 2.dp),
                    )
                }
                if (storyboardCreator) {
                    StoryboardCreatorHeader(
                        pageNumber = state.pages.indexOfFirst { it.id == state.activePageId }.coerceAtLeast(0) + 1,
                        pageCount = state.pages.size.coerceAtLeast(1),
                        preview = storyboardPreview,
                        pagesExpanded = storyboardPagesExpanded,
                        onBack = onBack,
                        onTogglePreview = {
                            storyboardPreview = !storyboardPreview
                            viewModel.selectMedia(null, null)
                            selectedEmptySlotIndex = null
                        },
                        onTogglePages = { storyboardPagesExpanded = !storyboardPagesExpanded },
                    )
                } else if (!editorOnly) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = InkSpacing.md, vertical = InkSpacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        InkTextButton(
                            label = "Create page with AI",
                            onClick = { viewModel.openStoryboardGeneration(rightToLeft) },
                            compact = true,
                        )
                        InkTextButton(
                            label = "Manga sources",
                            onClick = { showMangaSources = true },
                            compact = true,
                        )
                        InkTextButton(
                            label = "Export PNG",
                            onClick = viewModel::exportStoryboardPage,
                            compact = true,
                        )
                        InkTextButton(
                            label = "Translate page to English",
                            onClick = viewModel::translateActiveMangaPageToEnglish,
                            compact = true,
                        )
                        Text(
                            "or continue editing the current page; originals remain preserved",
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.secondaryText,
                            modifier = Modifier.padding(start = InkSpacing.sm),
                        )
                    }
                    Text(
                        "AI page plans reuse saved artwork first; every panel stays editable after applying.",
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.secondaryText,
                        modifier = Modifier.padding(horizontal = InkSpacing.md, vertical = 2.dp),
                    )
                } else {
                    Text(
                        "Editing imported manga · originals stay unchanged",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = InkSpacing.md, vertical = 4.dp),
                    )
                }
                if (!storyboardCreator || storyboardPagesExpanded) PageStrip(
                    pages = state.pages,
                    activePageId = state.activePageId,
                    onSelect = { pageId ->
                        selectedEmptySlotIndex = null
                        viewModel.switchPage(pageId)
                    },
                    onAddPage = viewModel::addPage,
                    onRenamePage = viewModel::renamePage,
                    onDeletePage = viewModel::deletePage,
                    onApplyTemplate = { templateId ->
                        selectedEmptySlotIndex = null
                        viewModel.applyPanelTemplate(templateId)
                    },
                    rightToLeft = rightToLeft,
                    onImportPages = {
                        pagesPicker.launch(
                            arrayOf(
                                "application/pdf",
                                "application/zip",
                                "application/x-cbz",
                                "application/vnd.comicbook+zip",
                                "image/*",
                            ),
                        )
                    },
                    onImportGeneratedPanel = {
                        if (state.selectedMediaKey != null) {
                            showGeneratedImportChoice = true
                        } else {
                            generatedReplaceTargetKey = null
                            generatedPanelPicker.launch(arrayOf("image/*"))
                        }
                    },
                )
                ScrollGutterBackdrop(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = InkSpacing.sm),
                ) {
                    MangaSnapGrid(
                        panels = state.mediaPanels,
                        selectedKey = state.selectedMediaKey,
                        canPaste = state.canPasteMedia,
                        compactStyle = compactStyle,
                        gridSize = MediaGrid.SIZE,
                        templateId = state.activeTemplateId,
                        showTemplateSlots = !storyboardCreator || storyboardTool == "Panels",
                        editable = !storyboardPreview,
                        selectedEmptySlotIndex = selectedEmptySlotIndex,
                        textEmphasis = false,
                        emptyHint = if (storyboardCreator) {
                            "An empty comic page.\n\nOpen Panels to choose a layout, Art to add media, or AI to draft the page."
                        } else {
                            "An empty page.\n\nUse Add pages for a PDF, CBZ, webtoon, or page image. Long-press a panel for picture tools."
                        },
                        onSelect = { msgId, blockId ->
                            selectedEmptySlotIndex = null
                            viewModel.selectMedia(msgId, blockId)
                        },
                        onEmptySlotSelect = { slotIndex ->
                            viewModel.selectMedia(null, null)
                            selectedEmptySlotIndex = slotIndex
                        },
                        onRemove = viewModel::removeMedia,
                        onSnap = viewModel::setMediaGridCell,
                        onResizeSpan = viewModel::setMediaGridSpan,
                        onStackOnto = viewModel::stackMediaOnto,
                        onStackMenu = viewModel::stackMedia,
                        onCycleStack = viewModel::cycleMediaStack,
                        onMediaEdit = viewModel::onMediaEditAction,
                        onMediaTransform = viewModel::setMediaTransform,
                        onOverlayMove = viewModel::moveTextOverlay,
                        onOverlayResize = viewModel::resizeTextOverlay,
                        onOverlayTap = viewModel::openOverlayEditor,
                        onClearSelection = {
                            selectedEmptySlotIndex = null
                            viewModel.selectMedia(null, null)
                        },
                        onAddMedia = {
                            mediaPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo),
                            )
                        },
                        onGenerateMedia = viewModel::openImageGen,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            "dungeonMaster" -> Column(modifier = Modifier.weight(1f)) {
                PageStrip(
                    pages = state.pages,
                    activePageId = state.activePageId,
                    onSelect = viewModel::switchPage,
                    onAddPage = viewModel::addPage,
                    onRenamePage = viewModel::renamePage,
                    onDeletePage = viewModel::deletePage,
                    onApplyTemplate = viewModel::applyPanelTemplate,
                    rightToLeft = rightToLeft,
                )
                ScrollGutterBackdrop(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = InkSpacing.sm),
                ) {
                    MangaSnapGrid(
                        panels = state.mediaPanels,
                        selectedKey = state.selectedMediaKey,
                        canPaste = state.canPasteMedia,
                        compactStyle = compactStyle,
                        gridSize = MediaGrid.DM_SIZE,
                        templateId = state.activeTemplateId,
                        textEmphasis = true,
                        emptyHint = "An empty scene.\n\nProse and pictures share the board — tap either to move or resize it.\nPress / for AI · \\ to write it yourself.",
                        onSelect = { msgId, blockId -> viewModel.selectMedia(msgId, blockId) },
                        onRemove = viewModel::removeMedia,
                        onSnap = viewModel::setMediaGridCell,
                        onResizeSpan = viewModel::setMediaGridSpan,
                        onStackOnto = viewModel::stackMediaOnto,
                        onStackMenu = viewModel::stackMedia,
                        onCycleStack = viewModel::cycleMediaStack,
                        onMediaEdit = viewModel::onMediaEditAction,
                        onMediaTransform = viewModel::setMediaTransform,
                        onOverlayMove = viewModel::moveTextOverlay,
                        onOverlayResize = viewModel::resizeTextOverlay,
                        onOverlayTap = viewModel::openOverlayEditor,
                        onClearSelection = { viewModel.selectMedia(null, null) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            else -> ScrollGutterBackdrop(modifier = Modifier.weight(1f)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = scrollGutterPadding(),
            ) {
                itemsIndexed(state.messages, key = { _, it -> it.id }) { index, message ->
                    val previous = state.messages.getOrNull(index - 1)
                    // Discord-style grouping: repeat the avatar/name header only when the
                    // speaker changes or enough time has passed.
                    val grouped = previous != null &&
                        previous.speaker == message.speaker &&
                        previous.role == message.role &&
                        (message.createdAt - previous.createdAt) in 0 until GROUPING_WINDOW_MS
                    val showDayDivider = previous != null &&
                        !isSameDay(previous.createdAt, message.createdAt)
                    if (showDayDivider) {
                        DayDivider(message.createdAt)
                    }
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Box {
                            MessengerRow(
                                message = message,
                                grouped = grouped && !showDayDivider,
                                compactStyle = compactStyle,
                                selectedMediaKey = state.selectedMediaKey,
                                onLongPress = { popupMessageId = message.id },
                                canPasteMedia = state.canPasteMedia,
                                onSelectMedia = { blockId ->
                                    viewModel.selectMedia(message.id, blockId)
                                },
                                onRemoveMedia = { blockId ->
                                    viewModel.removeMedia(message.id, blockId)
                                },
                                onMoveMedia = { blockId, delta ->
                                    viewModel.moveMedia(message.id, blockId, delta)
                                },
                                onStackMedia = { blockId ->
                                    viewModel.stackMedia(message.id, blockId)
                                },
                                onStackOnto = { fromId, ontoId ->
                                    viewModel.stackMediaOnto(message.id, fromId, ontoId)
                                },
                                onCycleStack = { blockId ->
                                    viewModel.cycleMediaStack(message.id, blockId)
                                },
                                onMediaEdit = { blockId, action ->
                                    viewModel.onMediaEditAction(message.id, blockId, action)
                                },
                            )
                            EditTextPopup(
                                expanded = popupMessageId == message.id,
                                onDismiss = { popupMessageId = null },
                                config = EditTextPopupConfig(
                                    showFormatting = false,
                                    showWritingAi = false,
                                    showHistory = false,
                                    showMessageEdit = true,
                                    showSpeak = true,
                                    hasSelection = message.text.isNotBlank(),
                                ),
                                onAction = { action ->
                                    when (action) {
                                        EditTextAction.Copy, EditTextAction.SelectAll -> {
                                            if (message.text.isNotBlank()) {
                                                clipboard.setText(AnnotatedString(message.text))
                                            }
                                        }
                                        EditTextAction.Cut -> {
                                            if (message.text.isNotBlank()) {
                                                clipboard.setText(AnnotatedString(message.text))
                                                viewModel.editMessage(message.id, "")
                                            }
                                        }
                                        EditTextAction.Paste -> {
                                            val clip = clipboard.getText()?.text.orEmpty()
                                            editingMessageId = message.id
                                            editDraft = message.text + clip
                                        }
                                        EditTextAction.Delete -> viewModel.deleteMessage(message.id)
                                        EditTextAction.Edit -> {
                                            editingMessageId = message.id
                                            editDraft = message.text
                                        }
                                        EditTextAction.Speak -> viewModel.speakText(message.text)
                                        EditTextAction.Dictate -> {
                                            editingMessageId = message.id
                                            editDraft = message.text
                                            startDictateEdit()
                                        }
                                        else -> Unit
                                    }
                                },
                            )
                        }
                        if (message.role == "char" && message.swipeCount > 1) {
                            Row(modifier = Modifier.padding(start = MessengerGutterWidth)) {
                                InkTextButton(label = "◀", onClick = { viewModel.swipe(message.id, -1) })
                                Text(
                                    "${message.swipeIndex + 1}/${message.swipeCount}",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                                InkTextButton(label = "▶", onClick = { viewModel.swipe(message.id, 1) })
                                InkTextButton(label = "Regen", onClick = { viewModel.regenerate(message.id) })
                            }
                        }
                    }
                }
                if (state.isStreaming && state.streamingText.isNotBlank()) {
                    item("streaming") {
                        val speaker = state.messages.lastOrNull { it.role != "user" }?.speaker
                            ?: state.title.ifBlank { "Character" }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = InkSpacing.md, vertical = InkSpacing.xs),
                        ) {
                            CharacterAvatar(
                                name = speaker,
                                colorHex = state.messages.lastOrNull { it.role != "user" }
                                    ?.avatarColorHex
                                    .orEmpty(),
                            )
                            Column(modifier = Modifier.padding(start = InkSpacing.sm)) {
                                Text(
                                    "$speaker · typing…",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                SelectionContainer {
                                    Text(
                                        state.streamingText,
                                        style = compactStyle,
                                        modifier = Modifier.padding(top = 2.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                alwaysScrollEndSpacer()
            }
            }
        }

        if (storyboardCreator && state.displayMode == "roleplay") {
            StoryboardCreatorDock(
                activeTool = storyboardTool,
                onTool = { storyboardTool = it },
                preview = storyboardPreview,
                onTogglePreview = {
                    storyboardPreview = !storyboardPreview
                    viewModel.selectMedia(null, null)
                    selectedEmptySlotIndex = null
                },
                panels = state.mediaPanels,
                selectedKey = state.selectedMediaKey,
                canUndo = state.canUndoStoryboard,
                canRedo = state.canRedoStoryboard,
                onUndo = viewModel::undoStoryboardEdit,
                onRedo = viewModel::redoStoryboardEdit,
                onSelectPanel = { messageId, blockId -> viewModel.selectMedia(messageId, blockId) },
                onMoveLayer = { delta ->
                    state.selectedMediaKey?.split("::", limit = 2)?.takeIf { it.size == 2 }?.let {
                        viewModel.moveMedia(it[0], it[1], delta)
                    }
                },
                onSelectedAction = { action ->
                    state.selectedMediaKey?.split("::", limit = 2)?.takeIf { it.size == 2 }?.let {
                        viewModel.onMediaEditAction(it[0], it[1], action)
                    }
                },
                onAddCaption = {
                    state.selectedMediaKey?.split("::", limit = 2)?.takeIf { it.size == 2 }?.let {
                        viewModel.addTextOverlay(it[0], it[1])
                    }
                },
                onAddBubble = {
                    state.selectedMediaKey?.split("::", limit = 2)?.takeIf { it.size == 2 }?.let {
                        viewModel.addSpeechBubbleOverlay(it[0], it[1])
                    }
                },
                onAddPage = viewModel::addPage,
                onTogglePages = { storyboardPagesExpanded = !storyboardPagesExpanded },
                onTemplate = { templateId ->
                    selectedEmptySlotIndex = null
                    viewModel.applyPanelTemplate(templateId)
                },
                onAddArt = {
                    mediaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                },
                onImportPages = {
                    pagesPicker.launch(
                        arrayOf(
                            "application/pdf",
                            "application/zip",
                            "application/x-cbz",
                            "application/vnd.comicbook+zip",
                            "image/*",
                        ),
                    )
                },
                onGenerateArt = viewModel::openImageGen,
                onCreateWithAi = { viewModel.openStoryboardGeneration(rightToLeft) },
                onExport = viewModel::exportStoryboardPage,
            )
        }

        if (editorOnly && state.displayMode == "roleplay") {
            StoryboardEditorDock(
                onBack = onBack,
                onAddPage = viewModel::addPage,
                onApplyTemplate = viewModel::applyPanelTemplate,
                onExport = viewModel::exportStoryboardPage,
                onTranslate = viewModel::translateActiveMangaPageToEnglish,
                onColor = viewModel::openFirstMangaPanelEditor,
                onMediaAction = { action ->
                    state.selectedMediaKey?.split("::", limit = 2)?.takeIf { it.size == 2 }?.let { parts ->
                        viewModel.onMediaEditAction(parts[0], parts[1], action)
                    }
                },
                hasSelection = state.selectedMediaKey != null,
            )
        }

        if (state.errorMessage.isNotBlank()) {
            Text(
                state.errorMessage,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = InkSpacing.lg),
            )
        }
        CollapsibleUsageStrip(
            usageText = state.lastUsage,
            modifier = Modifier.padding(horizontal = InkSpacing.lg),
        )
        state.contextMeter?.let { meter ->
            Text(
                meter.label,
                style = MaterialTheme.typography.labelSmall,
                color = inkTokens().secondaryText,
                modifier = Modifier.padding(horizontal = InkSpacing.lg, vertical = 2.dp),
            )
        }

        // The shared prompt window — same bar as the RPG adventure and Novel
        // editor. Media attach stays on the mic-hold menu via the + button.
        if (!editorOnly && !storyboardCreator) UnifiedPromptBar(
            value = state.input,
            onValueChange = viewModel::onInputChange,
            placeholder = "Message ${state.title.ifBlank { "chat" }}",
            collapsed = promptCollapsed,
            onCollapsedChange = { promptCollapsed = it },
            contextLabel = state.contextMeter?.label.orEmpty(),
            minimumWords = minimumWordsText,
            maximumWords = maximumWordsText,
            onMinimumWordsChange = { value ->
                minimumWordsText = value.filter(Char::isDigit).take(4)
                minimumWordsText.toIntOrNull()?.let(viewModel::updateMinimumOutputWords)
            },
            onMaximumWordsChange = { value ->
                maximumWordsText = value.filter(Char::isDigit).take(4)
                maximumWordsText.toIntOrNull()?.let(viewModel::updateOutputWords)
            },
            wordRangeValid = wordRangeValid,
            modelLabel = PromptModelSelection.shortLabel(
                PromptModelSelection.effectiveModelRef(state.selectedModelRef, state.defaultModelRef),
                state.writingModels,
            ),
            onModelClick = { modelsOpen = true },
            aiMode = state.entryMode != "nai",
            onToggleMode = {
                viewModel.setEntryMode(if (state.entryMode == "nai") "ai" else "nai")
            },
            streaming = state.isStreaming,
            canSubmit = state.input.isNotBlank() && wordRangeValid,
            canClear = state.input.isNotBlank(),
            onSubmit = viewModel::send,
            onCancel = viewModel::cancelGeneration,
            onClear = viewModel::clearInput,
            onUndoClear = viewModel::undoClearInput,
            onRetry = viewModel::regenerateLatestReply,
            onContinue = viewModel::continueAdventure,
            onMicTap = { if (!state.isStreaming) startDictateNew() },
            onAdd = viewModel::requestMediaPick,
            onRoll = viewModel::rollAction,
            onSpoken = { spoken ->
                viewModel.onInputChange(mergeSpokenText(state.input, spoken))
            },
            compactSingleLine = true,
            showCommandPopup = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = InkSpacing.sm, vertical = InkSpacing.xs),
        )
    }

    if (modelsOpen) {
        PromptModelPickerDialog(
            models = state.writingModels,
            search = modelSearch,
            onSearchChange = { modelSearch = it },
            selectedRef = state.selectedModelRef,
            defaultRef = state.defaultModelRef,
            onSelect = { id ->
                viewModel.selectModel(id)
                modelsOpen = false
            },
            onUseDefault = {
                viewModel.useDefaultModel()
                modelsOpen = false
            },
            onDismiss = { modelsOpen = false },
        )
    }
}

@Composable
private fun StoryboardCreatorHeader(
    pageNumber: Int,
    pageCount: Int,
    preview: Boolean,
    pagesExpanded: Boolean,
    onBack: () -> Unit,
    onTogglePreview: () -> Unit,
    onTogglePages: () -> Unit,
) {
    val tokens = inkTokens()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(tokens.panel)
            .padding(horizontal = InkSpacing.sm, vertical = InkSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs),
    ) {
        InkTextButton(label = "‹ Projects", onClick = onBack, compact = true)
        Column(modifier = Modifier.weight(1f)) {
            Text("Storyboard creator", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "Page $pageNumber of $pageCount",
                style = MaterialTheme.typography.labelSmall,
                color = tokens.secondaryText,
            )
        }
        InkTextButton(
            label = if (pagesExpanded) "Hide pages" else "Pages",
            onClick = onTogglePages,
            compact = true,
        )
        InkTextButton(
            label = if (preview) "Edit" else "Preview",
            onClick = onTogglePreview,
            compact = true,
        )
    }
}

/** Bottom-owned tool workspace for original user/AI-created comic pages. */
@Composable
private fun StoryboardCreatorDock(
    activeTool: String,
    onTool: (String) -> Unit,
    preview: Boolean,
    onTogglePreview: () -> Unit,
    panels: List<RpMediaRef>,
    selectedKey: String?,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSelectPanel: (String, String) -> Unit,
    onMoveLayer: (Int) -> Unit,
    onSelectedAction: (MediaEditAction) -> Unit,
    onAddCaption: () -> Unit,
    onAddBubble: () -> Unit,
    onAddPage: () -> Unit,
    onTogglePages: () -> Unit,
    onTemplate: (String) -> Unit,
    onAddArt: () -> Unit,
    onImportPages: () -> Unit,
    onGenerateArt: () -> Unit,
    onCreateWithAi: () -> Unit,
    onExport: () -> Unit,
) {
    val tokens = inkTokens()
    val hasSelection = selectedKey != null
    val tools = listOf("Select", "Panels", "Art", "Text", "Bubble", "Layers", "AI", "Export")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(tokens.panel)
            .padding(horizontal = InkSpacing.sm, vertical = InkSpacing.xs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs),
        ) {
            tools.forEach { tool ->
                Text(
                    tool,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (activeTool == tool) tokens.activePillLabel else tokens.secondaryText,
                    modifier = Modifier
                        .clip(RoundedCornerShape(inkRadiusSm()))
                        .background(if (activeTool == tool) tokens.activePill else Color.Transparent)
                        .clickable { onTool(tool) }
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                )
            }
        }
        Text(
            when {
                preview -> "Preview mode · artwork and overlays are locked"
                hasSelection -> "$activeTool · selected panel tools"
                else -> "$activeTool · tap a panel to select it"
            },
            style = MaterialTheme.typography.labelSmall,
            color = tokens.secondaryText,
            modifier = Modifier.padding(top = 3.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (activeTool) {
                "Select" -> {
                    InkTextButton(label = "Undo", onClick = onUndo, enabled = canUndo, compact = true)
                    InkTextButton(label = "Redo", onClick = onRedo, enabled = canRedo, compact = true)
                    InkTextButton(
                        label = "Copy",
                        onClick = { onSelectedAction(MediaEditAction.Copy) },
                        enabled = hasSelection && !preview,
                        compact = true,
                    )
                    InkTextButton(
                        label = "Paste",
                        onClick = { onSelectedAction(MediaEditAction.Paste) },
                        enabled = hasSelection && !preview,
                        compact = true,
                    )
                    InkTextButton(
                        label = "Stack",
                        onClick = { onSelectedAction(MediaEditAction.Stack) },
                        enabled = hasSelection && !preview,
                        compact = true,
                    )
                    InkTextButton(
                        label = "Delete",
                        onClick = { onSelectedAction(MediaEditAction.Delete) },
                        enabled = hasSelection && !preview,
                        compact = true,
                    )
                    InkTextButton(label = if (preview) "Return to edit" else "Read preview", onClick = onTogglePreview, compact = true)
                }
                "Panels" -> {
                    InkTextButton(label = "Pages", onClick = onTogglePages, compact = true)
                    InkTextButton(label = "+ Page", onClick = onAddPage, enabled = !preview, compact = true)
                    InkTextButton(
                        label = "Split panel",
                        onClick = { onSelectedAction(MediaEditAction.SeparatePanelsAuto) },
                        enabled = hasSelection && !preview,
                        compact = true,
                    )
                    InkTextButton(
                        label = "AI panel split",
                        onClick = { onSelectedAction(MediaEditAction.SeparatePanels) },
                        enabled = hasSelection && !preview,
                        compact = true,
                    )
                    PanelTemplates.all.forEach { template ->
                        InkTextButton(
                            label = template.label,
                            onClick = { onTemplate(template.id) },
                            enabled = !preview,
                            compact = true,
                        )
                    }
                }
                "Art" -> {
                    InkTextButton(label = "Add media", onClick = onAddArt, enabled = !preview, compact = true)
                    InkTextButton(label = "Import pages", onClick = onImportPages, enabled = !preview, compact = true)
                    InkTextButton(label = "Generate AI", onClick = onGenerateArt, enabled = !preview, compact = true)
                    InkTextButton(
                        label = "Retouch",
                        onClick = { onSelectedAction(MediaEditAction.EditImage) },
                        enabled = hasSelection && !preview,
                        compact = true,
                    )
                }
                "Text" -> {
                    InkTextButton(label = "Add caption", onClick = onAddCaption, enabled = hasSelection && !preview, compact = true)
                    Text("Tap an existing caption to edit its wording and style.", style = MaterialTheme.typography.labelSmall, color = tokens.secondaryText)
                }
                "Bubble" -> {
                    InkTextButton(label = "Add speech bubble", onClick = onAddBubble, enabled = hasSelection && !preview, compact = true)
                    Text("Drag the bubble on the page; tap it for text, tail and colors.", style = MaterialTheme.typography.labelSmall, color = tokens.secondaryText)
                }
                "Layers" -> {
                    panels.forEachIndexed { index, panel ->
                        val key = "${panel.messageId}::${panel.blockId}"
                        val textCount = panel.overlays.size
                        InkTextButton(
                            label = buildString {
                                if (key == selectedKey) append("✓ ")
                                append("Layer ${index + 1}")
                                if (textCount > 0) append(" · $textCount text")
                            },
                            onClick = { onSelectPanel(panel.messageId, panel.blockId) },
                            compact = true,
                        )
                    }
                    InkTextButton(label = "Send backward", onClick = { onMoveLayer(-1) }, enabled = hasSelection && !preview, compact = true)
                    InkTextButton(label = "Bring forward", onClick = { onMoveLayer(1) }, enabled = hasSelection && !preview, compact = true)
                }
                "AI" -> {
                    InkTextButton(label = "Draft complete page", onClick = onCreateWithAi, enabled = !preview, compact = true)
                    InkTextButton(label = "Generate selected art", onClick = onGenerateArt, enabled = !preview, compact = true)
                    Text("AI changes apply only to this page or the selected panel.", style = MaterialTheme.typography.labelSmall, color = tokens.secondaryText)
                }
                "Export" -> {
                    InkTextButton(label = "Export page PNG", onClick = onExport, compact = true)
                    InkTextButton(label = "Preview first", onClick = onTogglePreview, compact = true)
                }
            }
        }
    }
}

/**
 * A compact, always-visible editor rail for imported manga.  The reader stays
 * the primary surface; this dock exposes the common actions without opening the
 * general AI prompt window.  More actions expand in place so the canvas keeps
 * its context.
 */
@Composable
private fun StoryboardEditorDock(
    onBack: () -> Unit,
    onAddPage: () -> Unit,
    onApplyTemplate: (String) -> Unit,
    onExport: () -> Unit,
    onTranslate: () -> Unit,
    onColor: () -> Unit,
    onMediaAction: (MediaEditAction) -> Unit,
    hasSelection: Boolean,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(inkTokens().panel)
            .padding(horizontal = InkSpacing.sm, vertical = InkSpacing.xs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs),
        ) {
            InkTextButton(label = "Back", onClick = onBack, compact = true)
            InkTextButton(label = "Page", onClick = { expanded = !expanded }, compact = true)
            InkTextButton(
                label = "Art",
                onClick = { onMediaAction(MediaEditAction.EditImage) },
                enabled = hasSelection,
                compact = true,
            )
            InkTextButton(
                label = "Text",
                onClick = { onMediaAction(MediaEditAction.AddTextOverlay) },
                enabled = hasSelection,
                compact = true,
            )
            InkTextButton(label = "Translate", onClick = onTranslate, compact = true)
            InkTextButton(label = "Color", onClick = onColor, compact = true)
            InkTextButton(
                label = if (expanded) "Less" else "More",
                onClick = { expanded = !expanded },
                compact = true,
            )
        }
        if (expanded) {
            Text(
                if (hasSelection) "Selected panel tools" else "Select a panel for art tools",
                style = MaterialTheme.typography.labelSmall,
                color = inkTokens().secondaryText,
                modifier = Modifier.padding(top = 2.dp, bottom = 2.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs),
            ) {
                InkTextButton(label = "Add page", onClick = onAddPage, compact = true)
                InkTextButton(label = "Template", onClick = { onApplyTemplate("classic-6") }, compact = true)
                InkTextButton(label = "Export PNG", onClick = onExport, compact = true)
                InkTextButton(
                    label = "Separate panels",
                    onClick = { onMediaAction(MediaEditAction.SeparatePanels) },
                    enabled = hasSelection,
                    compact = true,
                )
                InkTextButton(
                    label = "AI separate",
                    onClick = { onMediaAction(MediaEditAction.SeparatePanelsAuto) },
                    enabled = hasSelection,
                    compact = true,
                )
                InkTextButton(
                    label = "Delete",
                    onClick = { onMediaAction(MediaEditAction.Delete) },
                    enabled = hasSelection,
                    compact = true,
                )
            }
        }
    }
}

/**
 * Chat composer in the shape a messenger app uses: a rounded bar with a `+` for
 * attachments, the text field inline, and mic/send on the right. Previously the
 * messenger had no input at all — typing went through the global `/` overlay,
 * which is what made a chat screen feel like anything but a chat screen.
 */
@Composable
private fun MessageComposer(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    entryMode: String,
    isStreaming: Boolean,
    onSend: () -> Unit,
    onCancel: () -> Unit = {},
    onPickMedia: () -> Unit,
    onPickAudio: () -> Unit,
    onDictate: () -> Unit,
    onToggleEntryMode: () -> Unit,
) {
    val tokens = inkTokens()
    var attachOpen by remember { mutableStateOf(false) }
    val canSend = value.isNotBlank() && !isStreaming

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = InkSpacing.sm, vertical = InkSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs),
    ) {
        Box {
            ComposerIconButton(
                glyph = "+",
                contentDescription = "Add media or audio",
                onClick = { attachOpen = true },
                background = tokens.hover,
                tint = tokens.primaryText,
            )
            DropdownMenu(expanded = attachOpen, onDismissRequest = { attachOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Picture or video") },
                    onClick = { attachOpen = false; onPickMedia() },
                )
                DropdownMenuItem(
                    text = { Text("Audio") },
                    onClick = { attachOpen = false; onPickAudio() },
                )
            }
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(percent = 50))
                .background(tokens.hover)
                .padding(horizontal = InkSpacing.md, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.secondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                androidx.compose.foundation.text.BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = tokens.primaryText),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(tokens.primaryText),
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            // AI vs manual entry, kept where it affects what Send does.
            Text(
                if (entryMode == "nai") "NAI" else "AI",
                style = MaterialTheme.typography.labelSmall,
                color = if (entryMode == "nai") tokens.secondaryText else tokens.activePill,
                modifier = Modifier
                    .clip(RoundedCornerShape(inkRadiusSm()))
                    .clickable(onClick = onToggleEntryMode)
                    .padding(horizontal = InkSpacing.xs, vertical = 2.dp),
            )
        }

        if (isStreaming) {
            ComposerIconButton(
                glyph = "✕",
                contentDescription = "Cancel generation",
                onClick = onCancel,
                background = tokens.hover,
                tint = tokens.primaryText,
            )
        } else if (canSend) {
            ComposerIconButton(
                glyph = "➤",
                contentDescription = "Send",
                onClick = onSend,
                background = tokens.activePill,
                tint = tokens.activePillLabel,
            )
        } else {
            ComposerIconButton(
                glyph = "🎙",
                contentDescription = "Dictate",
                onClick = onDictate,
                background = tokens.hover,
                tint = tokens.primaryText,
            )
        }
    }
}

@Composable
private fun ComposerIconButton(
    glyph: String,
    contentDescription: String,
    onClick: () -> Unit,
    background: Color,
    tint: Color,
) {
    Box(
        modifier = Modifier
            .width(36.dp)
            .height(36.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(background)
            .clickable(onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, style = MaterialTheme.typography.titleMedium, color = tint)
    }
}

/** Comic-book page tabs: tap to flip, `+` to add, long-press for rename/delete. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PageStrip(
    pages: List<RpPageMeta>,
    activePageId: String,
    onSelect: (String) -> Unit,
    onAddPage: () -> Unit,
    onRenamePage: (String, String) -> Unit,
    onDeletePage: (String) -> Unit,
    onApplyTemplate: (String) -> Unit,
    rightToLeft: Boolean = false,
    /** "Add pages" — import pictures or a whole PDF/CBZ/webtoon file. */
    onImportPages: (() -> Unit)? = null,
    /** Durable picker import for externally-generated panel artwork. */
    onImportGeneratedPanel: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val tokens = inkTokens()
    var menuForPageId by remember { mutableStateOf<String?>(null) }
    var renamingPageId by remember { mutableStateOf<String?>(null) }
    var renameDraft by remember { mutableStateOf("") }
    var templateMenuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = InkSpacing.sm, vertical = InkSpacing.xs),
        // Manga reads right-to-left, so page 1 sits on the right.
        horizontalArrangement = if (rightToLeft) {
            Arrangement.spacedBy(InkSpacing.xs, Alignment.End)
        } else {
            Arrangement.spacedBy(InkSpacing.xs)
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        pages.forEachIndexed { index, page ->
            val active = page.id == activePageId
            Box {
                Text(
                    text = page.title ?: "Page ${index + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (active) tokens.activePillLabel else tokens.secondaryText,
                    modifier = Modifier
                        .clip(RoundedCornerShape(inkRadiusSm()))
                        .background(
                            if (active) tokens.activePill else Color.Transparent,
                            RoundedCornerShape(inkRadiusSm()),
                        )
                        .border(
                            1.dp,
                            if (active) Color.Transparent else tokens.hairline,
                            RoundedCornerShape(inkRadiusSm()),
                        )
                        .combinedClickable(
                            onClick = { onSelect(page.id) },
                            onLongClick = { menuForPageId = page.id },
                        )
                        .padding(horizontal = InkSpacing.sm, vertical = 4.dp),
                )
                DropdownMenu(
                    expanded = menuForPageId == page.id,
                    onDismissRequest = { menuForPageId = null },
                ) {
                    DropdownMenuItem(
                        text = { Text("Rename…") },
                        onClick = {
                            renameDraft = page.title ?: "Page ${index + 1}"
                            renamingPageId = page.id
                            menuForPageId = null
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete page") },
                        enabled = pages.size > 1,
                        onClick = {
                            onDeletePage(page.id)
                            menuForPageId = null
                        },
                    )
                }
            }
        }
        Text(
            text = "+",
            style = MaterialTheme.typography.labelMedium,
            color = tokens.secondaryText,
            modifier = Modifier
                .clip(RoundedCornerShape(inkRadiusSm()))
                .border(1.dp, tokens.hairline, RoundedCornerShape(inkRadiusSm()))
                .clickable { onAddPage() }
                .padding(horizontal = InkSpacing.md, vertical = 4.dp),
        )
        if (onImportPages != null) {
            Text(
                text = "Add pages",
                style = MaterialTheme.typography.labelMedium,
                color = tokens.secondaryText,
                modifier = Modifier
                    .clip(RoundedCornerShape(inkRadiusSm()))
                    .border(1.dp, tokens.hairline, RoundedCornerShape(inkRadiusSm()))
                    .clickable { onImportPages() }
                    .padding(horizontal = InkSpacing.sm, vertical = 4.dp),
            )
        }
        if (onImportGeneratedPanel != null) {
            Text(
                text = "Import generated panel",
                style = MaterialTheme.typography.labelMedium,
                color = tokens.secondaryText,
                modifier = Modifier
                    .clip(RoundedCornerShape(inkRadiusSm()))
                    .border(1.dp, tokens.hairline, RoundedCornerShape(inkRadiusSm()))
                    .clickable { onImportGeneratedPanel() }
                    .padding(horizontal = InkSpacing.sm, vertical = 4.dp),
            )
        }
        Box {
            Text(
                text = "Layout ▾",
                style = MaterialTheme.typography.labelMedium,
                color = tokens.secondaryText,
                modifier = Modifier
                    .clip(RoundedCornerShape(inkRadiusSm()))
                    .border(1.dp, tokens.hairline, RoundedCornerShape(inkRadiusSm()))
                    .clickable { templateMenuOpen = true }
                    .padding(horizontal = InkSpacing.sm, vertical = 4.dp),
            )
            DropdownMenu(
                expanded = templateMenuOpen,
                onDismissRequest = { templateMenuOpen = false },
            ) {
                PanelTemplates.all.forEach { template ->
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(InkSpacing.sm),
                            ) {
                                PanelTemplatePreview(template)
                                Column {
                                    Text(template.label, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "${template.panelCount} panels",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = tokens.secondaryText,
                                    )
                                }
                            }
                        },
                        onClick = {
                            templateMenuOpen = false
                            onApplyTemplate(template.id)
                        },
                    )
                }
            }
        }
    }

    if (renamingPageId != null) {
        AlertDialog(
            onDismissRequest = { renamingPageId = null },
            title = { Text("Rename page") },
            text = {
                OutlinedTextField(
                    value = renameDraft,
                    onValueChange = { renameDraft = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onRenamePage(renamingPageId!!, renameDraft)
                    renamingPageId = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renamingPageId = null }) { Text("Cancel") }
            },
        )
    }
}

/** A literal miniature of the selected 12×12 comic grid, not a text-only preset. */
@Composable
private fun PanelTemplatePreview(template: PanelTemplate) {
    val tokens = inkTokens()
    Canvas(
        modifier = Modifier
            .width(54.dp)
            .height(68.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(tokens.background),
    ) {
        val cellW = size.width / MediaGrid.SIZE
        val cellH = size.height / MediaGrid.SIZE
        template.slots.forEach { slot ->
            val left = slot.col * cellW + 1.5f
            val top = slot.row * cellH + 1.5f
            val width = slot.colSpan * cellW - 3f
            val height = slot.rowSpan * cellH - 3f
            val center = Offset(left + width / 2f, top + height / 2f)
            rotate(slot.rotationDeg, pivot = center) {
                drawRect(
                    color = tokens.activePill.copy(alpha = .16f),
                    topLeft = Offset(left, top),
                    size = Size(width, height),
                )
                drawRect(
                    color = tokens.primaryText.copy(alpha = .72f),
                    topLeft = Offset(left, top),
                    size = Size(width, height),
                    style = Stroke(width = 1.5f),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MangaSnapGrid(
    panels: List<RpMediaRef>,
    selectedKey: String?,
    canPaste: Boolean,
    compactStyle: androidx.compose.ui.text.TextStyle,
    gridSize: Int = MediaGrid.SIZE,
    templateId: String = "",
    showTemplateSlots: Boolean = true,
    editable: Boolean = true,
    selectedEmptySlotIndex: Int? = null,
    textEmphasis: Boolean = false,
    emptyHint: String,
    onSelect: (String, String) -> Unit,
    onEmptySlotSelect: (Int) -> Unit = {},
    onRemove: (String, String) -> Unit,
    onSnap: (String, String, Int, Int) -> Unit,
    onResizeSpan: (String, String, Int, Int) -> Unit,
    onStackOnto: (String, String, String) -> Unit,
    onStackMenu: (String, String) -> Unit,
    onCycleStack: (String, String) -> Unit,
    onMediaEdit: (String, String, MediaEditAction) -> Unit,
    onMediaTransform: (String, String, Float, Float, Float) -> Unit,
    onOverlayMove: (String, String, String, Float, Float) -> Unit,
    onOverlayResize: (String, String, String, Float) -> Unit,
    onOverlayTap: (String, String, String) -> Unit,
    onClearSelection: () -> Unit,
    /** Long-press on an empty slot: add a picture/video to the page. */
    onAddMedia: (() -> Unit)? = null,
    /** Long-press on an empty slot: generate a picture with a cloud AI model. */
    onGenerateMedia: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val tokens = inkTokens()
    val scroll = rememberScrollState()
    var emptySlotMenuAt by remember { mutableStateOf<Int?>(null) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 12.dp, vertical = InkSpacing.sm),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                // A real comic/manga page is portrait; the former square canvas
                // made complete templates look cropped and compressed.
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(inkRadiusSm()))
                .border(1.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f), RoundedCornerShape(inkRadiusSm()))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                // Preview mode locks the page; edit mode lets bare-canvas taps clear selection.
                .then(
                    if (editable) {
                        Modifier.pointerInput(Unit) {
                            detectTapGestures(onTap = { onClearSelection() })
                        }
                    } else {
                        Modifier
                    },
                ),
        ) {
            val cellW = maxWidth / gridSize
            val cellH = maxHeight / gridSize
            // The chosen layout's slots, drawn as empty frames so the page reads as
            // a comic page before any media is dropped in.
            val template = PanelTemplates.byId(templateId)?.takeIf {
                gridSize == MediaGrid.SIZE && showTemplateSlots
            }
            val gridPanels = panels.map { panel ->
                StoryboardGridItem(
                    panel.gridCol,
                    panel.gridRow,
                    panel.gridColSpan,
                    panel.gridRowSpan,
                )
            }
            template?.slots?.forEachIndexed { index, slot ->
                val occupied = isStoryboardSlotOccupied(slot, gridPanels, gridSize)
                val selectedSlot = !occupied && selectedEmptySlotIndex == index
                // Always construct the complete template underneath the artwork.
                // Existing media fills these frames; missing media leaves a numbered
                // panel behind, so selecting a six-panel layout visibly creates six.
                Box(
                    modifier = Modifier
                        .offset(x = cellW * slot.col, y = cellH * slot.row)
                        .width(cellW * slot.colSpan)
                        .height(cellH * slot.rowSpan)
                        .padding(2.dp)
                        .rotate(slot.rotationDeg)
                        .background(
                            if (selectedSlot) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                            } else {
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
                            },
                            RoundedCornerShape(inkRadiusSm()),
                        )
                        .border(
                            if (selectedSlot) 2.5.dp else 1.5.dp,
                            if (selectedSlot) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.72f)
                            },
                            RoundedCornerShape(inkRadiusSm()),
                        )
                        .then(
                            if (!occupied) {
                                if (onAddMedia != null) {
                                    Modifier.combinedClickable(
                                        onClick = { onEmptySlotSelect(index) },
                                        onLongClick = { emptySlotMenuAt = index },
                                    )
                                } else {
                                    Modifier.clickable { onEmptySlotSelect(index) }
                                }
                            } else {
                                Modifier
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Panel ${index + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selectedSlot) MaterialTheme.colorScheme.primary else tokens.secondaryText,
                    )
                    if (emptySlotMenuAt == index && (onAddMedia != null || onGenerateMedia != null)) {
                        DropdownMenu(
                            expanded = true,
                            onDismissRequest = { emptySlotMenuAt = null },
                        ) {
                            if (onAddMedia != null) {
                                DropdownMenuItem(
                                    text = { Text("Add picture / video") },
                                    onClick = {
                                        emptySlotMenuAt = null
                                        onAddMedia()
                                    },
                                )
                            }
                            if (onGenerateMedia != null) {
                                DropdownMenuItem(
                                    text = { Text("Generate picture (AI)") },
                                    onClick = {
                                        emptySlotMenuAt = null
                                        onGenerateMedia()
                                    },
                                )
                            }
                        }
                    }
                }
            }
            // Snap grid stays active for move/resize/stack, but lines are hidden.
            if (panels.isEmpty() && template == null) {
                Text(
                    emptyHint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.secondaryText,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(InkSpacing.lg),
                )
            }
            panels.forEach { panel ->
                val col = if (MediaGrid.isPlaced(panel.gridCol, panel.gridRow, gridSize)) {
                    panel.gridCol
                } else {
                    0
                }
                val row = if (MediaGrid.isPlaced(panel.gridCol, panel.gridRow, gridSize)) {
                    panel.gridRow
                } else {
                    0
                }
                val colSpan = MediaGrid.clampSpan(panel.gridColSpan, gridSize)
                    .coerceAtMost(gridSize - col)
                val rowSpan = MediaGrid.clampSpan(panel.gridRowSpan, gridSize)
                    .coerceAtMost(gridSize - row)
                val key = "${panel.messageId}::${panel.blockId}"
                MangaSnapPanel(
                    panel = panel,
                    selected = selectedKey == key,
                    canPaste = canPaste,
                    cellW = cellW,
                    cellH = cellH,
                    col = col,
                    row = row,
                    colSpan = colSpan,
                    rowSpan = rowSpan,
                    gridSize = gridSize,
                    textEmphasis = textEmphasis,
                    compactStyle = compactStyle,
                    panels = panels,
                    onSelect = { onSelect(panel.messageId, panel.blockId) },
                    onRemove = { onRemove(panel.messageId, panel.blockId) },
                    onSnap = { c, r -> onSnap(panel.messageId, panel.blockId, c, r) },
                    onResizeSpan = { cs, rs -> onResizeSpan(panel.messageId, panel.blockId, cs, rs) },
                    onStackOnto = { ontoBlockId ->
                        onStackOnto(panel.messageId, panel.blockId, ontoBlockId)
                    },
                    onStackMenu = { onStackMenu(panel.messageId, panel.blockId) },
                    onCycleStack = { onCycleStack(panel.messageId, panel.blockId) },
                    onMediaEdit = { onMediaEdit(panel.messageId, panel.blockId, it) },
                    onMediaTransform = { s, ox, oy ->
                        onMediaTransform(panel.messageId, panel.blockId, s, ox, oy)
                    },
                    onOverlayMove = { overlayId, x, y ->
                        onOverlayMove(panel.messageId, panel.blockId, overlayId, x, y)
                    },
                    onOverlayResize = { overlayId, w ->
                        onOverlayResize(panel.messageId, panel.blockId, overlayId, w)
                    },
                    onOverlayTap = { overlayId ->
                        onOverlayTap(panel.messageId, panel.blockId, overlayId)
                    },
                    editable = editable,
                )
            }
        }
        Spacer(modifier = Modifier.height(48.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MangaSnapPanel(
    panel: RpMediaRef,
    selected: Boolean,
    canPaste: Boolean,
    cellW: androidx.compose.ui.unit.Dp,
    cellH: androidx.compose.ui.unit.Dp,
    col: Int,
    row: Int,
    colSpan: Int,
    rowSpan: Int,
    gridSize: Int,
    textEmphasis: Boolean,
    compactStyle: androidx.compose.ui.text.TextStyle,
    panels: List<RpMediaRef>,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
    onSnap: (Int, Int) -> Unit,
    onResizeSpan: (Int, Int) -> Unit,
    onStackOnto: (String) -> Unit,
    onStackMenu: () -> Unit,
    onCycleStack: () -> Unit,
    onMediaEdit: (MediaEditAction) -> Unit,
    onMediaTransform: (Float, Float, Float) -> Unit,
    onOverlayMove: (String, Float, Float) -> Unit,
    onOverlayResize: (String, Float) -> Unit,
    onOverlayTap: (String) -> Unit,
    editable: Boolean = true,
) {
    var dragX by remember(panel.blockId) { mutableFloatStateOf(0f) }
    var dragY by remember(panel.blockId) { mutableFloatStateOf(0f) }
    var resizeDx by remember(panel.blockId) { mutableFloatStateOf(0f) }
    var resizeDy by remember(panel.blockId) { mutableFloatStateOf(0f) }
    var menuOpen by remember(panel.blockId) { mutableStateOf(false) }
    var adjustMode by remember(panel.blockId) { mutableStateOf(false) }
    val border = when {
        adjustMode -> MaterialTheme.colorScheme.secondary
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val widthDp = cellW * colSpan
    val heightDp = cellH * rowSpan
    fun commitMovePlacement() {
        val originX = with(density) { cellW.toPx() } * col
        val originY = with(density) { cellH.toPx() } * row
        val cellWPx = with(density) { cellW.toPx() }
        val cellHPx = with(density) { cellH.toPx() }
        val centerX = originX + dragX + (cellWPx * colSpan) / 2f
        val centerY = originY + dragY + (cellHPx * rowSpan) / 2f
        val gridW = cellWPx * gridSize
        val gridH = cellHPx * gridSize
        val snapCol = MediaGrid.snapFraction(centerX / gridW.coerceAtLeast(1f), gridSize)
        val snapRow = MediaGrid.snapFraction(centerY / gridH.coerceAtLeast(1f), gridSize)
        val target = panels.firstOrNull { other ->
            other.blockId != panel.blockId &&
                other.messageId == panel.messageId &&
                !other.isTextTile &&
                !panel.isTextTile &&
                MediaGrid.isPlaced(other.gridCol, other.gridRow, gridSize) &&
                snapCol in other.gridCol until (other.gridCol + MediaGrid.clampSpan(other.gridColSpan, gridSize)) &&
                snapRow in other.gridRow until (other.gridRow + MediaGrid.clampSpan(other.gridRowSpan, gridSize))
        }
        if (target != null) {
            onStackOnto(target.blockId)
        } else {
            onSnap(snapCol, snapRow)
        }
        dragX = 0f
        dragY = 0f
    }
    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    x = with(density) { (cellW * col).roundToPx() } + dragX.roundToInt(),
                    y = with(density) { (cellH * row).roundToPx() } + dragY.roundToInt(),
                )
            }
            .width(widthDp + with(density) { resizeDx.toDp() }.coerceAtLeast(0.dp))
            .height(heightDp + with(density) { resizeDy.toDp() }.coerceAtLeast(0.dp))
            .padding(2.dp)
            // Template-driven tilt: a slanted gutter, the way a comic paces action.
            .rotate(panel.panelRotationDeg)
            .clip(RoundedCornerShape(inkRadiusSm()))
            .border(if (selected) 2.dp else 1.dp, border, RoundedCornerShape(inkRadiusSm()))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .then(
                when {
                    !editable -> Modifier
                    // Pinch/pan belongs to the media itself while adjusting.
                    adjustMode -> Modifier
                    // Selected panels are directly draggable — no mode to enter first.
                    // The drag detector runs before the tap detector, so a press that
                    // never moves still falls through to tap/long-press.
                    selected -> Modifier
                        .pointerInput(panel.blockId, col, row, colSpan, rowSpan, panels) {
                            detectDragGestures(
                                onDragStart = {
                                    menuOpen = false
                                    dragX = 0f
                                    dragY = 0f
                                },
                                onDragEnd = { commitMovePlacement() },
                                onDragCancel = {
                                    dragX = 0f
                                    dragY = 0f
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragX += amount.x
                                    dragY += amount.y
                                },
                            )
                        }
                        .pointerInput(panel.blockId, panel.collapsed, panel.stackedPaths.size) {
                            detectTapGestures(
                                onTap = {
                                    when {
                                        panel.collapsed -> onMediaEdit(MediaEditAction.Uncollapse)
                                        panel.stackedPaths.size > 1 -> onCycleStack()
                                        else -> Unit
                                    }
                                },
                                onLongPress = { menuOpen = true },
                            )
                        }
                    else -> Modifier.combinedClickable(
                        onClick = {
                            when {
                                panel.collapsed -> onMediaEdit(MediaEditAction.Uncollapse)
                                else -> onSelect()
                            }
                        },
                        onLongClick = {
                            onSelect()
                            menuOpen = true
                        },
                    )
                },
            ),
    ) {
        if (panel.collapsed) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(InkSpacing.xs),
                contentAlignment = Alignment.Center,
            ) {
                Text("Collapsed", style = MaterialTheme.typography.labelSmall)
            }
        } else if (panel.isTextTile) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(InkSpacing.sm),
            ) {
                Text(
                    panel.speaker,
                    style = MaterialTheme.typography.labelSmall,
                    color = inkTokens().secondaryText,
                )
                Text(
                    panel.caption,
                    style = if (textEmphasis) {
                        MaterialTheme.typography.bodyMedium
                    } else {
                        compactStyle
                    },
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .weight(1f, fill = false),
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else if (panel.isAudio) {
            AudioMediaPlayer(
                path = panel.path,
                label = "Audio",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(InkSpacing.xs),
            )
        } else if (textEmphasis) {
            Column(modifier = Modifier.fillMaxSize()) {
                ZoomableMedia(
                    path = panel.path,
                    contentDescription = "Panel",
                    maxHeight = heightDp * 0.62f,
                    contentScale = ContentScale.Fit,
                    decodeOriginal = true,
                    fillPanel = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    initialScale = panel.mediaScale,
                    initialOffsetXPercent = panel.mediaOffsetXPercent,
                    initialOffsetYPercent = panel.mediaOffsetYPercent,
                    onTransformEnd = onMediaTransform,
                    onLongPress = if (editable) {
                        {
                            onSelect()
                            menuOpen = true
                        }
                    } else null,
                )
                if (panel.caption.isNotBlank() && panel.caption != "[media]") {
                    Text(
                        text = panel.caption,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                    )
                }
            }
        } else {
            ZoomableMedia(
                path = panel.path,
                contentDescription = "Panel",
                maxHeight = heightDp,
                contentScale = ContentScale.Crop,
                decodeOriginal = true,
                fillPanel = true,
                modifier = Modifier.fillMaxSize(),
                initialScale = panel.mediaScale,
                initialOffsetXPercent = panel.mediaOffsetXPercent,
                initialOffsetYPercent = panel.mediaOffsetYPercent,
                onTransformEnd = onMediaTransform,
                onLongPress = if (editable) {
                    {
                        onSelect()
                        menuOpen = true
                    }
                } else null,
            )
        }
        if (!panel.collapsed && panel.overlays.isNotEmpty()) {
            TextOverlayLayer(
                overlays = panel.overlays,
                // Overlays stay draggable only once the panel itself is settled,
                // so panel-drag and overlay-drag never compete for the same press.
                editable = editable && !selected,
                onMove = { id, x, y -> onOverlayMove(id, x, y) },
                onResize = { id, w -> onOverlayResize(id, w) },
                onTap = { id -> onOverlayTap(id) },
            )
        }
        if (editable && adjustMode) {
            Text(
                "Pinch/drag image · tap to finish",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(2.dp)
                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.9f), RoundedCornerShape(4.dp))
                    .clickable { adjustMode = false }
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        if (!panel.collapsed && !panel.isTextTile && panel.stackedPaths.size > 1) {
            Text(
                "x${panel.stackedPaths.size}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(2.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
        if (
            !panel.collapsed &&
            !panel.isTextTile &&
            !textEmphasis &&
            panel.caption.isNotBlank() &&
            panel.caption != "[media]"
        ) {
            Text(
                text = panel.caption.take(40),
                style = compactStyle.copy(fontSize = 10.sp),
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(2.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
        if (editable) {
            InkTextButton(
                label = "-",
                onClick = onRemove,
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
        // Resize grip only on the selected panel, so an unselected canvas stays clean.
        if (editable && !panel.collapsed && selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(2.dp)
                    .width(22.dp)
                    .height(22.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
                    .pointerInput(panel.blockId, col, row, colSpan, rowSpan, gridSize) {
                        detectDragGestures(
                            onDragStart = {
                                onSelect()
                                resizeDx = 0f
                                resizeDy = 0f
                            },
                            onDragEnd = {
                                val newColSpan = (
                                    (colSpan + resizeDx / cellW.toPx()).roundToInt()
                                    ).coerceIn(1, gridSize - col)
                                val newRowSpan = (
                                    (rowSpan + resizeDy / cellH.toPx()).roundToInt()
                                    ).coerceIn(1, gridSize - row)
                                onResizeSpan(newColSpan, newRowSpan)
                                resizeDx = 0f
                                resizeDy = 0f
                            },
                            onDragCancel = {
                                resizeDx = 0f
                                resizeDy = 0f
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                resizeDx += amount.x
                                resizeDy += amount.y
                            },
                        )
                    },
            )
        }
        if (editable) MediaEditPopup(
            expanded = menuOpen,
            onDismiss = { menuOpen = false },
            config = MediaEditPopupConfig(
                canPaste = canPaste,
                isCollapsed = panel.collapsed,
                canShrink = colSpan > 1 || rowSpan > 1,
                canExpand = colSpan < gridSize - col || rowSpan < gridSize - row,
                showStack = !panel.isTextTile,
                // Move is no longer a mode — a selected panel just drags.
                showMove = false,
                showAdjustImage = !panel.isTextTile && !panel.isAudio,
                showTextOverlay = !panel.isTextTile && !panel.isAudio,
                showPictureTools = !panel.isTextTile && !panel.isAudio,
            ),
            onAction = { action ->
                when (action) {
                    MediaEditAction.Delete -> onRemove()
                    MediaEditAction.Stack -> onStackMenu()
                    MediaEditAction.AdjustImage -> {
                        onSelect()
                        menuOpen = false
                        adjustMode = true
                    }
                    MediaEditAction.Expand -> onResizeSpan(
                        (colSpan + 1).coerceAtMost(gridSize - col),
                        (rowSpan + 1).coerceAtMost(gridSize - row),
                    )
                    MediaEditAction.Shrink -> onResizeSpan(
                        (colSpan - 1).coerceAtLeast(1),
                        (rowSpan - 1).coerceAtLeast(1),
                    )
                    else -> onMediaEdit(action)
                }
            },
        )
    }
}

/** Width of the avatar gutter, so grouped messages line up under the first one. */
private val MessengerGutterWidth = 44.dp

/** Consecutive messages from one speaker inside this window share a header. */
private const val GROUPING_WINDOW_MS = 5 * 60 * 1000L

private fun isSameDay(a: Long, b: Long): Boolean {
    if (a == 0L || b == 0L) return true
    val zone = java.time.ZoneId.systemDefault()
    return java.time.Instant.ofEpochMilli(a).atZone(zone).toLocalDate() ==
        java.time.Instant.ofEpochMilli(b).atZone(zone).toLocalDate()
}

private fun formatClock(epochMillis: Long): String {
    if (epochMillis == 0L) return ""
    return java.time.Instant.ofEpochMilli(epochMillis)
        .atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))
}

private fun formatDay(epochMillis: Long): String {
    if (epochMillis == 0L) return ""
    val date = java.time.Instant.ofEpochMilli(epochMillis)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalDate()
    val today = java.time.LocalDate.now()
    return when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(java.time.format.DateTimeFormatter.ofPattern("MMMM d, yyyy"))
    }
}

@Composable
private fun DayDivider(epochMillis: Long) {
    val tokens = inkTokens()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = InkSpacing.md, vertical = InkSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(tokens.hairline),
        )
        Text(
            formatDay(epochMillis),
            style = MaterialTheme.typography.labelSmall,
            color = tokens.secondaryText,
            modifier = Modifier.padding(horizontal = InkSpacing.sm),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(tokens.hairline),
        )
    }
}

/**
 * One message in the messenger transcript, laid out like a modern chat client:
 * an avatar gutter on the left, a bold name plus timestamp, then flat text.
 * When [grouped] the header and avatar are omitted so runs of messages read as one block.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessengerRow(
    message: RpMessageUi,
    grouped: Boolean,
    compactStyle: androidx.compose.ui.text.TextStyle,
    selectedMediaKey: String?,
    canPasteMedia: Boolean,
    onLongPress: () -> Unit,
    onSelectMedia: (String) -> Unit,
    onRemoveMedia: (String) -> Unit,
    onMoveMedia: (String, Int) -> Unit,
    onStackMedia: (String) -> Unit,
    onStackOnto: (String, String) -> Unit,
    onCycleStack: (String) -> Unit,
    onMediaEdit: (String, MediaEditAction) -> Unit,
) {
    val tokens = inkTokens()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = {}, onLongClick = onLongPress)
            .padding(
                start = InkSpacing.md,
                end = InkSpacing.md,
                top = if (grouped) 1.dp else InkSpacing.sm,
                bottom = 1.dp,
            ),
    ) {
        Box(modifier = Modifier.width(MessengerGutterWidth)) {
            if (!grouped) {
                CharacterAvatar(name = message.speaker, colorHex = message.avatarColorHex)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            if (!grouped) {
                Row(verticalAlignment = Alignment.Bottom) {
                    // Name carries the character's color, the way role colors work in Discord.
                    Text(
                        message.speaker,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = parseHexColor(
                            message.avatarColorHex,
                            MaterialTheme.colorScheme.onSurface,
                        ),
                    )
                    val clock = formatClock(message.createdAt)
                    if (clock.isNotBlank()) {
                        Text(
                            clock,
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.secondaryText,
                            modifier = Modifier.padding(start = InkSpacing.xs),
                        )
                    }
                }
            }
            if (message.text.isNotBlank()) {
                // Selectable so any span can be copied, not just whole messages.
                SelectionContainer {
                    Text(
                        message.text,
                        style = compactStyle,
                        modifier = Modifier.padding(top = if (grouped) 0.dp else 2.dp),
                    )
                }
            }
            if (message.usageText.isNotBlank()) {
                Text(
                    message.usageText,
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.secondaryText,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            message.mediaPaths.zip(message.mediaBlockIds).forEachIndexed { index, (path, blockId) ->
                RemovableMedia(
                    path = path,
                    blockId = blockId,
                    selected = selectedMediaKey == "${message.id}::$blockId",
                    maxHeight = 260.dp,
                    contentScale = ContentScale.FillWidth,
                    stacked = (message.mediaStackPaths[blockId]?.size ?: 0) > 1,
                    siblingBlockIds = message.mediaBlockIds,
                    isAudio = message.mediaIsAudio.getOrElse(index) { false },
                    canPaste = canPasteMedia,
                    collapsed = message.mediaCollapsed[blockId] == true,
                    onSelect = { onSelectMedia(blockId) },
                    onRemove = { onRemoveMedia(blockId) },
                    onMove = { onMoveMedia(blockId, it) },
                    onStack = { onStackMedia(blockId) },
                    onStackOnto = onStackOnto,
                    onCycle = { onCycleStack(blockId) },
                    onMediaEdit = { onMediaEdit(blockId, it) },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RemovableMedia(
    path: String,
    blockId: String,
    selected: Boolean,
    maxHeight: androidx.compose.ui.unit.Dp,
    contentScale: ContentScale,
    stacked: Boolean,
    siblingBlockIds: List<String>,
    isAudio: Boolean = false,
    canPaste: Boolean = false,
    collapsed: Boolean = false,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
    onMove: (Int) -> Unit,
    onStack: () -> Unit,
    onStackOnto: (String, String) -> Unit,
    onCycle: () -> Unit,
    onMediaEdit: (MediaEditAction) -> Unit = {},
) {
    var dragY by remember(blockId) { mutableFloatStateOf(0f) }
    var menuOpen by remember(blockId) { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .padding(top = InkSpacing.xs)
            .offset { IntOffset(0, dragY.roundToInt()) }
            .border(
                if (selected) 2.dp else 0.dp,
                MaterialTheme.colorScheme.primary,
                RoundedCornerShape(inkRadiusSm()),
            )
            .combinedClickable(
                onClick = {
                    onSelect()
                    when {
                        collapsed -> onMediaEdit(MediaEditAction.Uncollapse)
                        stacked -> onCycle()
                    }
                },
                onLongClick = {
                    onSelect()
                    menuOpen = true
                },
            )
            .pointerInput(blockId, siblingBlockIds) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        onSelect()
                        menuOpen = false
                        dragY = 0f
                    },
                    onDragEnd = {
                        val approx = 180f
                        val steps = (dragY / approx).toInt()
                        if (steps != 0) {
                            val index = siblingBlockIds.indexOf(blockId)
                            val targetIndex = (index + steps).coerceIn(0, siblingBlockIds.lastIndex)
                            if (targetIndex != index && index >= 0) {
                                onStackOnto(blockId, siblingBlockIds[targetIndex])
                                dragY = 0f
                                return@detectDragGesturesAfterLongPress
                            }
                        }
                        when {
                            dragY < -40f -> onMove(-1)
                            dragY > 40f -> onMove(1)
                        }
                        dragY = 0f
                    },
                    onDragCancel = { dragY = 0f },
                    onDrag = { change, amount ->
                        change.consume()
                        dragY += amount.y
                    },
                )
            },
    ) {
        if (collapsed) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(inkTokens().hover)
                    .padding(horizontal = InkSpacing.sm),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    "Media collapsed · tap to uncollapse · hold for menu",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else if (isAudio) {
            AudioMediaPlayer(path = path, label = "Audio")
        } else {
            ZoomableMedia(
                path = path,
                contentDescription = null,
                maxHeight = maxHeight,
                contentScale = contentScale,
                onLongPress = {
                    onSelect()
                    menuOpen = true
                },
            )
        }
        if (!collapsed) {
            InkTextButton(
                label = "-",
                onClick = onRemove,
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
        MediaEditPopup(
            expanded = menuOpen,
            onDismiss = { menuOpen = false },
            config = MediaEditPopupConfig(
                canPaste = canPaste,
                isCollapsed = collapsed,
                canShrink = true,
                canExpand = true,
                showStack = true,
            ),
            onAction = { action ->
                when (action) {
                    MediaEditAction.Delete -> onRemove()
                    MediaEditAction.Stack -> onStack()
                    else -> onMediaEdit(action)
                }
            },
        )
    }
}
