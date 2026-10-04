package com.ihy2ln.weaverse.core.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/** A mode page's own heading: its title, a line under it and its buttons (create, see all, play). */
class ModeHeaderInfo(
    val title: String,
    val subtitle: String = "",
    val actions: (@Composable RowScope.() -> Unit)? = null,
    /** A second, compact row under the heading (e.g. the featured book and its Read / Details). */
    val detail: (@Composable () -> Unit)? = null,
)

/**
 * Where a mode page's heading goes when the mode banner can show it: the banner and the page's
 * heading become one block instead of two stacked titles (RPG · Campaign over "Campaigns").
 */
class ModeHeaderHost {
    var info by mutableStateOf<ModeHeaderInfo?>(null)
        internal set

    private var owner: Any? = null

    internal fun claim(by: Any, value: ModeHeaderInfo) {
        owner = by
        info = value
    }

    internal fun release(by: Any) {
        if (owner === by) {
            owner = null
            info = null
        }
    }
}

val LocalModeHeaderHost = staticCompositionLocalOf<ModeHeaderHost?> { null }

/** Whether a mode banner is carrying this page's heading, so the page can drop its own hero. */
@Composable
fun isModeHeaderHosted(): Boolean = LocalModeHeaderHost.current != null

/**
 * A mode page's heading: handed to the mode banner when one is hosting ([LocalModeHeaderHost]),
 * else drawn in place by [inPage].
 */
@Composable
fun ModePageHeader(
    title: String,
    subtitle: String = "",
    actions: (@Composable RowScope.() -> Unit)? = null,
    detail: (@Composable () -> Unit)? = null,
    inPage: @Composable () -> Unit = {},
) {
    val host = LocalModeHeaderHost.current
    if (host == null) {
        inPage()
        return
    }
    val token = remember { Any() }
    // Every recomposition hands over fresh lambdas, so the buttons act on current state.
    SideEffect { host.claim(token, ModeHeaderInfo(title, subtitle, actions, detail)) }
    DisposableEffect(host) { onDispose { host.release(token) } }
}
