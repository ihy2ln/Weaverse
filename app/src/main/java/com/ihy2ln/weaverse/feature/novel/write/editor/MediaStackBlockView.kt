package com.ihy2ln.weaverse.feature.novel.write.editor

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.ihy2ln.weaverse.core.text.MediaStackBlock
import com.ihy2ln.weaverse.core.ui.components.InkDeleteButton
import com.ihy2ln.weaverse.core.ui.components.MediaEditAction
import com.ihy2ln.weaverse.core.ui.components.MediaEditPopup
import com.ihy2ln.weaverse.core.ui.components.MediaEditPopupConfig
import com.ihy2ln.weaverse.core.ui.components.ZoomableMedia
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkTokens

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaStackBlockView(
    block: MediaStackBlock,
    mediaPaths: Map<String, String>,
    selected: Boolean,
    canPaste: Boolean,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
    onCycle: () -> Unit,
    onStackAdjacent: () -> Unit,
    onMediaEditAction: (MediaEditAction) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember(block.id) { mutableStateOf(false) }
    var menuAnchor by remember(block.id) { mutableStateOf(Offset.Zero) }
    val haptic = LocalHapticFeedback.current
    val ids = block.mediaIds
    val index = block.currentIndex.coerceIn(0, (ids.size - 1).coerceAtLeast(0))
    val currentId = ids.getOrNull(index)
    val path = currentId?.let { mediaPaths[it] }
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        inkTokens().hairline
    }

    fun openMenu(at: Offset) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        onSelect()
        menuAnchor = at
        menuOpen = true
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = InkSpacing.sm)
            .border(if (selected) 2.dp else 1.dp, borderColor, RoundedCornerShape(InkSpacing.radiusSm))
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
                    else onCycle()
                },
                onLongClick = { openMenu(menuAnchor) },
            ),
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
                    "Stack collapsed (${ids.size}) · tap to uncollapse · hold for menu",
                    style = MaterialTheme.typography.labelMedium,
                    color = inkTokens().secondaryText,
                )
            }
        } else {
            ids.take(3).forEachIndexed { i, _ ->
                if (i > 0) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .padding(end = (i * 6).dp, bottom = (i * 6).dp)
                            .offset(x = (i * 4).dp, y = (i * 4).dp)
                            .background(
                                inkTokens().hover.copy(alpha = 0.5f),
                                RoundedCornerShape(InkSpacing.radiusSm),
                            )
                            .border(1.dp, inkTokens().hairline, RoundedCornerShape(InkSpacing.radiusSm)),
                    )
                }
            }
            if (path != null) {
                ZoomableMedia(
                    path = path,
                    contentDescription = "Stacked media ${index + 1}/${ids.size}",
                    maxHeight = 220.dp,
                    contentScale = ContentScale.Fit,
                    onLongPressAt = { openMenu(it) },
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(InkSpacing.xl)
                        .background(inkTokens().hover),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Stack · ${ids.size} pictures", color = inkTokens().secondaryText)
                }
            }
            Text(
                text = "Stack ${index + 1}/${ids.size.coerceAtLeast(1)} · tap to cycle",
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(InkSpacing.xs)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
                    .padding(horizontal = InkSpacing.sm, vertical = InkSpacing.xxs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            InkDeleteButton(
                itemName = "this stack",
                onConfirmedDelete = onRemove,
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
        MediaEditPopup(
            expanded = menuOpen,
            onDismiss = { menuOpen = false },
            config = MediaEditPopupConfig(
                canPaste = canPaste,
                isCollapsed = block.collapsed,
                canShrink = block.gridColSpan > 1 || block.gridRowSpan > 1,
                canExpand = block.gridColSpan < 6 || block.gridRowSpan < 6,
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
