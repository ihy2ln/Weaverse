package com.ihy2ln.weaverse.core.ui.components

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll

/**
 * Whether the top chrome (the mode tabs and the browser's address bar) is tucked away.
 * Like a mobile browser: scrolling down hides it, scrolling up or reaching the top shows it.
 */
@Stable
class ChromeCollapseState(private val thresholdPx: Float = 48f) {
    var collapsed by mutableStateOf(false)
        private set
    private var travel = 0f

    /** [dy] > 0 when the content moves toward its end (the finger goes up). */
    fun onScroll(dy: Float, atTop: Boolean = false) {
        if (atTop) { expand(); return }
        if (dy == 0f) return
        // A change of direction starts counting again.
        if ((dy > 0f) != (travel > 0f)) travel = 0f
        travel += dy
        if (travel > thresholdPx && !collapsed) { collapsed = true; travel = 0f }
        if (travel < -thresholdPx && collapsed) { collapsed = false; travel = 0f }
    }

    fun expand() {
        collapsed = false
        travel = 0f
    }
}

val LocalChromeCollapse = staticCompositionLocalOf { ChromeCollapseState() }

/** Watches every scroll inside without taking any of it, and folds the chrome to match. */
fun Modifier.collapseChromeOnScroll(state: ChromeCollapseState): Modifier = nestedScroll(
    object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            state.onScroll(-available.y)
            return Offset.Zero
        }
    },
)
