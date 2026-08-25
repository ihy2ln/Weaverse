package com.ihy2ln.weaverse.feature.novel.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ihy2ln.weaverse.core.ui.components.InkCard
import com.ihy2ln.weaverse.core.ui.components.InkChip
import com.ihy2ln.weaverse.core.ui.components.InkConfirmDeleteDialog
import com.ihy2ln.weaverse.core.ui.components.InkDeleteButton
import com.ihy2ln.weaverse.core.ui.components.InkOutlinedButton
import com.ihy2ln.weaverse.core.ui.components.InkSegmentedPill
import com.ihy2ln.weaverse.core.ui.components.InkTextButton
import com.ihy2ln.weaverse.core.ui.components.SegmentedOption
import com.ihy2ln.weaverse.core.ui.theme.CodexCharacters
import com.ihy2ln.weaverse.core.ui.theme.InkAccentBlue
import com.ihy2ln.weaverse.core.ui.theme.InkAccentGold
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkTokens
import com.ihy2ln.weaverse.core.ui.util.adaptiveContentPadding
import com.ihy2ln.weaverse.core.ui.util.alwaysScrollEndSpacer
import com.ihy2ln.weaverse.data.db.entities.CodexEntryEntity
import com.ihy2ln.weaverse.data.db.entities.SceneEntity
import com.ihy2ln.weaverse.feature.shell.WriteJumpKind

