package com.ihy2ln.weaverse.feature.novel.write.editor

/**
 * Pictures use the platform combinedClickable long-press (~400ms).
 * Text is a little slower so the Format popup and media menu don't feel identical.
 */
object EditorGestures {
    const val TEXT_LONG_PRESS_MS = 650L
    const val SELECTION_TAP_MS = 220L
}
