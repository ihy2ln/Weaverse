package com.ihy2ln.weaverse.feature.novel.codex

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ihy2ln.weaverse.core.ui.components.AudioMediaPlayer
import com.ihy2ln.weaverse.core.ui.components.InkConfirmButton
import com.ihy2ln.weaverse.core.ui.components.InkDeleteButton
import com.ihy2ln.weaverse.core.ui.components.InkOutlinedButton
import com.ihy2ln.weaverse.core.ui.components.InkToolbar
import com.ihy2ln.weaverse.core.ui.components.VoiceToTextField
import com.ihy2ln.weaverse.core.ui.components.ZoomableMedia
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkRadiusSm
import com.ihy2ln.weaverse.core.ui.theme.inkTokens
import com.ihy2ln.weaverse.core.ui.util.AlwaysScrollEndPadding
import com.ihy2ln.weaverse.core.ui.util.adaptiveContentPadding
import com.ihy2ln.weaverse.feature.roleplay.characters.CharacterDetailScreen
import com.ihy2ln.weaverse.feature.roleplay.party.InventoryScreen
import com.ihy2ln.weaverse.feature.roleplay.party.InventoryVocabulary
import kotlinx.coroutines.launch

/** The three faces of a codex entry, in the order the RPG section uses them. */
private enum class CodexEntryTab {
    Sheet,
    Ledger,
    Codex,
}

private fun tabLabel(tab: CodexEntryTab, kind: CodexEntryKind): String = when (tab) {
    CodexEntryTab.Sheet -> "Sheet"
    CodexEntryTab.Ledger -> kind.ledgerVocabulary()?.tabLabel.orEmpty()
    CodexEntryTab.Codex -> "Codex"
}

/**
 * A codex entry, in the Roster/Inventory format — but on the template its kind
 * needs. A character opens the exact RPG Roster sheet; a location, object, lore
 * entry or anything else opens a sheet built the same way with its own fields.
 * Inventory is the real RPG ledger scoped to this entry, offered only to the
 * kinds that can carry something, and Codex holds the lore-linking settings
 * (aliases, context, extra media) that have no home on a sheet.
 */
@Composable
fun CodexEntryDetailScreen(
    entryId: String,
    codexPanelExpanded: Boolean,
    onToggleCodexPanel: () -> Unit,
    viewModel: CodexEntryDetailViewModel = hiltViewModel(key = "codex-entry-$entryId"),
) {
    LaunchedEffect(entryId) { viewModel.load(entryId) }
    val state by viewModel.uiState.collectAsState()
    val tokens = inkTokens()
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var tab by rememberSaveable(entryId) { mutableStateOf(CodexEntryTab.Sheet.name) }

    Column(modifier = Modifier.fillMaxSize()) {
        Box {
            InkToolbar(
                title = state.name.ifBlank { "Codex" },
                subtitle = when (state.kind) {
                    CodexEntryKind.Character -> state.rosterSummary.ifBlank { "Roster sheet & inventory" }
                    else -> "${state.kind.label} sheet · ${state.categoryName}".trimEnd(' ', '·')
                },
                onSettings = { viewModel.onShowSettingsMenuChange(true) },
                navigationControl = {
                    IconButton(onClick = onToggleCodexPanel) {
                        Icon(
                            if (codexPanelExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (codexPanelExpanded) {
                                "Collapse Codex entries"
                            } else {
                                "Expand Codex entries"
                            },
                        )
                    }
                },
            )
            CodexEntrySettingsMenu(
                expanded = state.showSettingsMenu,
                trackMentions = state.trackMentions,
                caseSensitiveMatching = state.caseSensitiveMatching,
                onDismiss = { viewModel.onShowSettingsMenuChange(false) },
                onCopy = {
                    scope.launch { clipboard.setText(AnnotatedString(viewModel.copyText())) }
                },
                onPaste = {
                    clipboard.getText()?.text?.let(viewModel::onPaste)
                },
                onAddToRoster = viewModel::addToRoster,
                onCopyToCodex = viewModel::duplicateEntry,
                onTrackMentionsChange = viewModel::onTrackMentions,
                onCaseSensitiveMatchingChange = viewModel::onCaseSensitiveMatching,
            )
        }
        // Lore and Other hold nothing, so they get no ledger tab at all.
        val tabs = CodexEntryTab.entries.filter {
            it != CodexEntryTab.Ledger || state.carriesInventory
        }
        val current = runCatching { CodexEntryTab.valueOf(tab) }.getOrDefault(CodexEntryTab.Sheet)
            .takeIf { it in tabs } ?: CodexEntryTab.Sheet
        CodexEntryTabs(tabs = tabs, kind = state.kind, selected = current, onSelect = { tab = it.name })
        LaunchedEffect(current, state.kind) {
            if (current == CodexEntryTab.Ledger) viewModel.ensureCarrier()
        }
        if (state.loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Opening this entry's sheet…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.secondaryText,
                )
            }
            return@Column
        }
        when (current) {
            CodexEntryTab.Sheet -> CodexEntrySheetTab(state = state, viewModel = viewModel)
            CodexEntryTab.Ledger -> {
                val carrierId = state.rosterCharacterId
                val vocabulary = state.kind.ledgerVocabulary() ?: InventoryVocabulary.Carried
                if (carrierId.isNullOrBlank()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            vocabulary.preparingText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = tokens.secondaryText,
                        )
                    }
                } else {
                    InventoryScreen(
                        initialCarrierId = carrierId,
                        carrierFilterId = carrierId,
                        showGroupHeaders = false,
                        vocabulary = vocabulary,
                    )
                }
            }
            CodexEntryTab.Codex -> CodexLinkingTab(state = state, viewModel = viewModel)
        }
    }
}