@Composable
fun PlanScreen(
    onWrite: (sceneId: String, kind: WriteJumpKind) -> Unit = { _, _ -> },
    viewModel: PlanViewModel = hiltViewModel(),
) {
    var viewMode by rememberSaveable { mutableStateOf(PlanViewMode.Grid.name) }
    var selectedSceneId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingDeleteSceneId by rememberSaveable { mutableStateOf<String?>(null) }
    var showStructurePicker by rememberSaveable { mutableStateOf(false) }
    var editingSceneId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingChapterId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingTarget by rememberSaveable { mutableStateOf(false) }
    var editingDateSceneId by rememberSaveable { mutableStateOf<String?>(null) }
    val state by viewModel.uiState.collectAsState()
    val focusRequester = remember { FocusRequester() }
    val contentPad = adaptiveContentPadding()

    LaunchedEffect(selectedSceneId) {
        if (selectedSceneId != null) runCatching { focusRequester.requestFocus() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPad)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val id = selectedSceneId ?: return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Backspace, Key.Delete -> {
                        pendingDeleteSceneId = id
                        true
                    }
                    else -> false
                }
            },
    ) {
        pendingDeleteSceneId?.let { id ->
            val name = state.scenes.firstOrNull { it.id == id }?.title ?: "this scene"
            InkConfirmDeleteDialog(
                itemName = name,
                onConfirm = {
                    viewModel.removeScene(id)
                    if (selectedSceneId == id) selectedSceneId = null
                    pendingDeleteSceneId = null
                },
                onDismiss = { pendingDeleteSceneId = null },
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = InkSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InkSegmentedPill(
                options = PlanViewMode.entries.map { SegmentedOption(it.name, it.name) },
                selectedId = viewMode,
                onSelect = { viewMode = it },
            )
            Spacer(modifier = Modifier.weight(1f))
            InkTextButton(label = "Structure ▾", onClick = { showStructurePicker = true })
        }
        if (showStructurePicker) {
            StructureTemplatePickerDialog(
                onSelect = { template ->
                    viewModel.applyStructureTemplate(template)
                    showStructurePicker = false
                },
                onDismiss = { showStructurePicker = false },
            )
        }
        WordCountProgress(
            wordCount = state.wordCount,
            targetWordCount = state.targetWordCount,
            onClick = { editingTarget = true },
        )
        if (editingTarget) {
            EditTargetWordCountDialog(
                initialValue = state.targetWordCount,
                onSave = {
                    viewModel.updateTargetWordCount(it)
                    editingTarget = false
                },
                onDismiss = { editingTarget = false },
            )
        }
        when (PlanViewMode.valueOf(viewMode)) {
            PlanViewMode.Grid -> PlanGridView(
                scenes = state.scenes,
                outline = state.outline,
                characters = state.characters,
                selectedSceneId = selectedSceneId,
                onSelectScene = { selectedSceneId = it },
                onRemoveScene = {
                    viewModel.removeScene(it)
                    if (selectedSceneId == it) selectedSceneId = null
                },
                onWrite = onWrite,
                onPov = viewModel::updateScenePov,
                onCharacter = viewModel::updateSceneCharacter,
                onAddNewScene = {
                    viewModel.addNewScene(selectedSceneId) { id -> selectedSceneId = id }
                },
                onAddSceneBeat = {
                    viewModel.addSceneBeat(selectedSceneId) { id ->
                        selectedSceneId = id
                        onWrite(id, WriteJumpKind.SceneBeat)
                    }
                },
                onAddNewChapter = {
                    viewModel.addNewChapter(selectedSceneId) { id -> selectedSceneId = id }
                },
                onEditSceneSummary = { editingSceneId = it },
            )
            PlanViewMode.Outline -> PlanOutlineView(
                outline = state.outline,
                characters = state.characters,
                selectedSceneId = selectedSceneId,
                onSelectScene = { selectedSceneId = it },
                onRemoveScene = {
                    viewModel.removeScene(it)
                    if (selectedSceneId == it) selectedSceneId = null
                },
                onWrite = onWrite,
                onPov = viewModel::updateScenePov,
                onCharacter = viewModel::updateSceneCharacter,
                onAddNewScene = {
                    viewModel.addNewScene(selectedSceneId) { id -> selectedSceneId = id }
                },
                onAddSceneBeat = {
                    viewModel.addSceneBeat(selectedSceneId) { id ->
                        selectedSceneId = id
                        onWrite(id, WriteJumpKind.SceneBeat)
                    }
                },
                onAddNewChapter = {
                    viewModel.addNewChapter(selectedSceneId) { id -> selectedSceneId = id }
                },
                onEditSceneSummary = { editingSceneId = it },
                onEditChapterSummary = { editingChapterId = it },
            )
            PlanViewMode.Timeline -> PlanTimelineView(
                outline = state.outline,
                selectedSceneId = selectedSceneId,
                onSelectScene = { selectedSceneId = it },
                onWrite = onWrite,
                onEditInWorldDate = { editingDateSceneId = it },
            )
        }
        editingDateSceneId?.let { id ->
            val scene = state.scenes.firstOrNull { it.id == id }
            if (scene != null) {
                EditInWorldDateDialog(
                    initialValue = scene.inWorldDate,
                    onSave = {
                        viewModel.updateSceneInWorldDate(id, it)
                        editingDateSceneId = null
                    },
                    onDismiss = { editingDateSceneId = null },
                )
            } else {
                editingDateSceneId = null
            }
        }
        editingSceneId?.let { id ->
            val scene = state.scenes.firstOrNull { it.id == id }
            if (scene != null) {
                EditSummaryDialog(
                    title = "Scene summary",
                    initialValue = scene.summary,
                    onSave = {
                        viewModel.updateSceneSummary(id, it)
                        editingSceneId = null
                    },
                    onDismiss = { editingSceneId = null },
                )
            } else {
                editingSceneId = null
            }
        }
        editingChapterId?.let { id ->
            val chapter = state.outline.flatMap { it.chapters }.map { it.chapter }.firstOrNull { it.id == id }
            if (chapter != null) {
                EditSummaryDialog(
                    title = "Chapter summary",
                    initialValue = chapter.summary,
                    onSave = {
                        viewModel.updateChapterSummary(id, it)
                        editingChapterId = null
                    },
                    onDismiss = { editingChapterId = null },
                )
            } else {
                editingChapterId = null
            }
        }
    }
}

