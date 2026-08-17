package com.ihy2ln.weaverse.ai.token

import kotlin.math.ceil

/**
 * Lightweight token estimate (spec §8: "token estimator + budget
 * allocator") — the ~4-characters-per-token rule of thumb, not a real
 * tokenizer. Good enough for live "estimated tokens" UI and budget
 * allocation (Phase 9's ContextBuilder); exact counts always come from the
 * provider's own response usage stats.
 */
object TokenEstimator {
    private const val CHARS_PER_TOKEN = 4.0

    fun estimate(text: String): Int {
        if (text.isBlank()) return 0
        return ceil(text.length / CHARS_PER_TOKEN).toInt().coerceAtLeast(1)
    }

    fun estimate(texts: List<String>): Int = texts.sumOf { estimate(it) }
}