/**
 * The sheet itself: a character entry opens the real RPG Roster sheet, every
 * other kind opens the template built for it in the same shape.
 */
@Composable
private fun CodexEntrySheetTab(
    state: CodexEntryDetailUiState,
    viewModel: CodexEntryDetailViewModel,
) {
    val rosterCharacterId = state.rosterCharacterId
    if (state.kind == CodexEntryKind.Character) {
        if (rosterCharacterId.isNullOrBlank()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Opening this entry's roster sheet…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = inkTokens().secondaryText,
                )
            }
        } else {
            CharacterDetailScreen(
                characterId = rosterCharacterId,
                onBack = {},
                showBackRow = false,
            )
        }
        return
    }
    CodexSheetScreen(
        kind = state.kind,
        name = state.name,
        portraitPath = state.portraitPath,
        avatarColorHex = state.avatarColorHex,
        sheet = state.sheet,
        saved = state.saved,
        statusMessage = state.statusMessage,
        onName = viewModel::onName,
        onSheet = viewModel::onSheet,
        onPickPortrait = viewModel::requestMediaPick,
        onSave = viewModel::save,
    )
}

/** Small-caps tab strip: Sheet · what it holds · Codex. */
@Composable
private fun CodexEntryTabs(
    tabs: List<CodexEntryTab>,
    kind: CodexEntryKind,
    selected: CodexEntryTab,
    onSelect: (CodexEntryTab) -> Unit,
) {
    val tokens = inkTokens()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(tokens.panel)
            .padding(horizontal = InkSpacing.sm, vertical = InkSpacing.xs),
    ) {
        tabs.forEach { entryTab ->
            val active = entryTab == selected
            Text(
                tabLabel(entryTab, kind).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                color = if (active) tokens.activePill else tokens.secondaryText,
                modifier = Modifier
                    .padding(end = InkSpacing.xs)
                    .clip(RoundedCornerShape(inkRadiusSm()))
                    .background(if (active) tokens.hover else tokens.panel)
                    .clickable { onSelect(entryTab) }
                    .padding(horizontal = InkSpacing.md, vertical = InkSpacing.xs),
            )
        }
    }
}

/**
 * Codex-only settings for the entry. Name and text now live on the sheet, so
 * what is left is how the entry links into prose and AI context, plus the
 * gallery of extra pictures/video/audio a sheet portrait cannot hold.
 */
