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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.flow.distinctUntilChanged

/** Manga-first surface: tools overlay the canvas, so opening controls never moves the page. */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
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


    // Read is always non-editable. Tapping only reveals its compact navigation.
    var focusMode by rememberSaveable(chatId) { mutableStateOf(true) }
    var sheet by rememberSaveable(chatId) { mutableStateOf<String?>(null) }
    var aiAction by rememberSaveable(chatId) { mutableStateOf("Translate") }
    var chapterScope by rememberSaveable(chatId) { mutableStateOf(false) }
    var selectedTextId by remember(chatId) { mutableStateOf<String?>(null) }
    var selectionReset by remember(chatId) { mutableStateOf(0) }
    LaunchedEffect(state.imageEditor) { if (state.imageEditor != null) focusMode = true }
    // Explicit translation entrypoints configure only; they never launch a paid job.
    LaunchedEffect(initialEditorAction) {
        if (initialEditorAction in listOf("TranslatePage", "TranslateChapter", "ColorPage", "ColorChapter", "ColorTranslateChapter")) {
            aiAction = when {
                initialEditorAction == "ColorTranslateChapter" -> "Colorize + Translate"
                initialEditorAction?.startsWith("Color") == true -> "Colorize"
                else -> "Translate"
            }
            focusMode = false
            sheet = "AI"
            chapterScope = initialEditorAction?.endsWith("Chapter") == true
        }
    }
    val pages = remember(state.pages) { state.pages.sortedBy { it.order } }
    val panelsByPage = remember(state.mangaPagePanels) { state.mangaPagePanels.groupBy { it.pageId } }
    val readerPages = remember(pages, panelsByPage) { pages.filter { !panelsByPage[it.id].isNullOrEmpty() } }
    val pageIndex = pages.indexOfFirst { it.id == state.activePageId }.coerceAtLeast(0)
    val selected = state.mediaPanels.firstOrNull { "${it.messageId}::${it.blockId}" == state.selectedMediaKey }
        ?: state.mediaPanels.firstOrNull()
    val pageScroll = rememberLazyListState()
    var jumpToPage by rememberSaveable(chatId) { mutableStateOf<String?>(initialPageId ?: "") }
    LaunchedEffect(jumpToPage, readerPages.map { it.id }) {
        if (jumpToPage != null && readerPages.isNotEmpty()) {
            val target = readerPages.indexOfFirst { it.id == jumpToPage }.coerceAtLeast(0)
            pageScroll.scrollToItem(target)
            viewModel.focusImportedMangaPage(readerPages[target].id)
            selectedTextId = null; selectionReset++
            jumpToPage = null
        }
    }
    LaunchedEffect(readerPages.map { it.id }) {
        snapshotFlow {
            val layout = pageScroll.layoutInfo
            if (!pageScroll.isScrollInProgress) null else layout.visibleItemsInfo.maxByOrNull {
                (minOf(it.offset + it.size, layout.viewportEndOffset) - maxOf(it.offset, layout.viewportStartOffset)).coerceAtLeast(0)
            }?.key as? String
        }.distinctUntilChanged().collect { id ->
            if (id != null && jumpToPage == null && !state.mangaEditBusy && id != state.activePageId) {
                viewModel.focusImportedMangaPage(id)
                selectedTextId = null; selectionReset++
            }
        }
    }
    var zoom by rememberSaveable(chatId) { mutableFloatStateOf(1f) }
    var panX by rememberSaveable(chatId) { mutableFloatStateOf(0f) }
    var panY by rememberSaveable(chatId) { mutableFloatStateOf(0f) }
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
            startWithCleanup = false,
        )
        return
    }




    fun back() {
        when {
            sheet != null -> sheet = null
            else -> onBack()
        }
    }
    BackHandler(enabled = state.editingOverlay == null) { back() }
    val review = state.activePageId in state.mangaTranslationReviewPageIds
    Box(Modifier.fillMaxSize().background(Color.Black).clipToBounds()) {
        LazyColumn(
            state = pageScroll,
            contentPadding = PaddingValues(top = 48.dp, bottom = if (focusMode) 0.dp else 48.dp),
            modifier = Modifier.fillMaxSize()
                .transformable(transform, canPan = { zoom > 1f })
                .graphicsLayer { scaleX = zoom; scaleY = zoom; translationX = panX; translationY = panY },
        ) {
            items(readerPages, key = { it.id }) { page ->
              Column {
              panelsByPage[page.id].orEmpty().forEach { panel ->
                ImportedMangaPagePanel(panel = panel, showOriginal = showOriginal,
                    editable = false, selected = false,
                    onSelect = {
                        viewModel.focusImportedMangaPage(page.id)
                        viewModel.selectMedia(panel.messageId, panel.blockId)
                        focusMode = !focusMode
                    },
                    onOverlayMove = { id, x, y -> viewModel.moveTextOverlay(panel.messageId, panel.blockId, id, x, y) },
                    onOverlayResize = { id, x, y, w, h -> viewModel.resizeTextOverlay(panel.messageId, panel.blockId, id, x, y, w, h) },
                    onOverlayTap = { id -> viewModel.focusImportedMangaPage(page.id); viewModel.openOverlayEditor(panel.messageId, panel.blockId, id) },
                    selectionResetKey = selectionReset,
                    onOverlaySelected = { viewModel.focusImportedMangaPage(page.id); viewModel.selectMedia(panel.messageId, panel.blockId); selectedTextId = it },
                )
              }
              Surface(color = Color.Black,
                  contentColor = Color.White, modifier = Modifier.fillMaxWidth()) {
                  Box(Modifier.height(24.dp), contentAlignment = Alignment.Center) {
                      Text("${pages.indexOfFirst { it.id == page.id } + 1}/${pages.size}", fontSize = 12.sp, fontFamily = FontFamily.SansSerif)
                  }
              }
              }
            }
        }
        if (readerPages.isEmpty()) Text("Loading manga…", color = Color.White, modifier = Modifier.align(Alignment.Center))
        val banner = Color.Black
        val bannerText = Color.White
        Surface(Modifier.align(Alignment.TopCenter).fillMaxWidth(), color = banner, contentColor = bannerText) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MangaTool("Back", { back() })
                    if (focusMode) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        Text("READ", fontSize = 12.sp, fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold)
                        Text(state.title.ifBlank { "Manga" }, modifier = Modifier.weight(1f).padding(horizontal = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp, fontFamily = FontFamily.SansSerif)
                        MangaTool("Edit", { selected?.let { viewModel.openImageEditor(it.messageId, it.blockId) } }, selected != null && !state.mangaEditBusy)
                        MangaTool("More", { sheet = "More" })
                    }
                }
        }
        if (focusMode) Surface(Modifier.align(Alignment.BottomEnd), color = banner, contentColor = bannerText) {
            MangaTool("${pageIndex + 1}/${pages.size.coerceAtLeast(1)}", { sheet = "Pages" })
        }
        if (!focusMode) {
            Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), color = banner, contentColor = bannerText) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    MangaTool("${pageIndex + 1}/${pages.size.coerceAtLeast(1)}", { sheet = "Pages" })
                    MangaTool("Read", { focusMode = true })
                    MangaTool(if (review) "Edit · review" else "Page editor", { selected?.let { viewModel.openImageEditor(it.messageId, it.blockId) } }, selected != null && !state.mangaEditBusy)
                    MangaTool("AI", { sheet = "AI" })
                }
            }
        }
        if (!focusMode && (state.mangaEditBusy || review)) {
            Surface(Modifier.align(Alignment.TopEnd).padding(top = 52.dp), shape = RoundedCornerShape(8.dp)) {
                MangaTool(if (state.mangaEditBusy) "${state.mangaEditCurrent}/${state.mangaEditTotal} · Working" else "Edit · needs review", {
                    if (state.mangaEditBusy) sheet = "Status" else viewModel.openActiveMangaReview()
                })
            }
        }
    }
    if (sheet != null) {
        val sheetScroll = remember(sheet) { androidx.compose.foundation.ScrollState(0) }
        ModalBottomSheet(onDismissRequest = { sheet = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            dragHandle = { Box(Modifier.padding(vertical = 8.dp).size(32.dp, 4.dp).background(Color.Gray, RoundedCornerShape(2.dp))) }) {
          val heightLimit = (LocalConfiguration.current.screenHeightDp.dp * if (sheet in listOf("AI", "Settings")) .75f else .27f) - 20.dp
          Column(Modifier.fillMaxWidth().imePadding().heightIn(max = heightLimit)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(when (sheet) { "History" -> "Undo / Redo"; "Settings" -> "AI settings"; else -> sheet ?: "" }, fontSize = 16.sp, fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                MangaTool("Close", { sheet = null })
            }
            Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(sheetScroll).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
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
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Translate", "Colorize", "Colorize + Translate").forEach { action ->
                                FilterChip(selected = aiAction == action, onClick = { aiAction = action },
                                    label = { Text(action, fontSize = 13.sp, fontFamily = FontFamily.SansSerif) })
                            }
                        }
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(selected = !chapterScope, onClick = { chapterScope = false }, label = { Text("Page", fontSize = 13.sp) })
                        FilterChip(selected = chapterScope, enabled = initialMangaChapterId != null, onClick = { chapterScope = true }, label = { Text("Chapter", fontSize = 13.sp) })
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
                        }) { Text("Run", fontSize = 14.sp, fontFamily = FontFamily.SansSerif) }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            MangaTool("AI settings", { sheet = "Settings" }, modifier = Modifier.weight(1f))
                            MangaTool("Separate panels", { selected?.let { viewModel.separatePanels(it.messageId, it.blockId, useAi = true) }; sheet = null }, selected != null && !state.mangaEditBusy && !showOriginal, modifier = Modifier.weight(1f))
                            MangaTool("Edit / review", { sheet = null; selected?.let { viewModel.openImageEditor(it.messageId, it.blockId) } }, selected != null && !state.mangaEditBusy, modifier = Modifier.weight(1f))
                        }
                    }
                    "Pages" -> FlowRow(Modifier.fillMaxWidth(), maxItemsInEachRow = 5) {
                        pages.forEachIndexed { index, page ->
                            MangaTool("${index + 1}", { jumpToPage = page.id; sheet = null })
                        }
                    }
                    "More" -> {
                        MangaTool(if (showOriginal) "Show edited page" else "Show original page", { showOriginal = !showOriginal; sheet = null })
                        MangaTool("Read · hide controls", { focusMode = true; sheet = null })
                        MangaTool("Pages · ${pageIndex + 1}/${pages.size.coerceAtLeast(1)}", { sheet = "Pages" })
                        MangaTool("Reset zoom", { zoom = 1f; panX = 0f; panY = 0f; sheet = null })
                        MangaTool("Separate panels offline", { selected?.let { viewModel.separatePanels(it.messageId, it.blockId, useAi = false) }; sheet = null }, selected != null && !state.mangaEditBusy)
                        MangaTool("Export PNG", { viewModel.exportStoryboardPage(); sheet = null })
                    }
                    "Status" -> {
                        Text(state.storyboardStatus.ifBlank { "No processing messages." })
                        if (state.mangaEditBusy) MangaTool("Stop processing", viewModel::stopMangaEditProcessing)
                        if (review) MangaTool("Edit / review", { sheet = null; viewModel.openActiveMangaReview() }, !state.mangaEditBusy)
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun MangaTool(label: String, onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 48.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = LocalContentColor.current),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
        Text(label, maxLines = 2, fontSize = 14.sp, lineHeight = 18.sp, fontFamily = FontFamily.SansSerif)
    }
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
