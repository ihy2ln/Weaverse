package com.ihy2ln.weaverse.feature.shell

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ihy2ln.weaverse.core.ui.Spacing
import com.ihy2ln.weaverse.core.util.AppMode

private const val TRANSITION_MS = 250

/** The "Novel ⇄ Roleplay" segmented pill pinned to the top bar's right edge (spec §5). */
@Composable
fun ModeSwitch(
    mode: AppMode,
    onModeChange: (AppMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(modifier = Modifier.padding(2.dp)) {
            ModeSwitchSegment(
                label = "Novel",
                selected = mode == AppMode.Novel,
                onClick = { onModeChange(AppMode.Novel) },
            )
            ModeSwitchSegment(
                label = "Roleplay",
                selected = mode == AppMode.Roleplay,
                onClick = { onModeChange(AppMode.Roleplay) },
            )
        }
    }
}

@Composable
private fun ModeSwitchSegment(label: String, selected: Boolean, onClick: () -> Unit) {
    val background by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(TRANSITION_MS),
        label = "modeSwitchBackground",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(TRANSITION_MS),
        label = "modeSwitchContent",
    )

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = contentColor)
    }
}
