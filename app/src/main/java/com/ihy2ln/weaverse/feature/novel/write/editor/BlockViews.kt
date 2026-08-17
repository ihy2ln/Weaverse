package com.ihy2ln.weaverse.feature.novel.write.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ihy2ln.weaverse.core.text.Align
import com.ihy2ln.weaverse.core.text.CodeBlock
import com.ihy2ln.weaverse.core.text.Divider
import com.ihy2ln.weaverse.core.text.DividerStyle
import com.ihy2ln.weaverse.core.text.Heading
import com.ihy2ln.weaverse.core.text.ListItem
import com.ihy2ln.weaverse.core.text.Paragraph
import com.ihy2ln.weaverse.core.text.Quote
import com.ihy2ln.weaverse.core.text.SceneBeatBlock
import com.ihy2ln.weaverse.core.text.MentionCandidate
import com.ihy2ln.weaverse.core.text.Span
import com.ihy2ln.weaverse.core.text.applyColorToRange
import com.ihy2ln.weaverse.core.text.applyHighlightToRange
import com.ihy2ln.weaverse.core.text.applyMarkToRange
import com.ihy2ln.weaverse.core.text.toPlainText
import com.ihy2ln.weaverse.core.text.updateSpansForTextChange
import com.ihy2ln.weaverse.core.ui.CodexMentionText
import com.ihy2ln.weaverse.core.ui.ColorPickerDialog
import com.ihy2ln.weaverse.core.ui.CornerRadius
import com.ihy2ln.weaverse.core.ui.Spacing
import com.ihy2ln.weaverse.core.ui.parseHex
import com.ihy2ln.weaverse.core.ui.toHex
import com.ihy2ln.weaverse.core.util.newId
import com.ihy2ln.weaverse.data.db.entity.CodexEntryEntity
import com.ihy2ln.weaverse.feature.novel.write.WriteViewModel

private fun plainTextOf(spans: List<Span>): String = spans.joinToString(separator = "") { it.text }

/**
 * Splits into a new block on Enter and merges into the previous block on Backspace-at-0. Also
 * hosts the `/` command trigger (spec §6): when this block's text starts with `/`, the rest of
 * the text after it is the palette's search query and the block itself is the anchor the palette
 * renders below — see the class doc on [SlashCommandPalette] for why this isn't a literal
 * caret-position popup.
 */
