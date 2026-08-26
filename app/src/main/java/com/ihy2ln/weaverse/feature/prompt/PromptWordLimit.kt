package com.ihy2ln.weaverse.feature.prompt

/** Shared exact word cap for prompt UI and final generated text insertion. */
object PromptWordLimit {
    val presets: List<Int> = listOf(100, 250, 500, 750, 1000, 1500, 2000, 3000, 4000)

    fun trim(text: String, maxWords: Int): String {
        if (maxWords <= 0 || text.isBlank()) return ""
        val matches = Regex("\\S+").findAll(text).take(maxWords).toList()
        if (matches.size < maxWords) return text.trim()
        val last = matches.last()
        val hasMore = Regex("\\S+").find(text, last.range.last + 1) != null
        return if (hasMore) text.substring(0, last.range.last + 1).trimEnd() else text.trim()
    }

    fun count(text: String): Int = Regex("\\S+").findAll(text).count()
}
