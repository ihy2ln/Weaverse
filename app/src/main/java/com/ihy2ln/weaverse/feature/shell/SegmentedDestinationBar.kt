package com.ihy2ln.weaverse.feature.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ihy2ln.weaverse.core.ui.CornerRadius
import com.ihy2ln.weaverse.core.ui.Spacing

/**
 * The dark-pill segmented control (spec §1.3, the reference screenshot's
 * green circle): `Plan · Write · Chat · Review` in novel mode, the roleplay
 * equivalent in roleplay mode — same composable, same styling, "so the
 * chrome feels like one app." Active segment = filled with
 * [MaterialTheme.colorScheme.onSurface] and an inverted (surface-colored)
 * label; inactive = transparent with primary text, no border.
 *
 * Selection compares by runtime *class*, not `==` — [NovelDestination.Write]
 * carries an optional `sceneId`, so the Write pill needs to stay highlighted
 * whether the current route is `Write(null)` or `Write("some-scene-id")`.
 */
@Composable
fun <T : Any> SegmentedDestinationBar(
    destinations: List<NavDestinationSpec<T>>,
    currentRoute: T?,
    onNavigate: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(CornerRadius.pill),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(modifier = Modifier.padding(2.dp)) {
            destinations.forEach { spec ->
                SegmentedDestinationPill(
                    label = spec.label,
                    icon = spec.icon,
                    selected = currentRoute != null && spec.route::class == currentRoute::class,
                    onClick = { onNavigate(spec.route) },
                )
            }
        }
    }
}

@Composable
private fun SegmentedDestinationPill(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val background = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent
    val contentColor = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(CornerRadius.pill))
            .background(background)
            .clickable(role = Role.Tab, onClickLabel = label, onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(16.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
                modifier = Modifier.padding(start = Spacing.xs),
            )
        }
    }
}