@Composable
fun ParagraphBlockView(
    block: Paragraph,
    state: EditorState,
    writeViewModel: WriteViewModel,
    codexEntries: List<CodexEntryEntity>,
    onPickImage: () -> Unit,
    onPickVideo: () -> Unit,
) {
    val text = plainTextOf(block.spans)
    var fieldValue by remember(block.id, text) {
        mutableStateOf(TextFieldValue(text = text, selection = TextRange(text.length)))
    }
    var showCodexPicker by remember(block.id) { mutableStateOf(false) }
    var showTextColorPicker by remember(block.id) { mutableStateOf(false) }
    var showHighlightPicker by remember(block.id) { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current

    fun clearBlockText() {
        fieldValue = TextFieldValue("")
        state.replaceBlock(block.id, block.copy(spans = emptyList()))
    }

    fun handleCommand(command: SlashCommand) {
        when (command.id) {
            SlashCommands.SCENE_BEAT -> state.replaceBlock(block.id, SceneBeatBlock(id = block.id, prompt = ""))
            SlashCommands.CONTINUE_WRITING -> {
                state.replaceBlock(block.id, SceneBeatBlock(id = block.id, prompt = ""))
                writeViewModel.generateSceneBeat(
                    blockId = block.id,
                    currentSceneText = state.document.toPlainText(),
                    promptText = "",
                    outputUnit = SceneBeatOutputUnit.Words,
                    outputCount = 750,
                )
            }
            SlashCommands.INSERT_CODEX_REFERENCE -> {
                clearBlockText()
                showCodexPicker = true
            }
            SlashCommands.HEADING_1 -> state.replaceBlock(block.id, Heading(id = block.id, level = 1))
            SlashCommands.HEADING_2 -> state.replaceBlock(block.id, Heading(id = block.id, level = 2))
            SlashCommands.HEADING_3 -> state.replaceBlock(block.id, Heading(id = block.id, level = 3))
            SlashCommands.QUOTE -> state.replaceBlock(block.id, Quote(id = block.id))
            SlashCommands.BULLETED_LIST -> state.replaceBlock(block.id, ListItem(id = block.id, ordered = false))
            SlashCommands.NUMBERED_LIST -> state.replaceBlock(block.id, ListItem(id = block.id, ordered = true))
            SlashCommands.SCENE_BREAK -> state.replaceBlock(block.id, Divider(id = block.id))
            SlashCommands.ALIGN_LEFT -> state.replaceBlock(block.id, block.copy(spans = emptyList(), align = Align.Start))
            SlashCommands.ALIGN_CENTER -> state.replaceBlock(block.id, block.copy(spans = emptyList(), align = Align.Center))
            SlashCommands.ALIGN_RIGHT -> state.replaceBlock(block.id, block.copy(spans = emptyList(), align = Align.End))
            SlashCommands.INSERT_IMAGE -> { clearBlockText(); onPickImage() }
            SlashCommands.INSERT_VIDEO -> { clearBlockText(); onPickVideo() }
            else -> Unit // NeedsSelection commands: no selection model yet, palette row already says so.
        }
    }

    /** Ask AI (spec §7): seeds a new [SceneBeatBlock] after this one with the selected passage
     * substituted into the command's instruction template and starts generating immediately —
     * the same accept/retry/discard flow rev02-07 built for Scene Beat/Continue Writing, just
     * triggered from a selection instead of the `/` palette. */
    fun askAi(command: SlashCommand, selectedText: String) {
        val template = SlashCommands.selectionInstructionTemplate(command.id) ?: return
        val instruction = template.replace("{{text}}", selectedText)
        val beatId = newId()
        state.insertBlockAfter(block.id, SceneBeatBlock(id = beatId, prompt = instruction))
        writeViewModel.generateSceneBeat(
            blockId = beatId,
            currentSceneText = state.document.toPlainText(),
            promptText = instruction,
            outputUnit = SceneBeatOutputUnit.Words,
            outputCount = 750,
        )
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        BasicTextField(
            value = fieldValue,
            onValueChange = { newValue ->
                val newlineIndex = newValue.text.indexOf('\n')
                if (newlineIndex >= 0) {
                    state.splitParagraph(block.id, newlineIndex)
                } else {
                    fieldValue = newValue
                    state.replaceBlock(block.id, block.copy(spans = updateSpansForTextChange(block.spans, newValue.text)))
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .onKeyEvent { event ->
                    val isBackspaceAtStart = event.type == KeyEventType.KeyDown &&
                        event.key == Key.Backspace &&
                        fieldValue.selection.collapsed &&
                        fieldValue.selection.start == 0
                    if (isBackspaceAtStart) {
                        state.mergeWithPrevious(block.id) != null
                    } else {
                        false
                    }
                },
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = when (block.align) {
                    Align.Start -> TextAlign.Start
                    Align.Center -> TextAlign.Center
                    Align.End -> TextAlign.End
                },
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            visualTransformation = SpanVisualTransformation(block.spans),
        )

        val slashQuery = fieldValue.text.takeIf { it.startsWith("/") }?.removePrefix("/")
        if (slashQuery != null) {
            SlashCommandPalette(
                query = slashQuery,
                onCommandSelected = ::handleCommand,
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs),
            )
        }

        val selection = fieldValue.selection
        if (!selection.collapsed) {
            val start = selection.min
            val end = selection.max
            SelectionToolbar(
                onCopy = { clipboardManager.setText(AnnotatedString(fieldValue.text.substring(start, end))) },
                onCut = {
                    clipboardManager.setText(AnnotatedString(fieldValue.text.substring(start, end)))
                    val newText = fieldValue.text.removeRange(start, end)
                    fieldValue = TextFieldValue(newText, TextRange(start))
                    state.replaceBlock(block.id, block.copy(spans = updateSpansForTextChange(block.spans, newText)))
                },
                onPaste = {
                    val clip = clipboardManager.getText()?.text.orEmpty()
                    val newText = fieldValue.text.replaceRange(start, end, clip)
                    fieldValue = TextFieldValue(newText, TextRange(start + clip.length))
                    state.replaceBlock(block.id, block.copy(spans = updateSpansForTextChange(block.spans, newText)))
                },
                onDelete = {
                    val newText = fieldValue.text.removeRange(start, end)
                    fieldValue = TextFieldValue(newText, TextRange(start))
                    state.replaceBlock(block.id, block.copy(spans = updateSpansForTextChange(block.spans, newText)))
                },
                onToggleMark = { mark ->
                    state.replaceBlock(block.id, block.copy(spans = applyMarkToRange(block.spans, start, end, mark)))
                },
                onPickTextColor = { showTextColorPicker = true },
                onPickHighlight = { showHighlightPicker = true },
                onRemoveHighlight = {
                    state.replaceBlock(block.id, block.copy(spans = applyHighlightToRange(block.spans, start, end, null)))
                },
                onMoveBlockUp = { state.moveBlock(block.id, delta = -1) },
                onMoveBlockDown = { state.moveBlock(block.id, delta = 1) },
                onAskAi = { command -> askAi(command, fieldValue.text.substring(start, end)) },
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs),
            )
        }
    }

    if (showCodexPicker) {
        CodexReferencePickerDialog(
            entries = codexEntries,
            onSelect = { entry ->
                state.replaceBlock(block.id, block.copy(spans = listOf(Span(entry.name, codexEntryId = entry.id))))
                showCodexPicker = false
            },
            onDismiss = { showCodexPicker = false },
        )
    }

    if (showTextColorPicker) {
        val selection = fieldValue.selection
        ColorPickerDialog(
            initialColor = MaterialTheme.colorScheme.onSurface,
            onDismiss = { showTextColorPicker = false },
            onColorSelected = { color ->
                state.replaceBlock(block.id, block.copy(spans = applyColorToRange(block.spans, selection.min, selection.max, color.toHex())))
                showTextColorPicker = false
            },
        )
    }

    if (showHighlightPicker) {
        val selection = fieldValue.selection
        ColorPickerDialog(
            initialColor = Color.Yellow,
            onDismiss = { showHighlightPicker = false },
            onColorSelected = { color ->
                state.replaceBlock(block.id, block.copy(spans = applyHighlightToRange(block.spans, selection.min, selection.max, color.toHex())))
                showHighlightPicker = false
            },
        )
    }
}

@Composable
private fun CodexReferencePickerDialog(
    entries: List<CodexEntryEntity>,
    onSelect: (CodexEntryEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = entries.filter { it.name.contains(query, ignoreCase = true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Insert codex reference") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search entries…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
                if (filtered.isEmpty()) {
                    Text(
                        "No matching entries",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(Spacing.sm),
                    )
                } else {
                    LazyColumn(modifier = Modifier.height(240.dp)) {
                        items(items = filtered, key = { it.id }) { entry ->
                            val tint = entry.colorHex?.let(::parseHex) ?: MaterialTheme.colorScheme.primary
                            Text(
                                entry.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = tint,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = Spacing.sm)
                                    .clickable { onSelect(entry) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun HeadingBlockView(block: Heading, state: EditorState) {
    val style = when (block.level) {
        1 -> MaterialTheme.typography.headlineMedium
        2 -> MaterialTheme.typography.headlineSmall
        else -> MaterialTheme.typography.titleLarge
    }
    SimpleEditableText(
        text = plainTextOf(block.spans),
        onTextChange = { state.replaceBlock(block.id, block.copy(spans = listOf(Span(it)))) },
        textStyle = style.copy(color = MaterialTheme.colorScheme.onSurface),
        keyPrefix = block.id,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
    )
}

@Composable
fun QuoteBlockView(block: Quote, state: EditorState) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg)) {
        QuoteRule(width = 3.dp)
        SimpleEditableText(
            text = plainTextOf(block.spans),
            onTextChange = { state.replaceBlock(block.id, block.copy(spans = listOf(Span(it)))) },
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            keyPrefix = block.id,
            modifier = Modifier.fillMaxWidth().padding(start = Spacing.md),
        )
    }
}

@Composable
private fun QuoteRule(width: Dp) {
    Box(
        modifier = Modifier
            .padding(top = 4.dp)
            .height(24.dp)
            .width(width)
            .background(MaterialTheme.colorScheme.outline),
    )
}

@Composable
fun ListItemBlockView(block: ListItem, state: EditorState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Spacing.lg + (Spacing.lg * block.depth), end = Spacing.lg),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = if (block.ordered) "1." else "•",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(end = Spacing.sm),
        )
        SimpleEditableText(
            text = plainTextOf(block.spans),
            onTextChange = { state.replaceBlock(block.id, block.copy(spans = listOf(Span(it)))) },
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            keyPrefix = block.id,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun CodeBlockView(block: CodeBlock, state: EditorState) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(Spacing.md),
    ) {
        SimpleEditableText(
            text = block.text,
            onTextChange = { state.replaceBlock(block.id, block.copy(text = it)) },
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            keyPrefix = block.id,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * The `/` AI overlay window (spec §6): both `Scene Beat` and `Continue Writing` create one of
 * these — the block is the real host of the window, not a separate popup, so it persists in the
 * document like the spec's "beat blocks persist... can be hidden... excluded from word counts and
 * exports by default" requires (`Document.toPlainText()`/`MarkdownConverter` both already treat
 * `SceneBeatBlock` that way). Generation itself (streaming text, Accept/Retry/Discard, the
 * `+ Context` chip readout) is ephemeral view-model state keyed by block id
 * ([WriteViewModel.generationByBlock]) rather than living on the block — only the user's typed
 * [SceneBeatBlock.prompt] and the collapsed flag are worth persisting.
 */
@Composable
fun SceneBeatBlockView(
    block: SceneBeatBlock,
    state: EditorState,
    writeViewModel: WriteViewModel,
    onOpenCodexEntry: (String) -> Unit,
) {
    if (block.collapsed) {
        CollapsedBeatChip(onExpand = { state.replaceBlock(block.id, block.copy(collapsed = false)) })
        return
    }

    val generationByBlock by writeViewModel.generationByBlock.collectAsState()
    val generation = generationByBlock[block.id] ?: SceneBeatGenerationState()
    val opacity by writeViewModel.slashOverlayOpacity.collectAsState()
    val profiles by writeViewModel.profiles.collectAsState()
    val currentProfile by writeViewModel.currentProfile.collectAsState()
    val selectedModelId by writeViewModel.selectedModelId.collectAsState()
    val availableModels by writeViewModel.availableModels.collectAsState()

    var outputUnit by remember(block.id) { mutableStateOf(SceneBeatOutputUnit.Words) }
    var outputCount by remember(block.id) { mutableStateOf(750) }
    var contextExpanded by remember(block.id) { mutableStateOf(false) }
    var overflowExpanded by remember(block.id) { mutableStateOf(false) }
    var profileMenuExpanded by remember(block.id) { mutableStateOf(false) }
    var modelMenuExpanded by remember(block.id) { mutableStateOf(false) }
    var modelRowExpanded by remember(block.id) { mutableStateOf(false) }
    var promptHeight by remember(block.id) { mutableStateOf(64.dp) }
    val density = LocalDensity.current

    fun runGeneration() {
        writeViewModel.generateSceneBeat(
            blockId = block.id,
            currentSceneText = state.document.toPlainText(),
            promptText = block.prompt,
            outputUnit = outputUnit,
            outputCount = outputCount,
        )
    }

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
        shape = RoundedCornerShape(CornerRadius.card),
        tonalElevation = 4.dp,
        shadowElevation = 4.dp,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = opacity),
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            // Header: beat icon, label, overflow (opacity slider), Hide, delete.
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    "SCENE BEAT",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = Spacing.xs).weight(1f),
                )
                Box {
                    IconButton(onClick = { overflowExpanded = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(expanded = overflowExpanded, onDismissRequest = { overflowExpanded = false }) {
                        Column(modifier = Modifier.padding(horizontal = Spacing.md).width(220.dp)) {
                            Text("Overlay opacity", style = MaterialTheme.typography.labelMedium)
                            Slider(
                                value = opacity,
                                onValueChange = { writeViewModel.setSlashOverlayOpacity(it) },
                                valueRange = 0.3f..1f,
                            )
                        }
                    }
                }
                IconButton(onClick = { state.replaceBlock(block.id, block.copy(collapsed = true)) }) {
                    Icon(Icons.Filled.VisibilityOff, contentDescription = "Hide")
                }
                IconButton(onClick = {
                    state.removeBlock(block.id)
                    writeViewModel.forgetGeneration(block.id)
                }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete beat")
                }
            }

            Spacer(modifier = Modifier.height(Spacing.xs))

            Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(promptHeight)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        SimpleEditableText(
                            text = block.prompt,
                            onTextChange = { state.replaceBlock(block.id, block.copy(prompt = it)) },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                            keyPrefix = block.id,
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = "Start writing, or type '/' for commands…",
                        )
                    }
                    // Drag up/down to resize the prompt box — small by default so one beat
                    // doesn't dominate the scene, but a long prompt shouldn't be stuck scrolling
                    // in a tiny window either.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(14.dp)
                            .pointerInput(block.id) {
                                detectVerticalDragGestures { _, dragAmount ->
                                    promptHeight = (promptHeight + with(density) { dragAmount.toDp() }).coerceIn(40.dp, 320.dp)
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.DragHandle,
                            contentDescription = "Resize prompt box",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    }
                }
                IconButton(onClick = ::runGeneration, enabled = !generation.isGenerating) {
                    Icon(Icons.Filled.Send, contentDescription = "Generate")
                }
            }

            Spacer(modifier = Modifier.height(Spacing.sm))

            // Control row: output length, +Context, model.
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { outputUnit = outputUnit.next() }) {
                    Text("$outputCount ${outputUnit.label}", style = MaterialTheme.typography.labelMedium)
                }
                OutlinedTextField(
                    value = outputCount.toString(),
                    onValueChange = { new -> new.toIntOrNull()?.let { outputCount = it.coerceIn(1, 10000) } },
                    modifier = Modifier.width(72.dp),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.labelMedium,
                )
                TextButton(onClick = { contextExpanded = !contextExpanded }) {
                    Text("+ Context", style = MaterialTheme.typography.labelMedium)
                }
                Spacer(modifier = Modifier.weight(1f))
                // Connection settings collapse behind one small icon by default — a connection
                // profile's label and a model id can both be long, and showing them expanded at
                // all times crowded out the rest of the card (reported: "taking up less of the
                // ui"). Tap to reveal the compact profile/model pickers below.
                IconButton(onClick = { modelRowExpanded = !modelRowExpanded }) {
                    Icon(
                        Icons.Filled.SmartToy,
                        contentDescription = if (modelRowExpanded) "Hide connection settings" else "Show connection settings",
                        tint = if (modelRowExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (modelRowExpanded) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = Spacing.xxs)) {
                    Box(modifier = Modifier.weight(1f)) {
                        TextButton(
                            onClick = { profileMenuExpanded = true },
                            contentPadding = PaddingValues(horizontal = Spacing.xs),
                        ) {
                            Text(
                                currentProfile.label,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        DropdownMenu(expanded = profileMenuExpanded, onDismissRequest = { profileMenuExpanded = false }) {
                            profiles.forEach { profile ->
                                DropdownMenuItem(
                                    text = { Text(profile.label) },
                                    onClick = { writeViewModel.selectProfile(profile.id); profileMenuExpanded = false },
                                )
                            }
                        }
                    }
                }
                // A real picker sourced from this profile's own fetched model list (the same
                // call "Test connection" makes) — a hand-typed model id is a human-readable
                // guess, not necessarily the exact API slug a provider expects, and is
                // guaranteed to fail.
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { modelMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = availableModels.isNotEmpty(),
                        contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = Spacing.xxs),
                    ) {
                        val label = when {
                            selectedModelId.isNotBlank() -> selectedModelId
                            availableModels.isNotEmpty() -> "Auto (${availableModels.first().id})"
                            else -> "Fetching models for ${currentProfile.label}…"
                        }
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    DropdownMenu(expanded = modelMenuExpanded, onDismissRequest = { modelMenuExpanded = false }) {
                        availableModels.forEach { model ->
                            DropdownMenuItem(
                                text = { Text(model.displayName.ifBlank { model.id }) },
                                onClick = { writeViewModel.selectModelId(model.id); modelMenuExpanded = false },
                            )
                        }
                    }
                }
            }

            if (contextExpanded) {
                Column(modifier = Modifier.padding(top = Spacing.xs)) {
                    if (generation.contextEntries.isEmpty()) {
                        Text(
                            "No codex entries matched yet — run a generation to see what's included.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Row {
                            generation.contextEntries.forEach { chip ->
                                val tint = chip.colorHex?.let(::parseHex) ?: MaterialTheme.colorScheme.primary
                                Surface(
                                    onClick = { onOpenCodexEntry(chip.id) },
                                    shape = RoundedCornerShape(CornerRadius.pill),
                                    color = tint.copy(alpha = 0.15f),
                                    modifier = Modifier.padding(end = Spacing.xs),
                                ) {
                                    Text(
                                        chip.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = tint,
                                        modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
                                    )
                                }
                            }
                        }
                        Text(
                            "~${generation.contextTokenCount} tokens",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs)) {
                TextButton(onClick = {
                    state.replaceBlock(block.id, block.copy(prompt = ""))
                    writeViewModel.clearGeneration(block.id)
                }) {
                    Text("Clear Beat")
                }
            }

            when {
                generation.isGenerating -> {
                    Column(modifier = Modifier.padding(top = Spacing.sm)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text(
                                "Generating…",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(start = Spacing.sm),
                            )
                        }
                        if (generation.streamingText.isNotBlank()) {
                            Text(
                                generation.streamingText,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = Spacing.xs),
                            )
                        }
                    }
                }
                generation.errorMessage != null -> {
                    Column(modifier = Modifier.padding(top = Spacing.sm)) {
                        Text(
                            "Error: ${generation.errorMessage}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        TextButton(onClick = ::runGeneration) { Text("Retry") }
                    }
                }
                generation.resultText != null -> {
                    val result = generation.resultText
                    Column(modifier = Modifier.padding(top = Spacing.sm)) {
                        val mentionCandidates = remember(generation.contextEntries) {
                            generation.contextEntries.map { chip ->
                                MentionCandidate(entryId = chip.id, name = chip.name, aliases = chip.aliases)
                            }
                        }
                        CodexMentionText(
                            text = result,
                            candidates = mentionCandidates,
                            onEntryClick = onOpenCodexEntry,
                            style = MaterialTheme.typography.bodyMedium,
                            entryColor = { entryId ->
                                generation.contextEntries.firstOrNull { it.id == entryId }?.colorHex
                                    ?.let(::parseHex) ?: Color.Unspecified
                            },
                        )
                        Text(
                            "${result.trim().split(Regex("\\s+")).size} words",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = Spacing.xxs),
                        )
                        Row(modifier = Modifier.padding(top = Spacing.xs)) {
                            TextButton(onClick = {
                                val paragraphs = result.split(Regex("\n\\s*\n"))
                                    .map { it.trim() }
                                    .filter { it.isNotBlank() }
                                var afterId = block.id
                                paragraphs.forEach { paragraphText ->
                                    val newBlockId = newId()
                                    state.insertBlockAfter(afterId, Paragraph(id = newBlockId, spans = listOf(Span(paragraphText))))
                                    afterId = newBlockId
                                }
                                state.replaceBlock(block.id, block.copy(collapsed = true))
                                writeViewModel.clearGeneration(block.id)
                            }) { Text("Accept") }
                            TextButton(onClick = ::runGeneration) { Text("Retry") }
                            TextButton(onClick = { writeViewModel.clearGeneration(block.id) }) { Text("Discard") }
                        }
                    }
                }
            }
        }
    }
}

