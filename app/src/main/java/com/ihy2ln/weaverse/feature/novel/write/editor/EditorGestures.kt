package com.ihy2ln.weaverse.feature.novel.write.editor

/**
 * Pictures use the platform combinedClickable long-press (~400ms).
 * Text Format opens via TextToolbar.showMenu (system long-press).
 * Scroll/drag past touch slop suppresses that showMenu so dragging to scroll
 * does not open Format; tap a highlight to reopen after dismiss.
 */
object EditorGestures {
    const val TEXT_LONG_PRESS_MS = 650L
    const val SELECTION_TAP_MS = 220L
}
