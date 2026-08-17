package com.ihy2ln.weaverse.feature.shell

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.ihy2ln.weaverse.core.util.AppMode

private const val TRANSITION_MS = 250

/**
 * Keeps both modes' content composed simultaneously and crossfades between
 * them, instead of the more common AnimatedVisibility/Crossfade (which
 * dispose the outgoing content once its exit animation finishes) — spec §5
 * requires each mode to come back to exactly where it was (scroll position
 * included), which only works if its composition — and therefore its
 * NavHost back stack and any LazyList scroll state — is never torn down.
 *
 * Once a side has fully faded out it's also pushed off-screen via
 * [Modifier.absoluteOffset], so it can't intercept touches meant for the
 * visible side sitting on top of it in z-order. The hidden side is also
 * marked [invisibleToUser] — without it, staying permanently composed means
 * its text/controls would still show up in the accessibility tree (so
 * TalkBack could navigate into an off-screen pane) and in Compose UI test
 * queries (`onNodeWithText` would see both modes' content at once and throw
 * on any text that happens to appear in both, e.g. the mode-switch labels).
 */
@Composable
fun ModeCrossfadeHost(
    mode: AppMode,
    novelContent: @Composable () -> Unit,
    roleplayContent: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        HideablePane(visible = mode == AppMode.Novel, content = novelContent)
        HideablePane(visible = mode == AppMode.Roleplay, content = roleplayContent)
    }
}

@Composable
private fun HideablePane(visible: Boolean, content: @Composable () -> Unit) {
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(TRANSITION_MS),
        label = "modePaneAlpha",
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(if (visible) 1f else 0f)
            .graphicsLayer { this.alpha = alpha }
            .then(if (!visible) Modifier.semantics { invisibleToUser() } else Modifier)
            .then(if (!visible && alpha < 0.01f) Modifier.absoluteOffset(x = 10_000.dp) else Modifier),
    ) {
        content()
    }
}
