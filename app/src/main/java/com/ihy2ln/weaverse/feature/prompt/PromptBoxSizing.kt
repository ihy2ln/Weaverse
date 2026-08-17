package com.ihy2ln.weaverse.feature.prompt

/** Compact PROMPT box grows with typed lines instead of a fixed tall panel. */
object PromptBoxSizing {
    const val MinLines = 1
    const val MaxLines = 8

    fun lineCount(text: String): Int {
        if (text.isEmpty()) return MinLines
        return text.count { it == '\n' } + 1
    }

    fun fieldMaxLines(text: String): Int = lineCount(text).coerceIn(MinLines, MaxLines)
}