@Composable
private fun WordCountProgress(
    wordCount: Int,
    targetWordCount: Int,
    onClick: () -> Unit,
) {
    val tokens = inkTokens()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(bottom = InkSpacing.md),
    ) {
        if (targetWordCount > 0) {
            val fraction = (wordCount.toFloat() / targetWordCount).coerceIn(0f, 1f)
            val percent = (fraction * 100).toInt()
            Text(
                "${"%,d".format(wordCount)} / ${"%,d".format(targetWordCount)} words · $percent%",
                style = MaterialTheme.typography.labelSmall,
                color = tokens.secondaryText,
            )
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = InkSpacing.xxs)
                    .clip(RoundedCornerShape(999.dp)),
            )
        } else {
            Text(
                "${"%,d".format(wordCount)} words · set a goal",
                style = MaterialTheme.typography.labelSmall,
                color = tokens.secondaryText,
            )
        }
    }
}

@Composable
private fun EditTargetWordCountDialog(
    initialValue: Int,
    onSave: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember(initialValue) {
        mutableStateOf(if (initialValue > 0) initialValue.toString() else "")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Word count goal") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { value -> text = value.filter { it.isDigit() } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Target words") },
                placeholder = { Text("e.g. 80000") },
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text.toIntOrNull() ?: 0) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun StructureTemplatePickerDialog(
    onSelect: (StoryStructureTemplate) -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = inkTokens()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chapter structure") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
            ) {
                Text(
                    "Lay out chapters as story beats from a popular framework, then fill them in.",
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.secondaryText,
                    modifier = Modifier.padding(bottom = InkSpacing.sm),
                )
                LazyColumn(verticalArrangement = Arrangement.spacedBy(InkSpacing.xs)) {
                    items(StoryStructureTemplates.all, key = { it.id }) { template ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(template) }
                                .padding(vertical = InkSpacing.sm),
                        ) {
                            Text(template.templateName, fontWeight = FontWeight.SemiBold)
                            Text(
                                template.summary,
                                style = MaterialTheme.typography.labelSmall,
                                color = tokens.secondaryText,
                            )
                            Text(
                                "${template.beats.size} chapters",
                                style = MaterialTheme.typography.labelSmall,
                                color = tokens.secondaryText,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun EditSummaryDialog(
    title: String,
    initialValue: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 8,
                placeholder = { Text("Summary") },
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(value) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun firstSceneInChapter(outline: List<PlanOutlineNode>, chapterId: String): String? =
    outline.asSequence()
        .flatMap { it.chapters.asSequence() }
        .firstOrNull { it.chapter.id == chapterId }
        ?.scenes
        ?.firstOrNull()
        ?.id

@Composable
private fun WriteJumpButton(
    sceneId: String?,
    chapterSceneId: String?,
    onWrite: (sceneId: String, kind: WriteJumpKind) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        InkTextButton(label = "Write ▾", onClick = { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (sceneId != null) {
                DropdownMenuItem(
                    text = { Text("Scene beat") },
                    onClick = {
                        open = false
                        onWrite(sceneId, WriteJumpKind.SceneBeat)
                    },
                )
            }
            if (chapterSceneId != null) {
                DropdownMenuItem(
                    text = { Text("Chapter") },
                    onClick = {
                        open = false
                        onWrite(chapterSceneId, WriteJumpKind.Chapter)
                    },
                )
            }
        }
    }
}

@Composable
private fun PlanGridView(
    scenes: List<SceneEntity>,
    outline: List<PlanOutlineNode>,
    characters: List<CodexEntryEntity>,
    selectedSceneId: String?,
    onSelectScene: (String) -> Unit,
    onRemoveScene: (String) -> Unit,
    onWrite: (sceneId: String, kind: WriteJumpKind) -> Unit,
    onPov: (String, String) -> Unit,
    onCharacter: (String, String?) -> Unit,
    onAddNewScene: () -> Unit,
    onAddSceneBeat: () -> Unit,
    onAddNewChapter: () -> Unit,
    onEditSceneSummary: (String) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = InkSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(InkSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(InkSpacing.sm),
    ) {
        items(scenes, key = { it.id }) { scene ->
            val selected = selectedSceneId == scene.id
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surface,
                        RoundedCornerShape(InkSpacing.radiusMd),
                    )
                    .border(
                        if (selected) 2.dp else 1.5.dp,
                        if (selected) InkAccentBlue else inkTokens().hairline,
                        RoundedCornerShape(InkSpacing.radiusMd),
                    )
                    .clickable { onSelectScene(scene.id) }
                    .padding(InkSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(InkSpacing.xs),
            ) {
                Text(
                    "${scene.title} — ${scene.wordCount}w",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                SceneSummaryBox(
                    scene = scene,
                    tone = SceneSummaryTone.Grid,
                    onClick = { onEditSceneSummary(scene.id) },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WriteJumpButton(
                        sceneId = scene.id,
                        chapterSceneId = firstSceneInChapter(outline, scene.chapterId) ?: scene.id,
                        onWrite = onWrite,
                    )
                    InkDeleteButton(
                        itemName = scene.title,
                        onConfirmedDelete = { onRemoveScene(scene.id) },
                    )
                }
                ScenePovControls(
                    scene = scene,
                    characters = characters,
                    onPov = onPov,
                    onCharacter = onCharacter,
                )
            }
        }
        item(key = "__plan_add") {
            PlanAddTile(
                onNewScene = onAddNewScene,
                onSceneBeat = onAddSceneBeat,
                onNewChapter = onAddNewChapter,
            )
        }
        alwaysScrollEndSpacer()
    }
}

@Composable
private fun PlanOutlineView(
    outline: List<PlanOutlineNode>,
    characters: List<CodexEntryEntity>,
    selectedSceneId: String?,
    onSelectScene: (String) -> Unit,
    onRemoveScene: (String) -> Unit,
    onWrite: (sceneId: String, kind: WriteJumpKind) -> Unit,
    onPov: (String, String) -> Unit,
    onCharacter: (String, String?) -> Unit,
    onAddNewScene: () -> Unit,
    onAddSceneBeat: () -> Unit,
    onAddNewChapter: () -> Unit,
    onEditSceneSummary: (String) -> Unit,
    onEditChapterSummary: (String) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        outline.forEach { node ->
            item(key = node.act.id) {
                Text(node.act.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = InkSpacing.sm))
            }
            node.chapters.forEach { chapterNode ->
                item(key = chapterNode.chapter.id) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, bottom = InkSpacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${chapterNode.chapter.title} — ${chapterNode.scenes.sumOf { it.wordCount }} words",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                        WriteJumpButton(
                            sceneId = chapterNode.scenes.firstOrNull()?.id,
                            chapterSceneId = chapterNode.scenes.firstOrNull()?.id,
                            onWrite = onWrite,
                        )
                    }
                    if (chapterNode.chapter.summary.isNotBlank()) {
                        Text(
                            "    ${chapterNode.chapter.summary}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(start = 24.dp, bottom = InkSpacing.sm)
                                .clickable { onEditChapterSummary(chapterNode.chapter.id) },
                        )
                    } else {
                        Text(
                            "    + Add chapter summary",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(start = 24.dp, bottom = InkSpacing.sm)
                                .clickable { onEditChapterSummary(chapterNode.chapter.id) },
                        )
                    }
                }
                items(chapterNode.scenes, key = { it.id }) { scene ->
                    val selected = selectedSceneId == scene.id
                    InkCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, bottom = InkSpacing.md)
                            .clickable { onSelectScene(scene.id) }
                            .then(
                                if (selected) {
                                    Modifier.border(
                                        2.dp,
                                        InkAccentGold,
                                        RoundedCornerShape(InkSpacing.radiusMd),
                                    )
                                } else {
                                    Modifier
                                },
                            ),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "• ${scene.title} (${scene.wordCount}w) — ${scene.status}",
                                modifier = Modifier.weight(1f).padding(bottom = InkSpacing.xs),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            WriteJumpButton(
                                sceneId = scene.id,
                                chapterSceneId = chapterNode.scenes.firstOrNull()?.id ?: scene.id,
                                onWrite = onWrite,
                            )
                            InkDeleteButton(
                                itemName = scene.title,
                                onConfirmedDelete = { onRemoveScene(scene.id) },
                            )
                        }
                        SceneSummaryBox(
                            scene = scene,
                            tone = SceneSummaryTone.Outline,
                            onClick = { onEditSceneSummary(scene.id) },
                        )
                        ScenePovControls(
                            scene = scene,
                            characters = characters,
                            onPov = onPov,
                            onCharacter = onCharacter,
                        )
                    }
                }
            }
        }
        item(key = "__plan_add") {
            PlanAddTile(
                onNewScene = onAddNewScene,
                onSceneBeat = onAddSceneBeat,
                onNewChapter = onAddNewChapter,
                modifier = Modifier.padding(start = 24.dp, bottom = InkSpacing.md),
            )
        }
        alwaysScrollEndSpacer()
    }
}

