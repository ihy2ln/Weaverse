package com.ihy2ln.weaverse.core.media.ui

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlignHorizontalCenter
import androidx.compose.material.icons.filled.AlignHorizontalLeft
import androidx.compose.material.icons.filled.AlignHorizontalRight
import androidx.compose.material.icons.filled.BurstMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ihy2ln.weaverse.core.media.MediaPaths
import com.ihy2ln.weaverse.core.media.MediaResize
import com.ihy2ln.weaverse.core.text.Align
import com.ihy2ln.weaverse.core.text.MediaBlock
import com.ihy2ln.weaverse.core.text.MediaKind
import com.ihy2ln.weaverse.core.ui.IconToolbarRow
import com.ihy2ln.weaverse.core.ui.Spacing
import com.ihy2ln.weaverse.core.ui.ToolbarAction
import com.ihy2ln.weaverse.data.db.entity.MediaEntity
import com.ihy2ln.weaverse.data.repo.MediaRepository

/**
 * The resizable/alignable image-or-video block (spec §7 — "must feel good,
 * this is a stated priority"). Drag either edge handle to resize live,
 * snapping to 25/33/50/66/75/100% with a haptic tick on each snap (spec §7);
 * the compact toolbar underneath covers the same resize via presets, plus
 * alignment/delete/video-specific toggles. Double-tap opens the full-screen
 * viewer ([onOpenViewer]) — Crop/Replace/Caption editing UI is deferred to
 * Phase 10 (the Write screen owns the surrounding chrome those need).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaBlockView(
    block: MediaBlock,
    mediaRepository: MediaRepository,
    onUpdate: (MediaBlock) -> Unit,
    onDelete: () -> Unit,
    onOpenViewer: (MediaBlock, MediaEntity) -> Unit,
    /** Non-null when the next block in the flow is also a [MediaBlock] — dropping one image onto
     * another creates a [com.ihy2ln.weaverse.core.text.MediaStack] per spec §8, but with no
     * drag-and-drop between blocks in this editor, an explicit "Stack with next" toolbar action
     * is the achievable substitute (see BUILD_NOTES "rev02-09"). */
    onStackWithNext: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var media by remember { mutableStateOf<MediaEntity?>(null) }
    LaunchedEffect(block.mediaId, mediaRepository) {
        media = mediaRepository.getById(block.mediaId)
    }
    var selected by remember(block.id) { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current

    // BoxScope.align(Alignment.Horizontal) doesn't exist — that overload belongs
    // to ColumnScope. BoxScope.align takes the full 2D Alignment, hence CenterStart/
    // Center/CenterEnd here rather than the Column-flavored Start/CenterHorizontally/End.
    val boxAlignment = when (block.align) {
        Align.Start -> Alignment.CenterStart
        Align.Center -> Alignment.Center
        Align.End -> Alignment.CenterEnd
    }

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.lg)) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val containerWidthPx = with(density) { maxWidth.toPx() }
            val aspectRatio = remember(media?.width, media?.height) {
                val width = media?.width
                val height = media?.height
                if (width != null && height != null && height > 0) width.toFloat() / height else 16f / 9f
            }

            // BoxWithConstraintsScope doesn't extend BoxScope, so `.align()` isn't
            // available directly on its content — a plain Box in between supplies it.
            Box(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .align(boxAlignment)
                        .fillMaxWidth(block.widthPercent / 100f)
                        .aspectRatio(aspectRatio)
                        .clip(RoundedCornerShape(8.dp))
                        .combinedClickable(
                            onClick = { selected = !selected },
                            onDoubleClick = { media?.let { onOpenViewer(block, it) } },
                        ),
                ) {
                    val entity = media
                    if (entity != null) {
                        val fileUri = remember(entity.relativePath) {
                            Uri.fromFile(MediaPaths.resolve(context, entity.relativePath))
                        }
                        when (block.kind) {
                            MediaKind.Image -> AsyncImage(
                                model = fileUri,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                            )
                            MediaKind.Video -> InlineVideoPlayer(
                                mediaId = block.mediaId,
                                uri = fileUri,
                                muted = block.muted,
                                loop = block.loop,
                                autoplay = block.autoplay,
                            )
                        }
                    }

                    if (selected) {
                        ResizeHandle(
                            isLeftEdge = true,
                            containerWidthPx = containerWidthPx,
                            widthPercent = block.widthPercent,
                            onWidthPercentChange = { onUpdate(block.copy(widthPercent = it)) },
                            onSnapTick = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                            modifier = Modifier.align(Alignment.CenterStart),
                        )
                        ResizeHandle(
                            isLeftEdge = false,
                            containerWidthPx = containerWidthPx,
                            widthPercent = block.widthPercent,
                            onWidthPercentChange = { onUpdate(block.copy(widthPercent = it)) },
                            onSnapTick = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                            modifier = Modifier.align(Alignment.CenterEnd),
                        )
                    }
                }
            }
        }

        if (selected) {
            MediaBlockToolbar(block = block, onUpdate = onUpdate, onDelete = onDelete, onStackWithNext = onStackWithNext)
        }
    }
}

