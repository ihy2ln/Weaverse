package com.ihy2ln.weaverse.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import kotlin.math.roundToInt

enum class MediaEditAction {
    Cut,
    Copy,
    Paste,
    Delete,
    Shrink,
    Expand,
    Collapse,
    Uncollapse,
    Stack,
    Move,
    AdjustImage,
    AddTextOverlay,
}

data class MediaEditPopupConfig(
    val canPaste: Boolean = false,
    val isCollapsed: Boolean = false,
    val canShrink: Boolean = true,
    val canExpand: Boolean = true,
    val showStack: Boolean = true,
    val showMove: Boolean = false,
    val showAdjustImage: Boolean = false,
    val showTextOverlay: Boolean = false,
)

/** Place the media menu at the long-press point inside its parent. */
fun mediaMenuAnchor(press: Offset): IntOffset = IntOffset(
    press.x.roundToInt().coerceAtLeast(0),
    press.y.roundToInt().coerceAtLeast(0),
)

@Composable
fun MediaEditPopup(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onAction: (MediaEditAction) -> Unit,
    config: MediaEditPopupConfig = MediaEditPopupConfig(),
    anchorOffset: Offset = Offset.Zero,
    modifier: Modifier = Modifier,
) {
    val labelColor = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val anchor = mediaMenuAnchor(anchorOffset)

    Box(
        modifier = modifier
            .offset { anchor }
            .size(1.dp),
    ) {
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismiss,
        ) {
        Text(
            "Media",
            style = MaterialTheme.typography.labelSmall,
            color = muted,
            modifier = Modifier.padding(horizontal = InkSpacing.md, vertical = InkSpacing.xs),
        )
        Item("Cut", labelColor) { onAction(MediaEditAction.Cut); onDismiss() }
        Item("Copy", labelColor) { onAction(MediaEditAction.Copy); onDismiss() }
        Item("Paste", labelColor, enabled = config.canPaste) {
            onAction(MediaEditAction.Paste); onDismiss()
        }
        Item("Delete", labelColor) { onAction(MediaEditAction.Delete); onDismiss() }
        if (config.showMove) {
            Item("Move", labelColor) { onAction(MediaEditAction.Move); onDismiss() }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        Text(
            "Size",
            style = MaterialTheme.typography.labelSmall,
            color = muted,
            modifier = Modifier.padding(horizontal = InkSpacing.md, vertical = InkSpacing.xs),
        )
        Item("Shrink", labelColor, enabled = config.canShrink) {
            onAction(MediaEditAction.Shrink); onDismiss()
        }
        Item("Expand", labelColor, enabled = config.canExpand) {
            onAction(MediaEditAction.Expand); onDismiss()
        }
        if (config.isCollapsed) {
            Item("Uncollapse", labelColor) { onAction(MediaEditAction.Uncollapse); onDismiss() }
        } else {
            Item("Collapse", labelColor) { onAction(MediaEditAction.Collapse); onDismiss() }
        }

        if (config.showStack) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Item("Stack pictures", labelColor) { onAction(MediaEditAction.Stack); onDismiss() }
        }

        if (config.showAdjustImage || config.showTextOverlay) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            if (config.showAdjustImage) {
                Item("Adjust image", labelColor) {
                    onAction(MediaEditAction.AdjustImage); onDismiss()
                }
            }
            if (config.showTextOverlay) {
                Item("Add text", labelColor) {
                    onAction(MediaEditAction.AddTextOverlay); onDismiss()
                }
            }
        }
        }
    }
}

@Composable
private fun Item(
    label: String,
    color: Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Text(
                label,
                color = if (enabled) color else color.copy(alpha = 0.38f),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        onClick = onClick,
        enabled = enabled,
    )
}
