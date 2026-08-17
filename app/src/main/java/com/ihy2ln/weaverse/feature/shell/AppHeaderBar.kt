package com.ihy2ln.weaverse.feature.shell

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ihy2ln.weaverse.core.ui.Spacing
import com.ihy2ln.weaverse.data.settings.AppSettingsRepository

val AppHeaderHeight: Dp = 64.dp

/**
 * The rail-width header region (spec §1.2, the reference screenshot's red
 * circle): back/forward (a real per-mode [DestinationHistory], not just
 * Android system back), a settings gear, a small-caps bold title with an
 * optional series line beneath it, and — on Medium/Expanded only — a
 * collapse toggle and a drag handle that resizes the rail between
 * [AppSettingsRepository.RailWidthMin] and [AppSettingsRepository.RailWidthMax]dp.
 * On Compact the rail is a modal drawer instead (spec: "the collapse icon
 * becomes a close, and the grip is hidden"), so [onRailToggle] there just
 * opens/closes that sheet and [showCollapseAndResize] should be false.
 */
@Composable
fun AppHeaderBar(
    title: String,
    seriesName: String?,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onSettingsClick: () -> Unit,
    onTitleClick: () -> Unit = {},
    onSeriesClick: () -> Unit = {},
    showCollapseAndResize: Boolean,
    railCollapsed: Boolean,
    onToggleCollapse: () -> Unit,
    onRailResize: (deltaDp: Dp) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth().height(AppHeaderHeight),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .border(width = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                .padding(horizontal = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderCircleIconButton(
                icon = Icons.Filled.ChevronLeft,
                contentDescription = "Back",
                enabled = canGoBack,
                onClick = onBack,
            )
            HeaderCircleIconButton(
                icon = Icons.Filled.ChevronRight,
                contentDescription = "Forward",
                enabled = canGoForward,
                onClick = onForward,
            )
            IconButton(onClick = onSettingsClick) {
                Icon(imageVector = Icons.Filled.Settings, contentDescription = "Settings")
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = Spacing.sm)
                    .clickable(onClickLabel = "Rename", role = Role.Button, onClick = onTitleClick),
            ) {
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().basicMarquee(),
                )
                if (seriesName != null) {
                    Text(
                        text = seriesName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable(onClickLabel = "Change series", role = Role.Button, onClick = onSeriesClick),
                    )
                }
            }

            if (showCollapseAndResize) {
                IconButton(onClick = onToggleCollapse) {
                    Icon(
                        imageVector = if (railCollapsed) Icons.Filled.ChevronRight else Icons.Filled.ChevronLeft,
                        contentDescription = if (railCollapsed) "Expand rail" else "Collapse rail",
                    )
                }
                if (!railCollapsed) {
                    RailDragHandle(onDrag = onRailResize)
                }
            } else {
                IconButton(onClick = onToggleCollapse) {
                    Icon(imageVector = Icons.Filled.Close, contentDescription = "Close rail")
                }
            }
        }
    }
}

@Composable
private fun RowScope.HeaderCircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val contentColor = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
    Box(
        modifier = Modifier
            .padding(end = Spacing.xxs)
            .size(28.dp)
            .clip(CircleShape)
            .border(width = 1.dp, color = MaterialTheme.colorScheme.outlineVariant, shape = CircleShape)
            .clickable(enabled = enabled, onClickLabel = contentDescription, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription, tint = contentColor, modifier = Modifier.size(18.dp))
    }
}

/** The `||` grip that resizes the rail (spec §1.2), reporting horizontal drag deltas in dp so
 * the caller can clamp/persist via [AppSettingsRepository.setRailWidthDp]. */
@Composable
private fun RailDragHandle(onDrag: (Dp) -> Unit) {
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(20.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(with(density) { dragAmount.x.toDp() })
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.DragHandle,
            contentDescription = "Resize rail",
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(16.dp),
        )
    }
}
