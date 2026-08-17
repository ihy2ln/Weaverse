package com.ihy2ln.weaverse.feature.novel.write

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.ihy2ln.weaverse.core.media.rememberMediaPickerActions
import com.ihy2ln.weaverse.core.media.ui.MediaGridCreatorDialog
import com.ihy2ln.weaverse.core.text.MediaBlock
import com.ihy2ln.weaverse.core.text.MediaGrid
import com.ihy2ln.weaverse.core.text.MediaKind
import com.ihy2ln.weaverse.core.text.toDocument
import com.ihy2ln.weaverse.core.text.wordCount
import com.ihy2ln.weaverse.core.ui.EmptyState
import com.ihy2ln.weaverse.core.ui.IconToolbarRow
import com.ihy2ln.weaverse.core.ui.SaveStatusIndicator
import com.ihy2ln.weaverse.core.ui.Spacing
import com.ihy2ln.weaverse.core.ui.ToolbarAction
import com.ihy2ln.weaverse.core.util.newId
import com.ihy2ln.weaverse.data.db.entity.MediaType
import com.ihy2ln.weaverse.data.db.entity.SceneEntity
import com.ihy2ln.weaverse.feature.novel.codex.CodexEntryEditorSheet
import com.ihy2ln.weaverse.feature.novel.codex.CodexViewModel
import com.ihy2ln.weaverse.feature.novel.write.editor.BlockEditor
import com.ihy2ln.weaverse.feature.novel.write.editor.EditorState
import com.ihy2ln.weaverse.feature.novel.write.editor.rememberAutosaveStatus
import kotlinx.coroutines.launch

/**
 * Write screen (spec §6/§9): scene picker + block editor + autosave. A fresh
 * [EditorState] is created per scene id (Phase 5's own contract — see its
 * KDoc), so switching scenes always loads that scene's own undo/redo history
 * rather than sharing one across scenes.
 *
 * Revision 02 §6's `/` command palette and AI overlay window live inside
 * [BlockEditor]/`ParagraphBlockView`/`SceneBeatBlockView` (rev02-07), driven
 * by [WriteViewModel]'s `generateSceneBeat`/[WriteViewModel.bookCodexEntries]/
 * [WriteViewModel.showSceneBeats]/[WriteViewModel.slashOverlayOpacity] — still
 * no separate AI "scope selector"/margin gutter surface (spec §9's other,
 * distinct ask). Documented in BUILD_NOTES.md.
 */
@Composable
fun WriteScreen(
    modifier: Modifier = Modifier,
    initialSceneId: String? = null,
    viewModel: WriteViewModel = hiltViewModel(),
    codexViewModel: CodexViewModel = hiltViewModel(),
) {
    val scenes by viewModel.scenes.collectAsState()
    val scene by viewModel.currentScene.collectAsState()
    val scope = rememberCoroutineScope()
    var openCodexEntryId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(initialSceneId) {
        initialSceneId?.let { viewModel.selectScene(it) }
    }

    Column(modifier = modifier.fillMaxSize()) {
        val currentSceneValue = scene
        if (currentSceneValue == null) {
            EmptyState(
                icon = Icons.Filled.Image,
                title = "No scenes yet",
                subtitle = "Create a scene from the Plan tab to start writing.",
                modifier = Modifier.fillMaxSize(),
            )
            return@Column
        }

        val editorState = remember(currentSceneValue.id) {
            EditorState(initialDocument = currentSceneValue.docJson.toDocument())
        }
        val codexEntries by viewModel.bookCodexEntries.collectAsState()
        val showSceneBeats by viewModel.showSceneBeats.collectAsState()
        val saveStatus = rememberAutosaveStatus(
            document = editorState.document,
            onSave = { document -> viewModel.saveDocument(currentSceneValue.id, document) },
        )
        val mediaPickerActions = rememberMediaPickerActions(onPicked = { uris ->
            scope.launch {
                uris.forEach { uri ->
                    val media = viewModel.importMedia(uri)
                    val kind = if (media.type == MediaType.Video) MediaKind.Video else MediaKind.Image
                    val afterId = editorState.blocks.lastOrNull()?.id.orEmpty()
                    editorState.insertBlockAfter(afterId, MediaBlock(id = newId(), mediaId = media.id, kind = kind))
                }
            }
        })
        var showMediaGridCreator by remember { mutableStateOf(false) }
        val allMedia by viewModel.mediaRepository.observeAll().collectAsState(initial = emptyList())

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ScenePicker(scenes = scenes, currentTitle = currentSceneValue.title, onSelect = viewModel::selectScene)
            SaveStatusIndicator(status = saveStatus)
        }

        IconToolbarRow(
            actions = listOf(
                ToolbarAction(Icons.Filled.Undo, "Undo", enabled = editorState.canUndo, onClick = editorState::undo),
                ToolbarAction(Icons.Filled.Redo, "Redo", enabled = editorState.canRedo, onClick = editorState::redo),
                ToolbarAction(Icons.Filled.Image, "Insert image", onClick = mediaPickerActions.pickImage),
                ToolbarAction(Icons.Filled.Videocam, "Insert video", onClick = mediaPickerActions.pickVideo),
                ToolbarAction(Icons.Filled.GridView, "Insert media grid", onClick = { showMediaGridCreator = true }),
            ),
            modifier = Modifier.padding(horizontal = Spacing.lg),
        )
        Text(
            "${editorState.document.wordCount()} words",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs),
        )

        BlockEditor(
            state = editorState,
            mediaRepository = viewModel.mediaRepository,
            writeViewModel = viewModel,
            codexEntries = codexEntries,
            showSceneBeats = showSceneBeats,
            onPickImage = mediaPickerActions.pickImage,
            onPickVideo = mediaPickerActions.pickVideo,
            onOpenCodexEntry = { entryId -> openCodexEntryId = entryId },
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = Spacing.lg),
        )

        if (showMediaGridCreator) {
            MediaGridCreatorDialog(
                allMedia = allMedia,
                onCreate = { template, items ->
                    val afterId = editorState.blocks.lastOrNull()?.id.orEmpty()
                    editorState.insertBlockAfter(afterId, MediaGrid(id = newId(), template = template, items = items))
                    showMediaGridCreator = false
                },
                onDismiss = { showMediaGridCreator = false },
            )
        }

        openCodexEntryId?.let { entryId ->
            val categories by codexViewModel.categories.collectAsState()
            val openEntry by remember(entryId) {
                codexViewModel.observeEntry(entryId)
            }.collectAsState(initial = null)
            CodexEntryEditorSheet(
                entryId = entryId,
                category = categories.firstOrNull { it.id == openEntry?.categoryId },
                viewModel = codexViewModel,
                onDismiss = { openCodexEntryId = null },
            )
        }
    }
}

@Composable
private fun ScenePicker(
    scenes: List<SceneEntity>,
    currentTitle: String,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        TextButton(onClick = { expanded = true }) {
            Text(currentTitle, style = MaterialTheme.typography.titleMedium)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            scenes.forEach { scene ->
                DropdownMenuItem(
                    text = { Text(scene.title) },
                    onClick = { onSelect(scene.id); expanded = false },
                )
            }
        }
    }
}