/** Scene entry paired with the chapter it belongs to, for display in the timeline. */
private data class TimelineRow(val chapterTitle: String, val scene: SceneEntity)

@Composable
private fun PlanTimelineView(
    outline: List<PlanOutlineNode>,
    selectedSceneId: String?,
    onSelectScene: (String) -> Unit,
    onWrite: (sceneId: String, kind: WriteJumpKind) -> Unit,
    onEditInWorldDate: (String) -> Unit,
) {
    val tokens = inkTokens()
    val allRows = outline.flatMap { node ->
        node.chapters.flatMap { chapterNode ->
            chapterNode.scenes.map { scene -> TimelineRow(chapterNode.chapter.title, scene) }
        }
    }
    val (dated, undated) = allRows.partition { it.scene.inWorldDate.isNotBlank() }
    val rows = dated.sortedBy { it.scene.inWorldDate } + undated

    if (rows.isEmpty()) {
        Text(
            "No scenes yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.secondaryText,
            modifier = Modifier.padding(InkSpacing.md),
        )
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(rows, key = { it.scene.id }) { row ->
            val scene = row.scene
            val selected = selectedSceneId == scene.id
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectScene(scene.id) }
                    .background(
                        if (selected) InkAccentBlue.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
                    )
                    .border(if (selected) 1.5.dp else 0.dp, InkAccentBlue)
                    .padding(vertical = InkSpacing.sm, horizontal = InkSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier
                        .width(96.dp)
                        .clickable { onEditInWorldDate(scene.id) },
                ) {
                    Text(
                        scene.inWorldDate.ifBlank { "Undated" },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (scene.inWorldDate.isBlank()) tokens.secondaryText else InkAccentBlue,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        scene.title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        row.chapterTitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.secondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                WriteJumpButton(
                    sceneId = scene.id,
                    chapterSceneId = scene.id,
                    onWrite = onWrite,
                )
            }
        }
        alwaysScrollEndSpacer()
    }
}

