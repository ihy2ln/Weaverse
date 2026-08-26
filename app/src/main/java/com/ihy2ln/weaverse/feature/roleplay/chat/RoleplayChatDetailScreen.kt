package com.ihy2ln.weaverse.feature.roleplay.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.ihy2ln.weaverse.data.db.entities.RpPageMeta
import com.ihy2ln.weaverse.core.ui.components.CollapsibleUsageStrip
import com.ihy2ln.weaverse.core.ui.components.EditTextAction
import com.ihy2ln.weaverse.core.ui.components.EditTextPopup
import com.ihy2ln.weaverse.core.ui.components.EditTextPopupConfig
import com.ihy2ln.weaverse.core.ui.components.InkTextButton
import com.ihy2ln.weaverse.core.ui.components.PromptCommandButtons
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
import com.ihy2ln.weaverse.core.ui.components.rememberSpeechToText
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkTokens
import com.ihy2ln.weaverse.core.ui.util.parseHexColor
import com.ihy2ln.weaverse.core.ui.util.ScrollGutterBackdrop
import com.ihy2ln.weaverse.core.ui.util.alwaysScrollEndSpacer
import com.ihy2ln.weaverse.core.ui.util.scrollGutterPadding
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
    viewModel: RoleplayChatViewModel = hiltViewModel(),
) {
    LaunchedEffect(chatId) { viewModel.bindChat(chatId) }
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
    val listState = rememberLazyListState()
    val mediaFocus = remember { FocusRequester() }

    LaunchedEffect(state.title, state.displayMode) {
        onChromeChange(
            RoleplayChatChrome(
                title = state.title.ifBlank { "Chat" },
                displayMode = state.displayMode,
                onDisplayMode = viewModel::setDisplayMode,
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

    LaunchedEffect(state.mediaPickRequestId) {
        if (state.mediaPickRequestId > 0L) {
            mediaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
        }
    }

    LaunchedEffect(state.audioPickRequestId) {
        if (state.audioPickRequestId > 0L) {
            audioPicker.launch(arrayOf("audio/*", "audio/mpeg", "audio/wav", "audio/x-wav"))
        }
    }

    LaunchedEffect(state.messages.size, state.streamingText, state.mediaPanels.size) {
        val last = state.messages.lastIndex
        if (last >= 0 && state.displayMode == "messenger") {
            runCatching { listState.animateScrollToItem(last) }
        }
    }

    LaunchedEffect(state.selectedMediaKey) {
        if (state.selectedMediaKey != null) runCatching { mediaFocus.requestFocus() }
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
                PageStrip(
                    pages = state.pages,
                    activePageId = state.activePageId,
                    onSelect = viewModel::switchPage,
                    onAddPage = viewModel::addPage,
                    onRenamePage = viewModel::renamePage,
                    onDeletePage = viewModel::deletePage,
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
                        textEmphasis = false,
                        emptyHint = "Manga canvas — add Media/Audio, then hold → Move to place on the grid. Drag corner to resize. Drop onto another picture to stack.\nPress / for AI · \\ for manual text.",
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
                        textEmphasis = true,
                        emptyHint = "DM · 3×3 · text & picture · hold → Move. Prose and pictures share an invisible snap grid.\nPress / for AI · \\ for manual text.",
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
                                Text(
                                    state.streamingText,
                                    style = compactStyle,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                        }
                    }
                }
                alwaysScrollEndSpacer()
            }
            }
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

        // Prompt entry is global: / = AI, \ = manual. Keep Media/Audio here.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = InkSpacing.md, vertical = InkSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InkTextButton(label = "Media", onClick = viewModel::requestMediaPick)
            InkTextButton(label = "Audio", onClick = viewModel::requestAudioPick)
            InkTextButton(label = "Mic", onClick = startDictateNew)
            if (state.showExtraPromptSurfaces && !promptOverlayOpen) {
                PromptCommandButtons(
                    onAi = onOpenAiPrompt,
                    onManual = onOpenManualPrompt,
                    enabled = !state.isStreaming,
                    modifier = Modifier.padding(start = InkSpacing.xs),
                )
            }
            if (state.isStreaming) {
                Text(
                    "Generating…",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(start = InkSpacing.sm),
                )
            }
        }
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
    modifier: Modifier = Modifier,
) {
    val tokens = inkTokens()
    var menuForPageId by remember { mutableStateOf<String?>(null) }
    var renamingPageId by remember { mutableStateOf<String?>(null) }
    var renameDraft by remember { mutableStateOf("") }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = InkSpacing.sm, vertical = InkSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs),
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
                        .clip(RoundedCornerShape(InkSpacing.radiusSm))
                        .background(
                            if (active) tokens.activePill else Color.Transparent,
                            RoundedCornerShape(InkSpacing.radiusSm),
                        )
                        .border(
                            1.dp,
                            if (active) Color.Transparent else tokens.hairline,
                            RoundedCornerShape(InkSpacing.radiusSm),
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
                .clip(RoundedCornerShape(InkSpacing.radiusSm))
                .border(1.dp, tokens.hairline, RoundedCornerShape(InkSpacing.radiusSm))
                .clickable { onAddPage() }
                .padding(horizontal = InkSpacing.md, vertical = 4.dp),
        )
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

