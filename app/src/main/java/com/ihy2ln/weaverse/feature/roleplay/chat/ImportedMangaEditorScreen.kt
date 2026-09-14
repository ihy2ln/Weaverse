package com.ihy2ln.weaverse.feature.roleplay.chat

import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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

/**
 * Dedicated, page-preserving editor for downloaded/imported manga.
 *
 * This deliberately does not share the blank-panel Storyboard composer. A
 * source page stays a full page until the user explicitly separates it, and
 * every bitmap operation creates a derived copy linked to its original.
 */
@Composable
fun ImportedMangaEditorScreen(
    chatId: String,
    onBack: () -> Unit,
    onChromeChange: (RoleplayChatChrome?) -> Unit = {},
    initialPageId: String? = null,
    initialEditorAction: String? = null,
    initialMangaChapterId: String? = null,
    viewModel: RoleplayChatViewModel = hiltViewModel(),
) {
    LaunchedEffect(chatId) {
        viewModel.bindChat(chatId)
        viewModel.setDisplayMode("roleplay")
    }
    val state by viewModel.uiState.collectAsState()
    var initialActionApplied by rememberSaveable(chatId) { mutableStateOf(false) }
    var showOriginal by rememberSaveable(chatId) { mutableStateOf(false) }

    LaunchedEffect(chatId, initialPageId, state.pages) {
        initialPageId?.takeIf { id -> state.pages.any { it.id == id } }?.let(viewModel::switchPage)
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
            "TranslatePage" -> viewModel.translateActiveMangaPageToEnglish()
            "TranslateChapter" -> initialMangaChapterId?.let(viewModel::translateDownloadedChapter)
            "ColorPage" -> viewModel.colorizeActiveMangaPage()
            "ColorChapter" -> initialMangaChapterId?.let(viewModel::colorizeDownloadedChapter)
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
            ),
        )
    }
    DisposableEffect(Unit) { onDispose { onChromeChange(null) } }

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

    val pages = remember(state.pages) { state.pages.sortedBy { it.order } }
    val pageIndex = pages.indexOfFirst { it.id == state.activePageId }.coerceAtLeast(0)
    val selected = state.mediaPanels.firstOrNull {
        "${it.messageId}::${it.blockId}" == state.selectedMediaKey
    } ?: state.mediaPanels.firstOrNull()
    val tokens = inkTokens()

    Column(modifier = Modifier.fillMaxSize().background(tokens.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = InkSpacing.md, vertical = InkSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InkTextButton(label = "‹ Library", onClick = onBack, compact = true)
            Column(modifier = Modifier.weight(1f).padding(horizontal = InkSpacing.sm)) {
                Text("Manga page editor", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    state.title.ifBlank { "Imported chapter" },
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.secondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            FilterChip(
                selected = showOriginal,
                onClick = { showOriginal = !showOriginal },
                label = { Text(if (showOriginal) "Original" else "Edited") },
            )
        }
        Text(
            "Full source pages stay intact. Translation, color and brush edits are saved as separate versions.",
            style = MaterialTheme.typography.labelSmall,
            color = tokens.secondaryText,
            modifier = Modifier.padding(horizontal = InkSpacing.md, vertical = 2.dp),
        )
        if (state.storyboardStatus.isNotBlank()) {
            Text(
                state.storyboardStatus,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = InkSpacing.md, vertical = 4.dp),
            )
        }
        if (state.mangaEditBusy) {
            val progress = state.mangaEditCurrent.toFloat() / state.mangaEditTotal.coerceAtLeast(1)
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = InkSpacing.md, vertical = 4.dp)) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${state.mangaEditAction} · ${state.mangaEditCurrent}/${state.mangaEditTotal}",
                        style = MaterialTheme.typography.labelSmall,
                    )
                    InkTextButton(label = "Stop", onClick = viewModel::stopMangaEditProcessing, compact = true)
                }
            }
        }
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = InkSpacing.sm, vertical = InkSpacing.xs),
            horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs),
        ) {
            items(pages, key = { it.id }) { page ->
                val index = pages.indexOf(page)
                FilterChip(
                    selected = page.id == state.activePageId,
                    onClick = { viewModel.switchPage(page.id) },
                    label = { Text("${index + 1}") },
                )
            }
        }
        HorizontalDivider()
        if (state.mediaPanels.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Loading the imported page…", color = tokens.secondaryText)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(InkSpacing.sm),
            ) {
                items(state.mediaPanels, key = { "${it.messageId}:${it.blockId}" }) { panel ->
                    ImportedMangaPagePanel(
                        panel = panel,
                        showOriginal = showOriginal,
                        selected = "${panel.messageId}::${panel.blockId}" == state.selectedMediaKey,
                        onSelect = { viewModel.selectMedia(panel.messageId, panel.blockId) },
                        onOverlayMove = { id, x, y ->
                            viewModel.moveTextOverlay(panel.messageId, panel.blockId, id, x, y)
                        },
                        onOverlayResize = { id, width ->
                            viewModel.resizeTextOverlay(panel.messageId, panel.blockId, id, width)
                        },
                        onOverlayTap = { id -> viewModel.openOverlayEditor(panel.messageId, panel.blockId, id) },
                    )
                }
            }
        }
        HorizontalDivider()
        Column(
            modifier = Modifier.fillMaxWidth().background(tokens.panel).padding(horizontal = InkSpacing.sm, vertical = InkSpacing.xs),
        ) {
            Text(
                "Page ${pageIndex + 1} of ${pages.size.coerceAtLeast(1)} · ${state.mediaPanels.size} editable picture(s)",
                style = MaterialTheme.typography.labelSmall,
                color = tokens.secondaryText,
            )
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InkTextButton(
                    label = "Previous",
                    onClick = { pages.getOrNull(pageIndex - 1)?.let { viewModel.switchPage(it.id) } },
                    enabled = pageIndex > 0,
                    compact = true,
                )
                InkTextButton(
                    label = "Next",
                    onClick = { pages.getOrNull(pageIndex + 1)?.let { viewModel.switchPage(it.id) } },
                    enabled = pageIndex < pages.lastIndex,
                    compact = true,
                )
                InkTextButton(
                    label = "Translate English",
                    onClick = viewModel::translateActiveMangaPageToEnglish,
                    enabled = !state.mangaEditBusy,
                    compact = true,
                )
                InkTextButton(
                    label = "Colorize B&W",
                    onClick = viewModel::colorizeActiveMangaPage,
                    enabled = !state.mangaEditBusy,
                    compact = true,
                )
                InkTextButton(
                    label = "Translate chapter",
                    onClick = { initialMangaChapterId?.let(viewModel::translateDownloadedChapter) },
                    enabled = !state.mangaEditBusy && initialMangaChapterId != null,
                    compact = true,
                )
                InkTextButton(
                    label = "Colorize chapter",
                    onClick = { initialMangaChapterId?.let(viewModel::colorizeDownloadedChapter) },
                    enabled = !state.mangaEditBusy && initialMangaChapterId != null,
                    compact = true,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs),
            ) {
                InkTextButton(
                    label = "Brush / erase",
                    onClick = { selected?.let { viewModel.openImageEditor(it.messageId, it.blockId) } },
                    enabled = selected != null && !showOriginal,
                    compact = true,
                )
                InkTextButton(
                    label = "Add text",
                    onClick = { selected?.let { viewModel.addTextOverlay(it.messageId, it.blockId) } },
                    enabled = selected != null && !showOriginal,
                    compact = true,
                )
                InkTextButton(
                    label = "Separate panels (AI)",
                    onClick = { selected?.let { viewModel.separatePanels(it.messageId, it.blockId, useAi = true) } },
                    enabled = selected != null && !showOriginal && !state.mangaEditBusy,
                    compact = true,
                )
                InkTextButton(
                    label = "Separate offline",
                    onClick = { selected?.let { viewModel.separatePanels(it.messageId, it.blockId, useAi = false) } },
                    enabled = selected != null && !showOriginal && !state.mangaEditBusy,
                    compact = true,
                )
                InkTextButton(label = "Export PNG", onClick = viewModel::exportStoryboardPage, compact = true)
            }
        }
    }
}

