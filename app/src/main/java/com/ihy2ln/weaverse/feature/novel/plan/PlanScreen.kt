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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import com.ihy2ln.weaverse.core.ui.theme.inkRadiusMd
import com.ihy2ln.weaverse.core.ui.theme.inkRadiusSm
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
        InkSegmentedPill(
            options = PlanViewMode.entries.map { SegmentedOption(it.name, it.name) },
            selectedId = viewMode,
            onSelect = { viewMode = it },
            modifier = Modifier.padding(bottom = InkSpacing.md),
        )
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
            )
        }
    }
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
                        RoundedCornerShape(inkRadiusMd()),
                    )
                    .border(
                        if (selected) 2.dp else 1.5.dp,
                        if (selected) InkAccentBlue else inkTokens().hairline,
                        RoundedCornerShape(inkRadiusMd()),
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
                            modifier = Modifier.padding(start = 24.dp, bottom = InkSpacing.sm),
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
                                        RoundedCornerShape(inkRadiusMd()),
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

@Composable
private fun PlanAddTile(
    onNewScene: () -> Unit,
    onSceneBeat: () -> Unit,
    onNewChapter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(inkRadiusMd())
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
    val shape = RoundedCornerShape(inkRadiusSm())
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .clip(shape)
            .background(accent.copy(alpha = 0.14f))
            .border(1.5.dp, accent, shape)
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
