package com.ihy2ln.weaverse.feature.novel.write

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ihy2ln.weaverse.core.ui.components.PromptDockChip
import com.ihy2ln.weaverse.core.ui.components.PromptDockGrid
import com.ihy2ln.weaverse.core.ui.components.PromptDockModelRow
import com.ihy2ln.weaverse.core.ui.components.PromptDockOption
import com.ihy2ln.weaverse.core.ui.components.PromptDockShell
import com.ihy2ln.weaverse.core.ui.components.PromptDockTextField
import com.ihy2ln.weaverse.core.ui.components.PromptDockWordsField
import com.ihy2ln.weaverse.core.ui.components.promptDockFieldLines
import com.ihy2ln.weaverse.core.ui.theme.inkTokens

/** Actions the composer can start, in the order they appear in its dropdown. */
private val ComposerActions = listOf(
    "custom" to "Custom",
    "continue" to "Continue",
    "replace" to "Rewrite",
    "expand" to "Expand",
    "shorten" to "Shorten",
    "scene_beat" to "Scene beat",
)

/**
 * The Novel-mode prompt dock. It stays one or two lines tall by default: the
 * generated prose itself is shown inline in the manuscript, not here.
 */
@Composable
fun NovelPromptComposer(model: WriteViewModel) {
    val state by model.uiState.collectAsState()
    val overlay = state.aiOverlay?.takeUnless { it.hidden } ?: return
    val settings by model.writingSettings.collectAsState()
    val actualModel by model.effectiveModel.collectAsState()
    val templates by model.templates.collectAsState()
    val textModels by model.textModels.collectAsState()
    val tokens = inkTokens()
    var expanded by rememberSaveable(state.sceneId) { mutableStateOf(false) }
    var actionMenu by remember { mutableStateOf(false) }
    var templateSearch by rememberSaveable { mutableStateOf("") }
    var templateName by rememberSaveable { mutableStateOf("") }
    val keyboard = with(LocalDensity.current) { WindowInsets.ime.getBottom(this).toDp() }
    val available = (LocalConfiguration.current.screenHeightDp.dp - keyboard - 96.dp).coerceAtLeast(80.dp)
    val draggedHeight by model.promptDockHeight.collectAsState()
    // A taller dock should mean a taller instruction box, not empty space.
    val promptFieldLines = promptDockFieldLines(draggedHeight, if (expanded) 5 else 2)
    PromptDockShell(
        pinnedHeightDp = draggedHeight,
        onPinnedHeightChange = model::setPromptDockHeight,
        autoMaxHeight = if (expanded) available * .6f else available * .3f,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box {
                PromptDockChip(overlay.label.ifBlank { "Custom" }, enabled = !overlay.isStreaming) { actionMenu = true }
                DropdownMenu(actionMenu, { actionMenu = false }) {
                    ComposerActions.forEach { (id, label) ->
                        DropdownMenuItem(text = { Text(label) },
                            onClick = { actionMenu = false; model.startSelectionAi(id, label) })
                    }
                }
            }
            Text(
                if (overlay.replaceBlockIndex == null) "at cursor" else "replace selection",
                Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = tokens.secondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text("W", style = MaterialTheme.typography.labelSmall, color = tokens.secondaryText)
            PromptDockWordsField(
                words = overlay.outputWords,
                enabled = !overlay.isStreaming,
                onChange = model::updateOutputWords,
            )
            PromptDockChip(if (expanded) "Less" else "More") { expanded = !expanded }
            PromptDockChip("Hide", onClick = model::dismissAiOverlay)
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PromptDockTextField(
                value = overlay.prompt,
                onValueChange = model::updateAiPrompt,
                placeholder = "Instruction",
                enabled = !overlay.isStreaming,
                maxLines = promptFieldLines,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = if (overlay.isStreaming) model::cancelAiGeneration else model::runAiGeneration,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.heightIn(min = 34.dp),
            ) { Text(if (overlay.isStreaming) "Stop" else "Generate", fontSize = 13.sp) }
        }

        PromptDockModelRow(
            current = settings.modelRef.ifBlank { actualModel },
            models = textModels,
            enabled = !overlay.isStreaming,
            onPick = { id -> saveModel(model, settings, overlay.outputWords, "openrouter/$id") },
            onUseDefault = { saveModel(model, settings, overlay.outputWords, "") },
        )

        if (expanded) {
            Row(Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                PromptDockChip("Use cursor", enabled = !overlay.isStreaming) { model.retargetCandidate(false) }
                PromptDockChip("Use selection", enabled = !overlay.isStreaming) { model.retargetCandidate(true) }
                PromptDockChip("Attach image", enabled = !overlay.isStreaming, onClick = model::requestBeatImage)
                if (overlay.imagePath != null) {
                    PromptDockChip("Remove image", enabled = !overlay.isStreaming, onClick = model::clearBeatImage)
                }
                PromptDockChip("Preview context", onClick = model::previewAiContext)
                PromptDockChip("Save as book default") {
                    model.saveWritingSettings(
                        settings.copy(modelRef = settings.modelRef, outputWords = overlay.outputWords),
                        model.styleGuide.value,
                    )
                }
            }
            overlay.imagePath?.let {
                Text("Attached: ${java.io.File(it).name}", style = MaterialTheme.typography.labelSmall,
                    color = tokens.secondaryText)
            }

            TemplatePicker(
                templates = templates,
                selectedIds = overlay.promptIds,
                search = templateSearch,
                onSearch = { templateSearch = it },
                onToggle = model::toggleTemplate,
                onClear = { model.selectTemplate(null) },
            )

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                PromptDockTextField(
                    value = templateName,
                    onValueChange = { templateName = it },
                    placeholder = "New template name",
                    modifier = Modifier.weight(1f),
                )
                PromptDockChip("Save", enabled = templateName.isNotBlank()) { model.saveTemplate(templateName) }
                PromptDockChip("Duplicate", enabled = templateName.isNotBlank() && overlay.promptId != null) {
                    model.saveTemplate(templateName, overlay.promptId)
                }
            }

            if (overlay.contextPreview.isNotBlank()) {
                SelectionContainer {
                    Text(overlay.contextPreview, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.heightIn(max = 160.dp).verticalScroll(rememberScrollState()))
                }
            }
        }

        if (overlay.errorMessage.isNotBlank()) {
            Text(overlay.errorMessage, color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall)
        }
        val meter = overlay.contextMeter?.label.orEmpty()
        val usage = overlay.usageLog
        if (meter.isNotBlank() || usage.isNotBlank()) {
            Text(listOf(meter, usage).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall, color = tokens.secondaryText,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** One place that writes the book's model override, from either menu entry. */
private fun saveModel(
    model: WriteViewModel,
    settings: com.ihy2ln.weaverse.data.db.entities.NovelWritingSettings,
    outputWords: Int,
    ref: String,
) {
    model.saveWritingSettings(settings.copy(modelRef = ref, outputWords = outputWords), model.styleGuide.value)
}

/**
 * Templates as two columns split by rules, each one a toggle: several can be on at
 * once and they are layered in the order they were ticked.
 */
@Composable
private fun TemplatePicker(
    templates: List<com.ihy2ln.weaverse.data.db.entities.PromptEntity>,
    selectedIds: List<String>,
    search: String,
    onSearch: (String) -> Unit,
    onToggle: (String) -> Unit,
    onClear: () -> Unit,
) {
    val shown = templates.filter { it.name.contains(search, true) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        PromptDockTextField(
            value = search,
            onValueChange = onSearch,
            placeholder = "Search templates",
            modifier = Modifier.weight(1f),
        )
        PromptDockChip(if (selectedIds.isEmpty()) "Action default" else "Clear ${selectedIds.size}", onClick = onClear)
    }
    PromptDockGrid(
        options = shown.map { PromptDockOption(it.id, it.name) },
        selectedIds = selectedIds,
        onSelect = onToggle,
        emptyText = "No templates match.",
    )
}

@Composable
fun NovelGuidanceDialog(model: WriteViewModel, onDismiss: () -> Unit) {
    val settings by model.writingSettings.collectAsState()
    val storedStyle by model.styleGuide.collectAsState()
    var memory by remember(settings.bookId) { mutableStateOf(settings.memory) }
    var note by remember(settings.bookId) { mutableStateOf(settings.authorNote) }
    var style by remember(settings.bookId) { mutableStateOf(storedStyle) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Book writing guidance") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("Memory keeps persistent facts. Author’s Note steers the current writing. Style guide controls voice and conventions.")
            OutlinedTextField(memory, { memory = it }, label = { Text("Memory") }, minLines = 2)
            OutlinedTextField(note, { note = it }, label = { Text("Author’s Note") }, minLines = 2)
            OutlinedTextField(style, { style = it }, label = { Text("Existing style guide") }, minLines = 2)
        }
    }, confirmButton = { TextButton(onClick = { model.saveWritingSettings(settings.copy(memory = memory, authorNote = note), style); onDismiss() }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