@Composable
private fun ImportedMangaPagePanel(
    panel: RpMediaRef,
    showOriginal: Boolean,
    selected: Boolean,
    onSelect: () -> Unit,
    onOverlayMove: (String, Float, Float) -> Unit,
    onOverlayResize: (String, Float) -> Unit,
    onOverlayTap: (String) -> Unit,
) {
    val path = panel.originalPath.takeIf { showOriginal && it.isNotBlank() } ?: panel.path
    val ratio = remember(path) { imageAspectRatio(path) }
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = InkSpacing.sm).border(2.dp, borderColor, RoundedCornerShape(6.dp)),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(ratio)
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onSelect),
        ) {
            AsyncImage(
                model = File(path),
                contentDescription = if (showOriginal) "Original imported manga page" else "Editable manga page",
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize(),
            )
            if (!showOriginal && panel.overlays.isNotEmpty()) {
                TextOverlayLayer(
                    overlays = panel.overlays,
                    editable = true,
                    onMove = onOverlayMove,
                    onResize = onOverlayResize,
                    onTap = onOverlayTap,
                )
            }
            if (!showOriginal && panel.variantKind != "original") {
                Text(
                    panel.variantKind.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.TopEnd).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)).padding(6.dp),
                )
            }
        }
    }
}

private fun imageAspectRatio(path: String): Float {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, options)
    if (options.outWidth <= 0 || options.outHeight <= 0) return 0.7f
    return (options.outWidth.toFloat() / options.outHeight.toFloat()).coerceIn(0.25f, 3f)
}
