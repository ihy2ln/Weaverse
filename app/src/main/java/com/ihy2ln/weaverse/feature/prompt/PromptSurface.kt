package com.ihy2ln.weaverse.feature.prompt

import com.ihy2ln.weaverse.feature.shell.AppMode

/**
 * Shared prompt bar is available on every generative workspace
 * (Novel Plan / Write / Chat / Review, Roleplay, Notes).
 */
object PromptSurface {
    fun usesGlobalOverlay(mode: AppMode, novelDest: String?): Boolean {
        return true
    }
}