@Composable
private fun EditInWorldDateDialog(
    initialValue: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("In-world date") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Date") },
                placeholder = { Text("e.g. Day 12, or 1420-03-02") },
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(value) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun PlanAddTile(
    onNewScene: () -> Unit,
    onSceneBeat: () -> Unit,
    onNewChapter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(InkSpacing.radiusMd)
    Box(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp)
                .clip(shape)
                .background(InkAccentBlue.copy(alpha = 0.12f))
                .border(2.dp, InkAccentBlue, shape)
                .clickable { open = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add scene, scene beat, or chapter",
                tint = InkAccentBlue,
                modifier = Modifier.size(36.dp),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("New scene") },
                onClick = {
                    open = false
                    onNewScene()
                },
            )
            DropdownMenuItem(
                text = { Text("Scene beat") },
                onClick = {
                    open = false
                    onSceneBeat()
                },
            )
            DropdownMenuItem(
                text = { Text("New chapter") },
                onClick = {
                    open = false
                    onNewChapter()
                },
            )
        }
    }
}

private enum class SceneSummaryTone { Grid, Outline }

@Composable
private fun SceneSummaryBox(
    scene: SceneEntity,
    tone: SceneSummaryTone,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val accent = when (tone) {
        SceneSummaryTone.Grid -> InkAccentBlue
        SceneSummaryTone.Outline -> InkAccentGold
    }
    val maxChars = when (tone) {
        SceneSummaryTone.Grid -> SceneSummary.GRID_MAX_CHARS
        SceneSummaryTone.Outline -> SceneSummary.OUTLINE_MAX_CHARS
    }
    val maxLines = when (tone) {
        SceneSummaryTone.Grid -> 4
        SceneSummaryTone.Outline -> 10
    }
    val minHeight = when (tone) {
        SceneSummaryTone.Grid -> 64.dp
        SceneSummaryTone.Outline -> 96.dp
    }
    val shape = RoundedCornerShape(InkSpacing.radiusSm)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .clip(shape)
            .background(accent.copy(alpha = 0.14f))
            .border(1.5.dp, accent, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = InkSpacing.sm, vertical = InkSpacing.xs),
    ) {
        SceneSummaryText(
            scene = scene,
            maxChars = maxChars,
            maxLines = maxLines,
        )
    }
}

