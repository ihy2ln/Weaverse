package com.ihy2ln.weaverse.feature.prompt

import com.ihy2ln.weaverse.feature.shell.AppMode
import com.ihy2ln.weaverse.feature.shell.NovelDestination

/**
 * The shared prompt bar belongs to an active writing/play surface. AppShell adds
 * the finer-grained checks for an opened chat or storyboard; this guard keeps it
 * off passive Novel destinations and the app-wide Notes workspace.
 */
object PromptSurface {
    fun usesGlobalOverlay(mode: AppMode, novelDest: String?): Boolean {
        return when (mode) {
            AppMode.Novel -> novelDest == NovelDestination.Write.name
            AppMode.Notes -> false
            else -> true
        }
    }
}
