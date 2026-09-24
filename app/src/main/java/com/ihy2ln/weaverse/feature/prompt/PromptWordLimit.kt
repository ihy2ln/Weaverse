package com.ihy2ln.weaverse.feature.prompt

/** Shared word target for prompt UI and sentence-safe generated text insertion. */
object PromptWordLimit {
    const val Minimum = 50
    const val Maximum = 4000
    val presets: List<Int> = listOf(100, 250, 500, 750, 1000, 1500, 2000, 3000, 4000)

    private val words = Regex("\\S+")
    private val sentenceEnd = Regex("""[.!?](?:["'”’)\]}*_]+)?(?=\s|$)""")

    fun instruction(minWords: Int, maxWords: Int): String {
        val maximum = maxWords.coerceIn(Minimum, Maximum)
        val minimum = minWords.coerceIn(Minimum, maximum)
        val allowance = overflowAllowance(maximum)
        return "Target $minimum–$maximum words. Always complete the final sentence before stopping. " +
            "Aim to stay at or below $maximum words, but you may use up to $allowance extra words " +
            "only when needed to finish that sentence. Never end with a cut-off sentence or fragment."
    }

    fun trim(text: String, maxWords: Int): String {
        if (maxWords <= 0 || text.isBlank()) return ""
        val normalized = text.trim()
        val matches = words.findAll(normalized).toList()
        if (matches.size <= maxWords) return normalized

        // Prefer the first complete sentence just beyond the target. The allowance keeps the
        // selected word count meaningful while ensuring normal prose is never sliced mid-sentence.
        val allowance = overflowAllowance(maxWords)
        val upperWord = matches[minOf(matches.lastIndex, maxWords + allowance - 1)]
        val targetWord = matches[maxWords - 1]
        val forwardEnd = sentenceEnd.find(normalized, targetWord.range.first)
            ?.takeIf { it.range.last <= upperWord.range.last }
        if (forwardEnd != null) {
            return normalized.substring(0, forwardEnd.range.last + 1).trimEnd()
        }

        // If the next sentence runs too far, finish at the most recent complete sentence. When
        // no boundary exists, preserve the response instead of manufacturing a cut-off fragment.
        val safePrefix = normalized.substring(0, upperWord.range.last + 1)
        val previousEnd = sentenceEnd.findAll(safePrefix).lastOrNull()
        return previousEnd
            ?.let { normalized.substring(0, it.range.last + 1).trimEnd() }
            ?: normalized
    }

    fun count(text: String): Int = words.findAll(text).count()

    private fun overflowAllowance(maxWords: Int): Int = (maxWords / 10).coerceIn(12, 80)
}
