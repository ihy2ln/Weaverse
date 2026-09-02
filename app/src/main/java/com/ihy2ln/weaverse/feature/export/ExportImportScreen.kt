package com.ihy2ln.weaverse.feature.export

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ihy2ln.weaverse.core.ui.components.InkConfirmButton
import com.ihy2ln.weaverse.core.ui.components.InkOutlinedButton
import com.ihy2ln.weaverse.core.ui.components.InkSegmentedPill
import com.ihy2ln.weaverse.core.ui.components.SegmentedOption
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkTokens
import com.ihy2ln.weaverse.data.export.ExportFormat
import com.ihy2ln.weaverse.data.export.SceneDivider

@Composable
fun ExportImportScreen(
    modifier: Modifier = Modifier,
    viewModel: ExportImportViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val tokens = inkTokens()
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.importUri(uri)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(InkSpacing.lg),
    ) {
        Text("Import / Export", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Top-bar Import and Export for novels, roleplay, and notes. App backup stays in Settings.",
            color = tokens.secondaryText,
            modifier = Modifier.padding(bottom = InkSpacing.sm),
        )
        InkOutlinedButton(
            label = "Import file…",
            onClick = {
                importLauncher.launch(
                    arrayOf(
                        "application/json",
                        "application/zip",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        "application/msword",
                        "text/markdown",
                        "text/html",
                        "text/plain",
                        "*/*",
                    ),
                )
            },
            enabled = !state.busy,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = InkSpacing.md),
        )
        InkSegmentedPill(
            options = listOf(
                SegmentedOption(ExportTab.Novel.name, "Novel"),
                SegmentedOption(ExportTab.Roleplay.name, "Roleplay"),
                SegmentedOption(ExportTab.Notes.name, "Notes"),
            ),
            selectedId = state.tab.name,
            onSelect = { viewModel.setTab(ExportTab.valueOf(it)) },
            modifier = Modifier.padding(bottom = InkSpacing.md),
        )

        if (state.tab == ExportTab.Novel) {
            Text(state.bookTitle, fontWeight = FontWeight.SemiBold)
            InkOutlinedButton(
                label = "Toggle All Scenes",
                onClick = viewModel::toggleAllScenes,
                modifier = Modifier.padding(vertical = InkSpacing.sm),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = InkSpacing.md),
            ) {
                val grouped = state.sceneNodes.groupBy { it.actId to it.actTitle }
                grouped.forEach { (actKey, actScenes) ->
                    val (actId, actTitle) = actKey
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.toggleAct(actId) },
                    ) {
                        Checkbox(
                            checked = actScenes.all { it.selected },
                            onCheckedChange = { viewModel.toggleAct(actId) },
                            colors = inkCheckboxColors(),
                        )
                        Text(actTitle, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    }
                    actScenes.groupBy { it.chapterId to it.chapterTitle }.forEach { (chKey, scenes) ->
                        val (chapterId, chapterTitle) = chKey
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp)
                                .clickable { viewModel.toggleChapter(chapterId) },
                        ) {
                            Checkbox(
                                checked = scenes.all { it.selected },
                                onCheckedChange = { viewModel.toggleChapter(chapterId) },
                                colors = inkCheckboxColors(),
                            )
                            Text(chapterTitle, color = MaterialTheme.colorScheme.onSurface)
                        }
                        scenes.forEach { node ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 32.dp)
                                    .clickable { viewModel.toggleScene(node.sceneId) },
                            ) {
                                Checkbox(
                                    checked = node.selected,
                                    onCheckedChange = { viewModel.toggleScene(node.sceneId) },
                                    colors = inkCheckboxColors(),
                                )
                                Text(node.sceneTitle, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }

            Text("Novel file format", style = MaterialTheme.typography.labelLarge)
            Column(modifier = Modifier.padding(vertical = InkSpacing.sm)) {
                ExportFormat.entries.forEach { format ->
                    val selected = state.format == format
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setFormat(format) }
                            .padding(vertical = 2.dp),
                    ) {
                        ExclusiveCheck(selected = selected)
                        Text(
                            format.label,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
            if (state.format == ExportFormat.Docx) {
                Text(
                    "Not all features are supported in DOCX (minimal Word ML). Prefer JSON or Project ZIP for full round-trip.",
                    color = tokens.secondaryText,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = InkSpacing.sm),
                )
            }
            if (state.format == ExportFormat.Epub) {
                Text(
                    "EPUB is a readable book for any reader app (prose + titles). Prefer JSON or Project ZIP for a full Weaverse round-trip.",
                    color = tokens.secondaryText,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = InkSpacing.sm),
                )
            }

            Text("General options", style = MaterialTheme.typography.labelLarge)
            OptionCheck("Export Summaries", state.options.exportSummaries) {
                viewModel.updateOptions { o -> o.copy(exportSummaries = it) }
            }
            OptionCheck("Export Prose", state.options.exportProse) {
                viewModel.updateOptions { o -> o.copy(exportProse = it) }
            }
            OptionCheck("Include Act Titles", state.options.includeActTitles) {
                viewModel.updateOptions { o -> o.copy(includeActTitles = it) }
            }
            OptionCheck("Include Scene Subtitles", state.options.includeSceneSubtitles) {
                viewModel.updateOptions { o -> o.copy(includeSceneSubtitles = it) }
            }

            Text("Scene Dividers", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = InkSpacing.sm))
            SceneDivider.entries.forEach { divider ->
                val selected = state.options.sceneDivider == divider
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setDivider(divider) }
                        .padding(vertical = 2.dp),
                ) {
                    ExclusiveCheck(selected = selected)
                    Text(
                        divider.label,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }

            Text(
                "Project extras (included in JSON / ZIP; Codex / Snippets / Chats / Prompts)",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = InkSpacing.sm),
            )
            OptionCheck("Include full Codex", state.options.includeCodex) {
                viewModel.updateOptions { o -> o.copy(includeCodex = it) }
            }
            OptionCheck("Include all Snippets", state.options.includeSnippets) {
                viewModel.updateOptions { o -> o.copy(includeSnippets = it) }
            }
            OptionCheck("Include all Chats", state.options.includeChats) {
                viewModel.updateOptions { o -> o.copy(includeChats = it) }
            }
            OptionCheck("Include Prompts", state.options.includePrompts) {
                viewModel.updateOptions { o -> o.copy(includePrompts = it) }
            }
            OptionCheck("Include Roleplay data", state.options.includeRoleplay) {
                viewModel.updateOptions { o -> o.copy(includeRoleplay = it) }
            }
        } else if (state.tab == ExportTab.Roleplay) {
            Text(
                "Exports characters, personas, chats, and messages as JSON. Import restores via upsert.",
                color = tokens.secondaryText,
                modifier = Modifier.padding(bottom = InkSpacing.md),
            )
        } else {
            Text(
                "Exports the shared Notes board (every book and mode) as JSON. Import restores via upsert.",
                color = tokens.secondaryText,
                modifier = Modifier.padding(bottom = InkSpacing.md),
            )
        }

        InkConfirmButton(
            onClick = viewModel::export,
            enabled = !state.busy,
            label = if (state.busy) "Working…" else "Export",
            contentDescription = "Export",
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = InkSpacing.md),
        )
        Text(
            "Import formats",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = InkSpacing.md),
        )
        Text(
            "• Weaverse JSON / project ZIP — upserts matching IDs\n" +
                "• Novelcrafter full ZIP — always creates a new book (codex, chats, snippets, manuscript)\n" +
                "• SillyTavern ZIP / PNG card / JSONL chats — characters, world books, personas, chats\n" +
                "• Word (.docx), HTML, Markdown — new book from manuscript\n" +
                "• Notes JSON — restores the shared notes board\n" +
                "• Legacy .doc — not supported (use .docx)",
            color = tokens.secondaryText,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = InkSpacing.sm),
        )
        if (state.status.isNotBlank()) {
            Text(
                state.status,
                style = MaterialTheme.typography.bodySmall,
                color = tokens.secondaryText,
                modifier = Modifier.padding(top = InkSpacing.sm),
            )
        }
    }
}

@Composable
private fun OptionCheck(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) },
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onChange,
            colors = inkCheckboxColors(),
        )
        Text(label, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun ExclusiveCheck(selected: Boolean) {
    Icon(
        imageVector = if (selected) Icons.Default.Check else Icons.Default.CheckBoxOutlineBlank,
        contentDescription = if (selected) "Selected" else "Not selected",
        tint = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier
            .padding(12.dp)
            .size(24.dp),
    )
}

@Composable
private fun inkCheckboxColors() = CheckboxDefaults.colors(
    checkedColor = MaterialTheme.colorScheme.primary,
    uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,
    checkmarkColor = MaterialTheme.colorScheme.onPrimary,
)