@Composable
private fun SceneSummaryText(
    scene: SceneEntity,
    maxChars: Int,
    maxLines: Int,
) {
    val summary = SceneSummary.compact(SceneSummary.source(scene), maxChars)
    Text(
        text = summary.ifBlank { "No summary yet." },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun ScenePovControls(
    scene: SceneEntity,
    characters: List<CodexEntryEntity>,
    onPov: (String, String) -> Unit,
    onCharacter: (String, String?) -> Unit,
) {
    val tokens = inkTokens()
    var expanded by rememberSaveable(scene.id) { mutableStateOf(false) }
    val selectedBase = PlanViewModel.extractPovBase(scene.pov)
    val focalName = characters.firstOrNull { it.id == scene.povCharacterId }?.name
    val summaryLabel = buildString {
        append("POV")
        if (selectedBase.isNotBlank()) append(": $selectedBase")
        if (!focalName.isNullOrBlank()) append(" · $focalName")
    }

    InkOutlinedButton(
        label = if (expanded) "Hide POV" else summaryLabel,
        onClick = { expanded = !expanded },
        modifier = Modifier.padding(top = InkSpacing.sm),
    )

    if (!expanded) return

    Text(
        "Point of view",
        style = MaterialTheme.typography.labelMedium,
        color = tokens.secondaryText,
        modifier = Modifier.padding(top = InkSpacing.sm),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(top = InkSpacing.xs),
    ) {
        PlanPovOptions.forEach { option ->
            val selected = selectedBase.equals(option, ignoreCase = true)
            InkOutlinedButton(
                label = if (selected) "✓ $option" else option,
                onClick = { onPov(scene.id, option) },
                modifier = Modifier.padding(end = InkSpacing.xs),
            )
        }
    }
    Text(
        "Focal character",
        style = MaterialTheme.typography.labelMedium,
        color = tokens.secondaryText,
        modifier = Modifier.padding(top = InkSpacing.sm),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(top = InkSpacing.xs),
    ) {
        InkChip(
            label = "None",
            color = tokens.secondaryText,
            selected = scene.povCharacterId == null,
            modifier = Modifier.padding(end = InkSpacing.xs),
            onClick = { onCharacter(scene.id, null) },
        )
        characters.forEach { character ->
            InkChip(
                label = character.name,
                color = CodexCharacters,
                selected = scene.povCharacterId == character.id,
                modifier = Modifier.padding(end = InkSpacing.xs),
                onClick = { onCharacter(scene.id, character.id) },
            )
        }
    }
}
