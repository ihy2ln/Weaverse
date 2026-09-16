package com.ihy2ln.weaverse.feature.novel.write

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp

@Composable
fun NovelPromptComposer(model: WriteViewModel) {
    val state by model.uiState.collectAsState()
    val overlay = state.aiOverlay?.takeUnless { it.hidden } ?: return
    val settings by model.writingSettings.collectAsState()
    val actualModel by model.effectiveModel.collectAsState()
    val templates by model.templates.collectAsState()
    val models by model.availableModels.collectAsState(initial = emptyList())
    var expanded by rememberSaveable(state.sceneId) { mutableStateOf(false) }
    var actionMenu by remember { mutableStateOf(false) }
    var compare by rememberSaveable(state.sceneId) { mutableStateOf(false) }
    var original by rememberSaveable(state.sceneId) { mutableStateOf(false) }
    var templateSearch by rememberSaveable { mutableStateOf("") }
    var templateName by rememberSaveable { mutableStateOf("") }
    var modelSearch by rememberSaveable { mutableStateOf("") }
    var modelRef by remember(settings.modelRef) { mutableStateOf(settings.modelRef) }
    val clipboard = LocalClipboardManager.current
    val keyboard = with(LocalDensity.current) { WindowInsets.ime.getBottom(this).toDp() }
    val available = (LocalConfiguration.current.screenHeightDp.dp - keyboard - 96.dp).coerceAtLeast(80.dp)
    Surface(tonalElevation = 5.dp) {
        Column(Modifier.fillMaxWidth().heightIn(max = available * .75f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    TextButton(onClick = { actionMenu = true }, enabled = !overlay.isStreaming) { Text(overlay.label.ifBlank { "Custom" }) }
                    DropdownMenu(actionMenu, { actionMenu = false }) {
                        listOf("custom" to "Custom", "continue" to "Continue", "replace" to "Rewrite", "expand" to "Expand", "shorten" to "Shorten", "scene_beat" to "Scene beat").forEach { (id, label) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = { actionMenu = false; model.startSelectionAi(id, label) })
                        }
                    }
                }
                Text(if (overlay.replaceBlockIndex == null) "Insert at cursor" else "Replace selected text", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                TextButton(onClick = model::dismissAiOverlay) { Text("Hide") }
            }
            OutlinedTextField(overlay.prompt, model::updateAiPrompt, label = { Text("Instruction") }, minLines = 1,
                maxLines = if (expanded) 8 else 2, enabled = !overlay.isStreaming, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = if (overlay.isStreaming) model::cancelAiGeneration else model::runAiGeneration) { Text(if (overlay.isStreaming) "Stop" else "Generate") }
                TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Less" else "More") }
                Text("${overlay.outputWords} words", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
            }
            Text(actualModel.ifBlank { "Resolving provider/model…" }, style = MaterialTheme.typography.labelSmall)
            if (expanded) {
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    TextButton(onClick = { model.retargetCandidate(false) }, enabled = !overlay.isStreaming) { Text("Use current cursor") }
                    TextButton(onClick = { model.retargetCandidate(true) }, enabled = !overlay.isStreaming) { Text("Use selection") }
                }
                OutlinedTextField(overlay.outputWords.toString(), { model.updateOutputWords(it.filter(Char::isDigit).take(4).toIntOrNull() ?: 100) }, label = { Text("Output words (50–4000)") }, singleLine = true)
                OutlinedTextField(modelRef, { modelRef = it }, label = { Text("Novel model override: provider/model") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { model.saveWritingSettings(settings.copy(modelRef = modelRef.trim(), outputWords = overlay.outputWords), model.styleGuide.value) }) { Text("Save for this book") }
                Text("Leave blank to use the connected default model. Other workspaces keep their settings.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(modelSearch, { modelSearch = it }, label = { Text("Search cached OpenRouter models") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (modelSearch.isNotBlank()) models.filter { it.id.contains(modelSearch, true) }.take(20).forEach { item ->
                    TextButton(onClick = { modelRef = "openrouter/${item.id}" }) { Text(item.id) }
                }
                Text("Template: " + (templates.firstOrNull { it.id == overlay.promptId }?.name ?: "Action default"))
                OutlinedTextField(templateSearch, { templateSearch = it }, label = { Text("Search prompt library") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { model.selectTemplate(null) }) { Text("Use action default") }
                Column(Modifier.heightIn(max = 160.dp).verticalScroll(rememberScrollState())) {
                    templates.filter { it.name.contains(templateSearch, true) }.forEach { template ->
                        TextButton(onClick = { model.selectTemplate(template.id) }) { Text(template.name) }
                    }
                }
                OutlinedTextField(templateName, { templateName = it }, label = { Text("Template name") }, singleLine = true)
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    TextButton(onClick = { model.saveTemplate(templateName) }, enabled = templateName.isNotBlank()) { Text("Save instruction") }
                    TextButton(onClick = { model.saveTemplate(templateName, overlay.promptId) }, enabled = templateName.isNotBlank() && overlay.promptId != null) { Text("Duplicate template") }
                }
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    TextButton(onClick = model::requestBeatImage, enabled = !overlay.isStreaming) { Text("Attach image") }
                    if (overlay.imagePath != null) TextButton(onClick = model::clearBeatImage, enabled = !overlay.isStreaming) { Text("Remove image") }
                }
                overlay.imagePath?.let { Text("Selected attachment: ${java.io.File(it).name}", style = MaterialTheme.typography.bodySmall) }
                TextButton(onClick = model::previewAiContext) { Text("Preview exact context") }
                if (overlay.contextPreview.isNotBlank()) SelectionContainer { Text(overlay.contextPreview, style = MaterialTheme.typography.bodySmall) }
            }
            if (overlay.streamingText.isNotBlank()) {
                Text(if (overlay.isStreaming) "Streaming candidate…" else "Candidate · not applied", style = MaterialTheme.typography.titleSmall)
                if (compare) Row {
                    FilterChip(selected = original, onClick = { original = true }, label = { Text("Original") })
                    FilterChip(selected = !original, onClick = { original = false }, label = { Text("Candidate") })
                }
                SelectionContainer { Text(if (compare && original) overlay.sourceParagraphText.orEmpty() else overlay.streamingText,
                    Modifier.heightIn(max = 160.dp).verticalScroll(rememberScrollState())) }
                if (!overlay.isStreaming) Row(Modifier.horizontalScroll(rememberScrollState())) {
                    TextButton(onClick = model::acceptAiResult) { Text(if (overlay.replaceBlockIndex == null) "Insert" else "Replace") }
                    TextButton(onClick = model::retryAiGeneration) { Text("Retry") }
                    TextButton(onClick = { compare = !compare; original = false }) { Text("Compare") }
                    TextButton(onClick = { clipboard.setText(AnnotatedString(overlay.streamingText)) }) { Text("Copy") }
                    TextButton(onClick = model::discardAiResult) { Text("Discard") }
                }
            }
            if (overlay.candidates.isNotEmpty() && !overlay.isStreaming) Row(Modifier.horizontalScroll(rememberScrollState())) {
                overlay.candidates.forEachIndexed { index, _ -> TextButton(onClick = { model.chooseCandidate(index) }) { Text("Earlier candidate ${index + 1}") } }
            }
            if (overlay.errorMessage.isNotBlank()) Text(overlay.errorMessage, color = MaterialTheme.colorScheme.error)
            overlay.contextMeter?.let { Text(it.label, style = MaterialTheme.typography.labelSmall) }
            if (overlay.usageLog.isNotBlank()) Text(overlay.usageLog, style = MaterialTheme.typography.labelSmall)
        }
    }
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
