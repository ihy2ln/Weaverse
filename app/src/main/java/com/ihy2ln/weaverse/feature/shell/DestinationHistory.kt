package com.ihy2ln.weaverse.feature.shell

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue

/**
 * A real per-mode navigation history (spec §1.2: "Both maintain a real
 * navigation history stack per mode, not just Android system back") — the
 * header's back/forward buttons walk this stack, independent of Android's
 * system back button and independent of [androidx.navigation.NavController]'s
 * own back stack (which this class drives, but doesn't read from — every
 * [NovelDestination]/[RoleplayDestination] is a parameterless `data object`,
 * so `==` is all the matching this needs).
 */
@Stable
class DestinationHistory<T : Any>(initial: T) {
    private val entries = mutableStateListOf(initial)
    private var cursor by mutableIntStateOf(0)

    val current: T get() = entries[cursor]
    val canGoBack: Boolean get() = cursor > 0
    val canGoForward: Boolean get() = cursor < entries.size - 1

    /** Navigates forward to [route], truncating any forward history past the current cursor —
     * the standard browser-style "new navigation after going back" behavior. A no-op if
     * [route] is already current. */
    fun navigate(route: T) {
        if (entries[cursor] == route) return
        while (entries.size > cursor + 1) entries.removeAt(entries.size - 1)
        entries.add(route)
        cursor = entries.size - 1
    }

    fun back(): T? {
        if (!canGoBack) return null
        cursor--
        return entries[cursor]
    }

    fun forward(): T? {
        if (!canGoForward) return null
        cursor++
        return entries[cursor]
    }
}