private fun SceneBeatOutputUnit.next(): SceneBeatOutputUnit {
    val values = SceneBeatOutputUnit.entries
    return values[(values.indexOf(this) + 1) % values.size]
}

@Composable
private fun CollapsedBeatChip(onExpand: () -> Unit) {
    Surface(
        modifier = Modifier
            .padding(horizontal = Spacing.lg)
            .clickable(onClick = onExpand),
        shape = RoundedCornerShape(CornerRadius.pill),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
        ) {
            Icon(
                Icons.Filled.Bolt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Text(
                "Scene Beat",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(start = Spacing.xs),
            )
        }
    }
}

@Composable
fun DividerBlockView(block: Divider, state: EditorState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (block.style == DividerStyle.SceneBreak) "***" else "———",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { state.removeBlock(block.id) }) {
            Icon(imageVector = Icons.Filled.Close, contentDescription = "Remove divider")
        }
    }
}

@Composable
private fun SimpleEditableText(
    text: String,
    onTextChange: (String) -> Unit,
    textStyle: TextStyle,
    keyPrefix: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
) {
    var fieldValue by remember(keyPrefix, text) {
        mutableStateOf(TextFieldValue(text = text, selection = TextRange(text.length)))
    }
    Box(modifier = modifier) {
        if (text.isEmpty() && placeholder != null) {
            Text(placeholder, style = textStyle, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        }
        BasicTextField(
            value = fieldValue,
            onValueChange = { newValue ->
                fieldValue = newValue
                onTextChange(newValue.text)
            },
            modifier = Modifier.fillMaxWidth(),
            textStyle = textStyle,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        )
    }
}
