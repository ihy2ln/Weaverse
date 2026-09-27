package com.ihy2ln.weaverse.feature.chatting.social

import com.ihy2ln.weaverse.feature.chatting.media.MediaTags

/** Checks the visible writing, rather than media queries or content-warning syntax. */
internal object SocialRepeatGuard {
    private val noise = setOf("the", "and", "for", "with", "this", "that", "from", "just", "into", "about", "have", "your", "some", "their", "there", "today", "really", "again", "would", "could")

    fun visible(text: String): String = MediaTags.extract(text).first
        .replace(Regex("\\[[^]]+]"), " ")
        .replace(Regex("https?://\\S+"), " ")
        .lowercase()
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()

    fun repeated(draft: String, history: Iterable<String>, reply: Boolean = false): String? {
        val clean = visible(draft)
        if (clean.isBlank()) return null
        val words = clean.split(' ').filter { it.length >= 3 && it !in noise }
        val shingles = words.windowed(3).map { it.joinToString(" ") }.toSet()
        return history.firstOrNull { old ->
            val previous = visible(old)
            if (previous.isBlank()) false else {
                val oldWords = previous.split(' ').filter { it.length >= 3 && it !in noise }
                val oldShingles = oldWords.windowed(3).map { it.joinToString(" ") }.toSet()
                val shared = words.toSet().intersect(oldWords.toSet())
                val overlap = shared.size.toDouble() / minOf(words.toSet().size, oldWords.toSet().size).coerceAtLeast(1)
                val phraseOverlap = shingles.intersect(oldShingles).size.toDouble() / minOf(shingles.size, oldShingles.size).coerceAtLeast(1)
                clean == previous ||
                    (minOf(words.size, oldWords.size) >= 5 && phraseOverlap >= (if (reply) 0.52 else 0.44)) ||
                    (shared.size >= (if (reply) 5 else 7) && overlap >= (if (reply) 0.83 else 0.77))
            }
        }
    }
}