@Composable
private fun CodexLinkingTab(
    state: CodexEntryDetailUiState,
    viewModel: CodexEntryDetailViewModel,
) {
    val tokens = inkTokens()
    val contentPad = adaptiveContentPadding()
    val mediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.importMedia(uris)
    }
    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.importMedia(uris)
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPad),
    ) {
        Text(
            if (state.kind == CodexEntryKind.Character) {
                "Name and entry text live on the Sheet tab — this entry follows its " +
                    "roster sheet's name and description."
            } else {
                "Name and the entry's text live on the Sheet tab; what the AI reads " +
                    "is this ${state.kind.label.lowercase()} sheet's own description."
            },
            style = MaterialTheme.typography.bodySmall,
            color = tokens.secondaryText,
        )
        VoiceToTextField(
            value = state.aliasesText,
            onValueChange = viewModel::onAliasesText,
            label = "Aliases / nicknames (comma separated)",
            singleLine = true,
            modifier = Modifier.padding(top = InkSpacing.md),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = InkSpacing.sm)
                .clickable { viewModel.onAlwaysInclude(!state.alwaysInclude) },
        ) {
            Checkbox(
                checked = state.alwaysInclude,
                onCheckedChange = viewModel::onAlwaysInclude,
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    checkmarkColor = MaterialTheme.colorScheme.onPrimary,
                    uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
            Text(
                "Always include in context",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Text(
            "Pictures, videos & audio",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = InkSpacing.lg, bottom = InkSpacing.sm),
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            InkOutlinedButton(
                label = "Add media",
                onClick = viewModel::requestMediaPick,
                modifier = Modifier.weight(1f),
            )
            InkOutlinedButton(
                label = "Add audio",
                onClick = viewModel::requestAudioPick,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = InkSpacing.sm),
            )
        }
        state.media.forEach { item ->
            Column(modifier = Modifier.padding(top = InkSpacing.sm)) {
                if (item.isAudio) {
                    AudioMediaPlayer(path = item.path, label = "Audio")
                } else {
                    ZoomableMedia(
                        path = item.path,
                        isVideo = item.isVideo,
                        contentDescription = "Codex media",
                        contentScale = ContentScale.Fit,
                    )
                }
                InkDeleteButton(
                    itemName = "this media",
                    onConfirmedDelete = { viewModel.removeMedia(item.id) },
                )
            }
        }
        if (state.statusMessage.isNotBlank()) {
            Text(
                state.statusMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = InkSpacing.sm),
            )
        }
        InkConfirmButton(
            onClick = viewModel::save,
            label = if (state.saved) "Saved" else "Save codex settings",
            contentDescription = if (state.saved) "Saved" else "Save codex settings",
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = InkSpacing.lg),
        )
        Spacer(modifier = Modifier.height(AlwaysScrollEndPadding))
    }
}

@Composable
private fun CodexEntrySettingsMenu(
    expanded: Boolean,
    trackMentions: Boolean,
    caseSensitiveMatching: Boolean,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    onAddToRoster: () -> Unit,
    onCopyToCodex: () -> Unit,
    onTrackMentionsChange: (Boolean) -> Unit,
    onCaseSensitiveMatchingChange: (Boolean) -> Unit,
) {
    val labelColor = MaterialTheme.colorScheme.onSurface
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(text = { Text("Copy entry", color = labelColor) }, onClick = { onCopy(); onDismiss() })
        DropdownMenuItem(text = { Text("Paste into description", color = labelColor) }, onClick = { onPaste(); onDismiss() })
        HorizontalDivider(modifier = Modifier.padding(vertical = InkSpacing.xs))
        Text(
            "Copy / add to",
            style = MaterialTheme.typography.labelSmall,
            color = mutedColor,
            modifier = Modifier.padding(horizontal = InkSpacing.md, vertical = InkSpacing.xs),
        )
        DropdownMenuItem(
            text = { Text("Add to Roster", color = labelColor) },
            onClick = { onAddToRoster(); onDismiss() },
        )
        DropdownMenuItem(
            text = { Text("Copy as new Codex entry", color = labelColor) },
            onClick = { onCopyToCodex(); onDismiss() },
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = InkSpacing.xs))
        Text(
            "Tracking / matching",
            style = MaterialTheme.typography.labelSmall,
            color = mutedColor,
            modifier = Modifier.padding(horizontal = InkSpacing.md, vertical = InkSpacing.xs),
        )
        DropdownMenuItem(
            text = { Text("Track this entry by name/alias", color = labelColor) },
            trailingIcon = { Checkbox(checked = trackMentions, onCheckedChange = null) },
            onClick = { onTrackMentionsChange(!trackMentions) },
        )
        DropdownMenuItem(
            text = { Text("Case-sensitive matching", color = if (trackMentions) labelColor else labelColor.copy(alpha = 0.38f)) },
            trailingIcon = { Checkbox(checked = caseSensitiveMatching, onCheckedChange = null, enabled = trackMentions) },
            onClick = { if (trackMentions) onCaseSensitiveMatchingChange(!caseSensitiveMatching) },
            enabled = trackMentions,
        )
    }
}
