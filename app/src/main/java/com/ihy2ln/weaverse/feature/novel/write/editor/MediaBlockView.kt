package com.ihy2ln.weaverse.feature.novel.write.editor

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.ihy2ln.weaverse.core.text.MediaBlock
import com.ihy2ln.weaverse.core.text.MediaKind
import com.ihy2ln.weaverse.core.ui.components.AudioMediaPlayer
import com.ihy2ln.weaverse.core.ui.components.InkDeleteButton
import com.ihy2ln.weaverse.core.ui.components.MediaEditAction
import com.ihy2ln.weaverse.core.ui.components.MediaEditPopup
import com.ihy2ln.weaverse.core.ui.components.MediaEditPopupConfig
import com.ihy2ln.weaverse.core.ui.components.ZoomableMedia
import com.ihy2ln.weaverse.core.ui.theme.InkHairline
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkTokens
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Suppress("UNUSED_PARAMETER")
@Composable
fun MediaBlockView(
    block: MediaBlock,
    mediaPath: String?,
    selected: Boolean,
    canPaste: Boolean,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
    onWidthChange: (Float) -> Unit,
    onMoveBy: (Int) -> Unit,
    onStackAdjacent: () -> Unit = {},
    onMediaEditAction: (MediaEditAction) -> Unit = {},
    /** Kept for DocumentEditor; reorder now uses the media menu instead of long-press drag. */
    onDragRelease: (Float) -> Unit = { dy ->
        when {
            dy < -48f -> onMoveBy(-1)
            dy > 48f -> onMoveBy(1)
        }
    },
    modifier: Modifier = Modifier,
) {
    var widthPercent by remember(block.id, block.widthPercent) { mutableFloatStateOf(block.widthPercent) }
    var menuOpen by remember(block.id) { mutableStateOf(false) }
    var menuAnchor by remember(block.id) { mutableStateOf(Offset.Zero) }
    val fraction = (widthPercent / 100f).coerceIn(0.2f, 1f)
    val focusRequester = remember { FocusRequester() }
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        InkHairline
    }

    fun openMenu(at: Offset) {
        onSelect()
        menuAnchor = at
        menuOpen = true
    }

    LaunchedEffect(selected) {
        if (selected) runCatching { focusRequester.requestFocus() }
    }
    LaunchedEffect(block.widthPercent) {
        widthPercent = block.widthPercent
    }

    Box(
        modifier = modifier
            .fillMaxWidth(fraction)
            .padding(vertical = InkSpacing.sm)
            .border(if (selected) 2.dp else 1.dp, borderColor, RoundedCornerShape(InkSpacing.radiusSm))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
                } else {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0f)
                },
            )
            .pointerInput(block.id) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val down = event.changes.firstOrNull { it.changedToDown() } ?: continue
                        menuAnchor = down.position
                    }
                }
            }
            .combinedClickable(
                onClick = {
                    onSelect()
                    if (block.collapsed) onMediaEditAction(MediaEditAction.Uncollapse)
                },
                onLongClick = { openMenu(menuAnchor) },
            )
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (!selected || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Backspace, Key.Delete -> {
                        onRemove()
                        true
                    }
                    else -> false
                }
            },
    ) {
        if (block.collapsed) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(inkTokens().hover)
                    .padding(horizontal = InkSpacing.sm),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    "Media collapsed · tap to uncollapse · hold for menu",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            val hasFile = mediaPath != null && File(mediaPath).exists() && File(mediaPath).length() > 0
            when {
                hasFile && block.kind == MediaKind.Audio -> {
                    AudioMediaPlayer(
                        path = mediaPath!!,
                        label = "Audio · ${block.mediaId.take(8)}",
                        modifier = Modifier.padding(InkSpacing.sm),
                    )
                }
                hasFile -> {
                    ZoomableMedia(
                        path = mediaPath!!,
                        isVideo = block.kind == MediaKind.Video,
                        contentDescription = "Scene media",
                        maxHeight = 220.dp,
                        contentScale = ContentScale.Fit,
                        onLongPressAt = { openMenu(it) },
                    )
                }
                else -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(120.dp).background(inkTokens().hover),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = when (block.kind) {
                                MediaKind.Video -> "Video"
                                MediaKind.Audio -> "Audio · ${block.mediaId.take(8)}"
                                else -> "Image · ${block.mediaId.take(8)}"
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            InkDeleteButton(
                itemName = "this media",
                onConfirmedDelete = onRemove,
                modifier = Modifier.align(Alignment.TopEnd),
            )
            Text(
                text = if (selected) {
                    "Selected · hold for cut/paste/size"
                } else {
                    "${widthPercent.toInt()}% width"
                },
                modifier = Modifier.align(Alignment.BottomEnd).padding(InkSpacing.xs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        MediaEditPopup(
            expanded = menuOpen,
            onDismiss = { menuOpen = false },
            config = MediaEditPopupConfig(
                canPaste = canPaste,
                isCollapsed = block.collapsed,
                canShrink = block.widthPercent > 25f,
                canExpand = block.widthPercent < 100f,
                showStack = true,
            ),
            anchorOffset = menuAnchor,
            onAction = { action ->
                when (action) {
                    MediaEditAction.Delete -> onRemove()
                    MediaEditAction.Stack -> onStackAdjacent()
                    else -> onMediaEditAction(action)
                }
            },
        )
    }
}