@Composable
private fun MangaSnapGrid(
    panels: List<RpMediaRef>,
    selectedKey: String?,
    canPaste: Boolean,
    compactStyle: androidx.compose.ui.text.TextStyle,
    gridSize: Int = MediaGrid.SIZE,
    textEmphasis: Boolean = false,
    emptyHint: String,
    onSelect: (String, String) -> Unit,
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
    modifier: Modifier = Modifier,
) {
    val tokens = inkTokens()
    val scroll = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 12.dp, vertical = InkSpacing.sm),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(InkSpacing.radiusSm))
                .border(1.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f), RoundedCornerShape(InkSpacing.radiusSm))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        ) {
            val cellW = maxWidth / gridSize
            val cellH = maxHeight / gridSize
            // Snap grid stays active for move/resize/stack, but lines are hidden.
            if (panels.isEmpty()) {
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
) {
    var dragX by remember(panel.blockId) { mutableFloatStateOf(0f) }
    var dragY by remember(panel.blockId) { mutableFloatStateOf(0f) }
    var resizeDx by remember(panel.blockId) { mutableFloatStateOf(0f) }
    var resizeDy by remember(panel.blockId) { mutableFloatStateOf(0f) }
    var menuOpen by remember(panel.blockId) { mutableStateOf(false) }
    var moveMode by remember(panel.blockId) { mutableStateOf(false) }
    var adjustMode by remember(panel.blockId) { mutableStateOf(false) }
    val border = when {
        moveMode -> MaterialTheme.colorScheme.tertiary
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
        moveMode = false
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
            .clip(RoundedCornerShape(InkSpacing.radiusSm))
            .border(if (moveMode || selected) 2.dp else 1.dp, border, RoundedCornerShape(InkSpacing.radiusSm))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .then(
                if (moveMode) {
                    // Immediate drag only — skip combinedClickable so it cannot steal the press.
                    Modifier.pointerInput(panel.blockId, col, row, colSpan, rowSpan, panels) {
                        detectDragGestures(
                            onDragStart = {
                                onSelect()
                                menuOpen = false
                                dragX = 0f
                                dragY = 0f
                            },
                            onDragEnd = { commitMovePlacement() },
                            onDragCancel = {
                                dragX = 0f
                                dragY = 0f
                                moveMode = false
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                dragX += amount.x
                                dragY += amount.y
                            },
                        )
                    }
                } else if (adjustMode) {
                    // Pinch/pan belongs to the media itself while adjusting; tap exits.
                    Modifier
                } else {
                    Modifier.combinedClickable(
                        onClick = {
                            when {
                                panel.collapsed -> onMediaEdit(MediaEditAction.Uncollapse)
                                panel.stackedPaths.size > 1 -> onCycleStack()
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
                    onLongPress = if (moveMode) {
                        null
                    } else {
                        {
                            onSelect()
                            menuOpen = true
                        }
                    },
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
                onLongPress = if (moveMode) {
                    null
                } else {
                    {
                        onSelect()
                        menuOpen = true
                    }
                },
            )
        }
        if (!panel.collapsed && panel.overlays.isNotEmpty()) {
            TextOverlayLayer(
                overlays = panel.overlays,
                editable = !moveMode,
                onMove = { id, x, y -> onOverlayMove(id, x, y) },
                onResize = { id, w -> onOverlayResize(id, w) },
                onTap = { id -> onOverlayTap(id) },
            )
        }
        if (moveMode) {
            Text(
                "Drag to place",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(2.dp)
                    .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.9f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        if (adjustMode) {
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
        InkTextButton(
            label = "-",
            onClick = onRemove,
            modifier = Modifier.align(Alignment.TopEnd),
        )
        if (!panel.collapsed && !moveMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(2.dp)
                    .width(18.dp)
                    .height(18.dp)
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
        MediaEditPopup(
            expanded = menuOpen,
            onDismiss = { menuOpen = false },
            config = MediaEditPopupConfig(
                canPaste = canPaste,
                isCollapsed = panel.collapsed,
                canShrink = colSpan > 1 || rowSpan > 1,
                canExpand = colSpan < gridSize - col || rowSpan < gridSize - row,
                showStack = !panel.isTextTile,
                showMove = true,
                showAdjustImage = !panel.isTextTile && !panel.isAudio,
                showTextOverlay = !panel.isTextTile && !panel.isAudio,
            ),
            onAction = { action ->
                when (action) {
                    MediaEditAction.Delete -> onRemove()
                    MediaEditAction.Stack -> onStackMenu()
                    MediaEditAction.Move -> {
                        onSelect()
                        menuOpen = false
                        moveMode = true
                        dragX = 0f
                        dragY = 0f
                    }
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
                Text(
                    message.text,
                    style = compactStyle,
                    modifier = Modifier.padding(top = if (grouped) 0.dp else 2.dp),
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
                RoundedCornerShape(InkSpacing.radiusSm),
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