/**
 * Both handles just drive the same [MediaBlock.widthPercent] — the block is
 * a single-dimension size (aligned via [Align], not independently cropped
 * per edge), so dragging either edge outward grows it and inward shrinks it.
 * [runningPercent] accumulates within one gesture from a local var captured
 * by the `pointerInput` coroutine, not from the (possibly stale, since
 * recomposition is async relative to raw pointer events) `widthPercent`
 * parameter — the standard pattern for drag-accumulated values in Compose.
 */
@Composable
private fun ResizeHandle(
    isLeftEdge: Boolean,
    containerWidthPx: Float,
    widthPercent: Float,
    onWidthPercentChange: (Float) -> Unit,
    onSnapTick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .padding(Spacing.xs)
            .size(width = 20.dp, height = 44.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f))
            .pointerInput(containerWidthPx, isLeftEdge) {
                var runningPercent = widthPercent
                var lastResolved = MediaResize.resolve(widthPercent)
                detectHorizontalDragGestures(
                    onDragStart = {
                        runningPercent = widthPercent
                        lastResolved = MediaResize.resolve(widthPercent)
                    },
                ) { change, dragAmount ->
                    change.consume()
                    if (containerWidthPx <= 0f) return@detectHorizontalDragGestures
                    val direction = if (isLeftEdge) -1f else 1f
                    runningPercent += (dragAmount / containerWidthPx) * 100f * direction
                    val resolved = MediaResize.resolve(runningPercent)
                    if (MediaResize.crossedSnapPoint(lastResolved, resolved)) onSnapTick()
                    lastResolved = resolved
                    onWidthPercentChange(resolved)
                }
            },
    )
}

@Composable
private fun MediaBlockToolbar(
    block: MediaBlock,
    onUpdate: (MediaBlock) -> Unit,
    onDelete: () -> Unit,
    onStackWithNext: (() -> Unit)?,
) {
    val actions = buildList {
        add(
            ToolbarAction(Icons.Filled.AlignHorizontalLeft, "Align left", selected = block.align == Align.Start) {
                onUpdate(block.copy(align = Align.Start))
            },
        )
        add(
            ToolbarAction(Icons.Filled.AlignHorizontalCenter, "Align center", selected = block.align == Align.Center) {
                onUpdate(block.copy(align = Align.Center))
            },
        )
        add(
            ToolbarAction(Icons.Filled.AlignHorizontalRight, "Align right", selected = block.align == Align.End) {
                onUpdate(block.copy(align = Align.End))
            },
        )
        if (block.kind == MediaKind.Video) {
            add(
                ToolbarAction(Icons.Filled.VolumeOff, "Toggle mute", selected = block.muted) {
                    onUpdate(block.copy(muted = !block.muted))
                },
            )
            add(
                ToolbarAction(Icons.Filled.Loop, "Toggle loop", selected = block.loop) {
                    onUpdate(block.copy(loop = !block.loop))
                },
            )
            add(
                ToolbarAction(Icons.Filled.PlayArrow, "Toggle autoplay", selected = block.autoplay) {
                    onUpdate(block.copy(autoplay = !block.autoplay))
                },
            )
        }
        add(ToolbarAction(Icons.Filled.Delete, "Delete", onClick = onDelete))
    }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SizePresetRow(widthPercent = block.widthPercent, onSelect = { onUpdate(block.copy(widthPercent = it)) })
            var widthText by remember(block.id, block.widthPercent) { mutableStateOf(block.widthPercent.toInt().toString()) }
            OutlinedTextField(
                value = widthText,
                onValueChange = { new ->
                    widthText = new
                    new.toFloatOrNull()?.let { onUpdate(block.copy(widthPercent = it.coerceIn(10f, 100f))) }
                },
                label = { Text("%") },
                singleLine = true,
                modifier = Modifier.padding(start = Spacing.sm).width(72.dp),
            )
            if (onStackWithNext != null) {
                IconButton(onClick = onStackWithNext) {
                    Icon(Icons.Filled.BurstMode, contentDescription = "Stack with next image")
                }
            }
        }
        IconToolbarRow(actions = actions, modifier = Modifier.padding(top = Spacing.xs))
    }
}

@Composable
private fun SizePresetRow(widthPercent: Float, onSelect: (Float) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        MediaResize.snapPoints.forEach { preset ->
            val isActive = preset == widthPercent
            AssistChip(
                onClick = { onSelect(preset) },
                label = { Text("${preset.toInt()}%") },
                colors = if (isActive) {
                    AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                } else {
                    AssistChipDefaults.assistChipColors()
                },
            )
        }
    }
}
