package com.ihy2ln.weaverse.feature.roleplay.chat

import android.graphics.BitmapFactory
import com.ihy2ln.weaverse.ai.ModelInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.ihy2ln.weaverse.core.ui.components.InkTextButton
import com.ihy2ln.weaverse.core.ui.components.TextOverlayEditSheet
import com.ihy2ln.weaverse.core.ui.components.TextOverlayLayer
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkTokens
import java.io.File

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.mutableFloatStateOf

/** Manga-first surface: tools overlay the canvas, so opening controls never moves the page. */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ImportedMangaEditorScreen(
    chatId: String, onBack: () -> Unit,
    onChromeChange: (RoleplayChatChrome?) -> Unit = {},
    initialPageId: String? = null, initialEditorAction: String? = null,
    initialMangaChapterId: String? = null,
    viewModel: RoleplayChatViewModel = hiltViewModel(),
) {
    LaunchedEffect(chatId) {
        viewModel.bindChat(chatId)
        viewModel.setDisplayMode("roleplay")
    }
    val state by viewModel.uiState.collectAsState()
    val tokens = inkTokens()
    var initialActionApplied by rememberSaveable(chatId) { mutableStateOf(false) }
    var initialPageApplied by rememberSaveable(chatId, initialPageId) { mutableStateOf(false) }
    var showOriginal by rememberSaveable(chatId) { mutableStateOf(false) }
    var visionMenuOpen by rememberSaveable(chatId) { mutableStateOf(false) }
    var translationMenuOpen by rememberSaveable(chatId) { mutableStateOf(false) }
    var imageMenuOpen by rememberSaveable(chatId) { mutableStateOf(false) }
    var presetMenuOpen by remember { mutableStateOf(false) }
    var colorGuide by rememberSaveable(chatId, state.mangaColorStyleGuide) { mutableStateOf(state.mangaColorStyleGuide) }
    var preserveDrawing by rememberSaveable(chatId, state.mangaPreserveLineArt) { mutableStateOf(state.mangaPreserveLineArt) }
    val modelPanelHeight = (LocalConfiguration.current.screenHeightDp * 0.45f).coerceIn(100f, 420f).dp
    LaunchedEffect(chatId) {
        if (state.editorVisionModels.isEmpty() || state.editorTextModels.isEmpty() || state.editorImageModels.isEmpty()) {
            viewModel.refreshEditorModels()
        }
    }

    LaunchedEffect(chatId, initialPageId, state.pages) {
        if (!initialPageApplied) initialPageId?.takeIf { id -> state.pages.any { it.id == id } }?.let {
            viewModel.switchPage(it)
            initialPageApplied = true
        }
    }
    LaunchedEffect(
        chatId,
        initialPageId,
        initialEditorAction,
        initialMangaChapterId,
        state.activePageId,
        state.mediaPanels,
    ) {
        if (initialActionApplied || initialEditorAction == null || state.mediaPanels.isEmpty()) return@LaunchedEffect
        if (initialPageId != null && state.activePageId != initialPageId) return@LaunchedEffect
        when (initialEditorAction) {
            // Translation opens here first so the user can choose both models before spending tokens.
            "TranslatePage", "TranslateChapter" -> Unit
            "ColorPage", "ColorChapter" -> Unit
        }
        initialActionApplied = true
    }
    LaunchedEffect(state.activePageId, state.mediaPanels) {
        val selectedStillVisible = state.mediaPanels.any {
            "${it.messageId}::${it.blockId}" == state.selectedMediaKey
        }
        if (!selectedStillVisible) viewModel.selectFirstMangaPanel()
    }
    LaunchedEffect(state.title) {
        onChromeChange(
            RoleplayChatChrome(
                title = state.title.ifBlank { "Imported manga" },
                displayMode = "roleplay",
                onDisplayMode = {},
                showSwitcher = false,
                hideWorkspaceChrome = true,
            ),
        )
    }
    DisposableEffect(Unit) { onDispose { onChromeChange(null) } }

    var editing by rememberSaveable(chatId) { mutableStateOf(false) }
    var chromeVisible by rememberSaveable(chatId) { mutableStateOf(false) }
    var sheet by rememberSaveable(chatId) { mutableStateOf<String?>(null) }
    var aiAction by rememberSaveable(chatId) { mutableStateOf("Translate") }
    var chapterScope by rememberSaveable(chatId) { mutableStateOf(false) }
    var selectedTextId by remember(state.activePageId) { mutableStateOf<String?>(null) }
    var selectionReset by remember(state.activePageId) { mutableStateOf(0) }
    LaunchedEffect(editing) { if (!editing) selectedTextId = null }
    // Explicit translation entrypoints configure only; they never launch a paid job.
    LaunchedEffect(initialEditorAction) {
        if (initialEditorAction in listOf("TranslatePage", "TranslateChapter", "ColorPage", "ColorChapter")) {
            aiAction = if (initialEditorAction?.startsWith("Color") == true) "Colorize" else "Translate"
            editing = true
            sheet = "AI"
            chapterScope = initialEditorAction?.endsWith("Chapter") == true
        }
    }
    val pages = remember(state.pages) { state.pages.sortedBy { it.order } }
    val pageIndex = pages.indexOfFirst { it.id == state.activePageId }.coerceAtLeast(0)
    val selected = state.mediaPanels.firstOrNull { "${it.messageId}::${it.blockId}" == state.selectedMediaKey }
        ?: state.mediaPanels.firstOrNull()
    val pageScroll = rememberLazyListState()
    var zoom by rememberSaveable(state.activePageId) { mutableFloatStateOf(1f) }
    var panX by rememberSaveable(state.activePageId) { mutableFloatStateOf(0f) }
    var panY by rememberSaveable(state.activePageId) { mutableFloatStateOf(0f) }
    val transform = rememberTransformableState { scale, pan, _ ->
        zoom = (zoom * scale).coerceIn(1f, 4f)
        if (zoom <= 1f) { panX = 0f; panY = 0f }
        else { panX = (panX + pan.x).coerceIn(-2000f, 2000f); panY = (panY + pan.y).coerceIn(-4000f, 4000f) }
    }

    state.imageEditor?.let { editor ->
        PanelImageEditor(
            editor = editor,
            visionModels = state.editorVisionModels,
            textModels = state.editorTextModels,
            visionModelRef = state.editorVisionModelRef,
            textModelRef = state.editorTextModelRef,
            onVisionModelSelected = viewModel::selectEditorVisionModel,
            onTextModelSelected = viewModel::selectEditorTextModel,
            onSave = viewModel::saveEditedPanel,
            onClose = viewModel::closeImageEditor,
            onRunPipeline = viewModel::editorRunPipeline,
            onSetLanguage = viewModel::editorSetLanguage,
            onUpdateRegions = viewModel::editorUpdateRegions,
            onSelectRegion = viewModel::editorSelectRegion,
            onConsumeCleanup = viewModel::editorConsumeCleanup,
        )
        return
    }

    state.editingOverlay?.let { (messageId, blockId, overlayId) ->
        state.mediaPanels.find { it.messageId == messageId && it.blockId == blockId }
            ?.overlays?.find { it.id == overlayId }
            ?.let { overlay ->
                TextOverlayEditSheet(
                    overlay = overlay,
                    onDismiss = viewModel::closeOverlayEditor,
                    onSave = { viewModel.saveTextOverlay(messageId, blockId, it) },
                    onDelete = { viewModel.deleteTextOverlay(messageId, blockId, overlayId) },
                )
            }
    }


    fun back() {
        when {
            sheet != null -> sheet = null
            editing && selectedTextId != null -> { selectedTextId = null; selectionReset++ }
            editing -> { editing = false; chromeVisible = true }
            else -> onBack()
        }
    }
    BackHandler(enabled = state.editingOverlay == null) { back() }
    val review = state.activePageId in state.mangaTranslationReviewPageIds
    Box(Modifier.fillMaxSize().background(Color.Black).clipToBounds()) {
        LazyColumn(
            state = pageScroll,
            modifier = Modifier.fillMaxSize()
                .transformable(transform, enabled = !editing, canPan = { zoom > 1f })
                .graphicsLayer { scaleX = zoom; scaleY = zoom; translationX = panX; translationY = panY },
        ) {
            items(state.mediaPanels, key = { "${it.messageId}:${it.blockId}" }) { panel ->
                ImportedMangaPagePanel(panel = panel, showOriginal = showOriginal,
                    editable = editing, selected = editing && "${panel.messageId}::${panel.blockId}" == state.selectedMediaKey,
                    onSelect = {
                        if (editing) viewModel.selectMedia(panel.messageId, panel.blockId)
                        else chromeVisible = !chromeVisible
                    },
                    onOverlayMove = { id, x, y -> viewModel.moveTextOverlay(panel.messageId, panel.blockId, id, x, y) },
                    onOverlayResize = { id, x, y, w, h -> viewModel.resizeTextOverlay(panel.messageId, panel.blockId, id, x, y, w, h) },
                    onOverlayTap = { id -> viewModel.openOverlayEditor(panel.messageId, panel.blockId, id) },
                    selectionResetKey = selectionReset,
                    onOverlaySelected = { selectedTextId = it },
                )
            }
        }
        if (state.mediaPanels.isEmpty()) Text("Loading manga…", color = Color.White, modifier = Modifier.align(Alignment.Center))
        if (editing || chromeVisible) {
            Surface(Modifier.align(Alignment.TopCenter).fillMaxWidth(), color = MaterialTheme.colorScheme.surface.copy(alpha = .96f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MangaTool("Back", { back() })
                    Text(state.title.ifBlank { "Manga" }, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    MangaTool(if (editing) "Read" else "Edit", {
                        editing = !editing
                    })
                    MangaTool("More", { sheet = "More" })
                }
            }
            Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), color = MaterialTheme.colorScheme.surface.copy(alpha = .96f)) {
                if (editing) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    MangaTool("Undo", viewModel::undoStoryboardEdit, state.canUndoStoryboard && !state.mangaEditBusy)
                    MangaTool("Redo", viewModel::redoStoryboardEdit, state.canRedoStoryboard && !state.mangaEditBusy)
                    MangaTool("Text", { sheet = "Text" }, !showOriginal && !state.mangaEditBusy)
                    MangaTool("Cleanup", { selected?.let { viewModel.openImageEditor(it.messageId, it.blockId) } }, selected != null && !showOriginal && !state.mangaEditBusy)
                    MangaTool("AI", { sheet = "AI" })
                } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    MangaTool("Previous", { pages.getOrNull(pageIndex - 1)?.let { viewModel.switchPage(it.id) } }, pageIndex > 0)
                    MangaTool("${pageIndex + 1}/${pages.size.coerceAtLeast(1)}", { sheet = "Pages" })
                    MangaTool("Next", { pages.getOrNull(pageIndex + 1)?.let { viewModel.switchPage(it.id) } }, pageIndex < pages.lastIndex)
                }
            }
        }
        if (state.mangaEditBusy || review) {
            Surface(Modifier.align(Alignment.TopEnd).padding(top = if (editing || chromeVisible) 52.dp else 4.dp), shape = RoundedCornerShape(8.dp)) {
                MangaTool(if (state.mangaEditBusy) "${state.mangaEditCurrent}/${state.mangaEditTotal} · Working" else "Needs review", { sheet = "Status" })
            }
        }
    }
    if (sheet != null) {
        val sheetScroll = remember(sheet) { androidx.compose.foundation.ScrollState(0) }
        ModalBottomSheet(onDismissRequest = { sheet = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.fillMaxWidth().imePadding().heightIn(max = LocalConfiguration.current.screenHeightDp.dp * .8f)
                .verticalScroll(sheetScroll).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(sheet ?: "", style = MaterialTheme.typography.titleLarge)
                when (sheet) {
                    "Settings" -> {
                ModelChoice(
                    label = "Vision / OCR",
                    models = state.editorVisionModels,
                    selectedRef = state.editorVisionModelRef,
                    expanded = visionMenuOpen,
                    onExpandedChange = { visionMenuOpen = it },
                    onSelected = viewModel::selectEditorVisionModel,
                )
                ModelChoice(
                    label = "Translation / proofread",
                    models = state.editorTextModels,
                    selectedRef = state.editorTextModelRef,
                    expanded = translationMenuOpen,
                    onExpandedChange = { translationMenuOpen = it },
                    onSelected = viewModel::selectEditorTextModel,
                )
                ModelChoice(
                    label = "Color / image edit",
                    models = state.editorImageModels,
                    selectedRef = state.editorImageModelRef,
                    expanded = imageMenuOpen,
                    onExpandedChange = { imageMenuOpen = it },
                    onSelected = viewModel::selectEditorImageModel,
                )
                  Box {
                      InkTextButton(label = "Colorization presets ▾", compact = true,
                          enabled = !state.mangaEditBusy, onClick = { presetMenuOpen = true })
                      androidx.compose.material3.DropdownMenu(
                          expanded = presetMenuOpen, onDismissRequest = { presetMenuOpen = false },
                          modifier = Modifier.heightIn(max = modelPanelHeight),
                      ) {
                          com.ihy2ln.weaverse.core.media.MangaColorPresets.guides.forEach { (name, guide) ->
                              androidx.compose.material3.DropdownMenuItem(text = { Text(name) }, onClick = {
                                  colorGuide = guide
                                  preserveDrawing = true
                                  presetMenuOpen = false
                              })
                          }
                      }
                  }
                  Text("Presets change the palette guide, not the drawing. Choose a preset, customize it below, then Save color guide.",
                      style = MaterialTheme.typography.labelSmall, color = tokens.secondaryText)
                androidx.compose.material3.OutlinedTextField(
                    value = colorGuide,
                    onValueChange = { colorGuide = it.take(2000) },
                    label = { Text("Colorization guide / palette") },
                    supportingText = { Text("Describe palette and color treatment, e.g. muted earth tones, flat colors, red coat. Save before processing.") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.mangaEditBusy,
                    minLines = 2,
                    maxLines = 5,
                )
                FilterChip(
                    selected = preserveDrawing,
                    onClick = { preserveDrawing = !preserveDrawing },
                    enabled = !state.mangaEditBusy,
                    label = { Text("Preserve original drawing and shading") },
                )
                Text(
                    if (preserveDrawing) "Uses AI color only, retaining source brightness and existing colored pixels. Pure white and black remain unchanged; colors may be subtle. Color placement can still be imperfect."
                    else "AI-rendered output: stronger color freedom, but the model may alter faces, shading or artwork despite the prompt.",
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.secondaryText,
                )
                  Column(verticalArrangement = Arrangement.spacedBy(InkSpacing.xs)) {
                    InkTextButton(label = "Save color guide", compact = true,
                        enabled = !state.mangaEditBusy,
                        onClick = { viewModel.saveMangaColorStyle(colorGuide, preserveDrawing) })
                    InkTextButton(label = "Reset guide", compact = true,
                        enabled = !state.mangaEditBusy,
                        onClick = { colorGuide = com.ihy2ln.weaverse.core.media.MangaColorPolicy.DEFAULT_GUIDE; preserveDrawing = true })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    InkTextButton(
                        label = if (state.editorModelsRefreshing) "Refreshing…" else "Refresh model list",
                        onClick = viewModel::refreshEditorModels,
                        enabled = !state.editorModelsRefreshing,
                        compact = true,
                    )
                    if (state.editorModelsStatus.isNotBlank()) {
                        Text(
                            state.editorModelsStatus,
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.secondaryText,
                            modifier = Modifier.padding(start = InkSpacing.xs),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (!state.editorModelsRefreshing &&
                    (state.editorVisionModels.isEmpty() ||
                        state.editorTextModels.isEmpty() ||
                        state.editorImageModels.isEmpty())
                ) {
                    Text(
                          "No selectable models are cached. Save an OpenRouter key, then tap Refresh model list.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                  }
                    }
                    "AI" -> {
                        Text("Choose an action and scope. Run starts a request using your configured models.")
                        listOf("Translate", "Colorize", "Colorize + Translate").forEach { action ->
                            FilterChip(selected = aiAction == action, onClick = { aiAction = action }, label = { Text(action) })
                        }
                        Row {
                            FilterChip(selected = !chapterScope, onClick = { chapterScope = false }, label = { Text("Page") })
                            FilterChip(selected = chapterScope, enabled = initialMangaChapterId != null, onClick = { chapterScope = true }, label = { Text("Chapter") })
                        }
                        MangaTool("AI models and color guide", { sheet = "Settings" })
                        Button(enabled = !state.mangaEditBusy && (!chapterScope || initialMangaChapterId != null), onClick = {
                            sheet = null
                            if (chapterScope) initialMangaChapterId?.let { id ->
                                when (aiAction) {
                                    "Translate" -> viewModel.translateDownloadedChapter(id)
                                    "Colorize" -> viewModel.colorizeDownloadedChapter(id)
                                    else -> viewModel.colorizeAndTranslateChapter(id)
                                }
                            } else when (aiAction) {
                                "Translate" -> viewModel.translateActiveMangaPageToEnglish()
                                "Colorize" -> viewModel.colorizeActiveMangaPage()
                                else -> viewModel.colorizeAndTranslateActivePage()
                            }
                        }) { Text("Run ${aiAction.lowercase()} · ${if (chapterScope) "chapter" else "page"}") }
                    }
                    "Pages" -> pages.forEachIndexed { index, page ->
                        MangaTool("Page ${index + 1}", { viewModel.switchPage(page.id); sheet = null })
                    }
                    "Text" -> {
                        MangaTool("Add text", { selected?.let { viewModel.addTextOverlay(it.messageId, it.blockId) }; sheet = null }, selected != null)
                        Text("Tap a text box to select it; tap again for formatting. Drag its center to move it.")
                        MangaTool("Re-layout page translations", { sheet = null; viewModel.relayoutMangaTranslations() }, !state.mangaEditBusy)
                        selected?.overlays?.filter { it.source == "manga-translation" }?.forEach { overlay ->
                            MangaTool("Re-layout: ${overlay.text.take(45)}", { sheet = null; viewModel.relayoutMangaTranslations(overlay.id) }, !state.mangaEditBusy)
                        }
                    }
                    "More" -> {
                        MangaTool(if (showOriginal) "Show edited page" else "Show original page", { showOriginal = !showOriginal; sheet = null })
                        MangaTool("Pages · ${pageIndex + 1}/${pages.size.coerceAtLeast(1)}", { sheet = "Pages" })
                        MangaTool("AI settings", { sheet = "Settings" })
                        MangaTool("Status / review", { sheet = "Status" })
                        MangaTool("Reset zoom", { zoom = 1f; panX = 0f; panY = 0f; sheet = null })
                        MangaTool("Separate panels (AI)", { selected?.let { viewModel.separatePanels(it.messageId, it.blockId, useAi = true) }; sheet = null }, selected != null && !state.mangaEditBusy && !showOriginal)
                        MangaTool("Separate panels offline", { selected?.let { viewModel.separatePanels(it.messageId, it.blockId, useAi = false) }; sheet = null }, selected != null && !state.mangaEditBusy && !showOriginal)
                        MangaTool("Export PNG", { viewModel.exportStoryboardPage(); sheet = null })
                    }
                    "Status" -> {
                        Text(state.storyboardStatus.ifBlank { "No processing messages." })
                        if (state.mangaEditBusy) MangaTool("Stop processing", viewModel::stopMangaEditProcessing)
                        if (review) MangaTool("Review translation", { sheet = null; editing = true; viewModel.openActiveMangaReview() }, !state.mangaEditBusy)
                    }
                }
                MangaTool("Close", { sheet = null })
            }
        }
    }
}

@Composable
private fun MangaTool(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    TextButton(onClick = onClick, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp),
        contentPadding = PaddingValues(horizontal = 8.dp)) { Text(label, maxLines = 2) }
}

@Composable
private fun ImportedMangaPagePanel(
    panel: RpMediaRef,
    showOriginal: Boolean,
    selected: Boolean,
    editable: Boolean,
    onSelect: () -> Unit,
    onOverlayMove: (String, Float, Float) -> Unit,
    onOverlayResize: (String, Float, Float, Float, Float) -> Unit,
    onOverlayTap: (String) -> Unit,
    selectionResetKey: Int,
    onOverlaySelected: (String?) -> Unit,
) {
    val path = panel.originalPath.takeIf { showOriginal && it.isNotBlank() } ?: panel.path
    val ratio = remember(path) { imageAspectRatio(path) }
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Surface(
        modifier = Modifier.fillMaxWidth().then(if (editable && selected) Modifier.border(1.dp, borderColor) else Modifier),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(ratio)
                .clickable(onClick = onSelect),
        ) {
            AsyncImage(
                model = File(path),
                contentDescription = if (showOriginal) "Original imported manga page" else "Manga page",
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize(),
            )
            if (!showOriginal && panel.overlays.isNotEmpty()) {
                TextOverlayLayer(
                    overlays = panel.overlays,
                    editable = editable,
                    onMove = onOverlayMove,
                    onResize = onOverlayResize,
                    onTap = onOverlayTap,
                    onSelected = onOverlaySelected,
                    selectionResetKey = selectionResetKey,
                    onSelectionCleared = { onOverlaySelected(null) },
                )
            }
            if (editable && !showOriginal && panel.variantKind != "original") {
                Text(
                    panel.variantKind.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.TopEnd).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)).padding(6.dp),
                )
            }
        }
    }
}

/** Short name for one picked model, for the folded-up summary line. */
private fun editorModelLabel(models: List<ModelInfo>, ref: String): String {
    val id = ref.removePrefix("openrouter/")
    if (id.isBlank()) return "Auto"
    return models.firstOrNull { it.id == id }?.displayName ?: id.substringAfterLast('/')
}

private fun imageAspectRatio(path: String): Float {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, options)
    if (options.outWidth <= 0 || options.outHeight <= 0) return 0.7f
    return (options.outWidth.toFloat() / options.outHeight.toFloat())
}
