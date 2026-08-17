package com.ihy2ln.weaverse.feature.notes

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ihy2ln.weaverse.core.ui.components.AudioMediaPlayer
import com.ihy2ln.weaverse.core.ui.components.InkConfirmButton
import com.ihy2ln.weaverse.core.ui.components.InkDeleteButton
import com.ihy2ln.weaverse.core.ui.components.InkTextButton
import com.ihy2ln.weaverse.core.ui.components.MediaEditAction
import com.ihy2ln.weaverse.core.ui.components.MediaEditPopup
import com.ihy2ln.weaverse.core.ui.components.MediaEditPopupConfig
import com.ihy2ln.weaverse.core.ui.components.VoiceToTextField
import com.ihy2ln.weaverse.core.ui.components.ZoomableMedia
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkTokens
import com.ihy2ln.weaverse.core.ui.util.ScrollGutterBackdrop
import com.ihy2ln.weaverse.core.ui.util.adaptiveContentPadding
import com.ihy2ln.weaverse.core.ui.util.horizontalScrollIfNeeded
import com.ihy2ln.weaverse.core.ui.util.scrollGutterPadding

/**
 * Notes editor pane (app-wide — not tied to a book). List lives in [NotesRailScreen].
 */
@Composable
fun NotesScreen(
    viewModel: NotesViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    val tokens = inkTokens()
    val contentPad = adaptiveContentPadding()

    val mediaPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris -> if (uris.isNotEmpty()) viewModel.importMedia(uris) }
    val audioPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> if (uris.isNotEmpty()) viewModel.importMedia(uris) }

    LaunchedEffect(state.mediaPickRequestId) {
        if (state.mediaPickRequestId > 0) {
            mediaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
        }
    }
    LaunchedEffect(state.audioPickRequestId) {
        if (state.audioPickRequestId > 0) {
            audioPicker.launch(arrayOf("audio/mpeg", "audio/wav", "audio/x-wav", "audio/*"))
        }
    }

    ScrollGutterBackdrop(modifier = modifier.fillMaxSize()) {
        if (state.selectedId == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Select a note or press New.\nNotes are personal — not tied to a book.",
                        color = tokens.secondaryText,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(contentPad),
                    )
                    InkConfirmButton(
                        onClick = viewModel::createNote,
                        label = "New",
                        contentDescription = "New note",
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(scrollGutterPadding())
                    .padding(vertical = contentPad),
            ) {
                VoiceToTextField(
                    value = state.title,
                    onValueChange = viewModel::onTitleChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = "Title",
                    singleLine = true,
                )
                VoiceToTextField(
                    value = state.body,
                    onValueChange = viewModel::onBodyChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 220.dp)
                        .padding(top = InkSpacing.md),
                    label = "Notes",
                    placeholder = "Speak, type, or paste…",
                    minLines = 10,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = InkSpacing.md)
                        .horizontalScrollIfNeeded(maxWidthFraction = 0.95f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(InkSpacing.sm),
                ) {
                    InkConfirmButton(
                        onClick = viewModel::saveNote,
                        label = "Save",
                        contentDescription = "Save note",
                    )
                    InkTextButton(label = "Media", onClick = viewModel::requestMediaPick)
                    InkTextButton(label = "Audio", onClick = viewModel::requestAudioPick)
                    if (state.status.isNotBlank()) {
                        Text(
                            state.status,
                            style = MaterialTheme.typography.labelMedium,
                            color = tokens.secondaryText,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
                state.media.forEach { media ->
                    NoteMediaBox(
                        media = media,
                        canPaste = state.canPasteMedia,
                        onRemove = { viewModel.removeMedia(media.blockId) },
                        onMediaEdit = { viewModel.onMediaEditAction(media.blockId, it) },
                    )
                }
                Spacer(modifier = Modifier.height(72.dp))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteMediaBox(
    media: NoteMediaUi,
    canPaste: Boolean,
    onRemove: () -> Unit,
    onMediaEdit: (MediaEditAction) -> Unit,
) {
    var menuOpen by remember(media.blockId) { mutableStateOf(false) }
    val fraction = (media.widthPercent / 100f).coerceIn(0.25f, 1f)
    Box(
        modifier = Modifier
            .fillMaxWidth(fraction)
            .padding(top = InkSpacing.md)
            .clip(RoundedCornerShape(InkSpacing.radiusSm))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(InkSpacing.radiusSm))
            .combinedClickable(
                onClick = {
                    if (media.collapsed) onMediaEdit(MediaEditAction.Uncollapse)
                },
                onLongClick = { menuOpen = true },
            ),
    ) {
        if (media.collapsed) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(inkTokens().hover)
                    .padding(horizontal = InkSpacing.sm),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    "Media collapsed · tap to uncollapse · hold for menu",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        } else if (media.isAudio) {
            AudioMediaPlayer(
                path = media.path,
                label = "Audio",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(InkSpacing.sm),
            )
        } else {
            ZoomableMedia(
                path = media.path,
                contentDescription = "Note media",
                maxHeight = 260.dp,
                contentScale = ContentScale.Fit,
                onLongPress = { menuOpen = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp),
            )
        }
        if (!media.collapsed) {
            InkDeleteButton(
                itemName = "this media",
                onConfirmedDelete = onRemove,
                modifier = Modifier.align(Alignment.TopEnd),
            )
            Text(
                "${media.widthPercent.toInt()}% · hold for menu",
                modifier = Modifier.align(Alignment.BottomEnd).padding(InkSpacing.xs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
            )
        }
        MediaEditPopup(
            expanded = menuOpen,
            onDismiss = { menuOpen = false },
            config = MediaEditPopupConfig(
                canPaste = canPaste,
                isCollapsed = media.collapsed,
                canShrink = media.widthPercent > 25f,
                canExpand = media.widthPercent < 100f,
                showStack = false,
            ),
            onAction = { action ->
                when (action) {
                    MediaEditAction.Delete -> onRemove()
                    else -> onMediaEdit(action)
                }
            },
        )
    }
}
