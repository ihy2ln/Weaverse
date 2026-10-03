package com.ihy2ln.weaverse.core.media

import com.ihy2ln.weaverse.ai.AIError

/**
 * Image models apply their provider's own content rules, and some decline pages (fan-service
 * art especially) that another provider colors without complaint. Weaverse has no filter of
 * its own, so a declined page is simply tried again on other providers' models.
 */
object MangaColorFallback {
    /** How many other models a declined page is offered to. */
    const val MAX_FALLBACKS = 2

    private val refusalWords = Regex(
        "returned no image|declined|refus|safety|prohibited|content (policy|rules|filter)|moderation|flagged|" +
            "blocked|not allowed|violat|inappropriate|sexual|nsfw",
        RegexOption.IGNORE_CASE,
    )

    /** True when the model answered but would not make this picture (as opposed to a key, credit or network problem). */
    fun isRefusal(error: Throwable): Boolean = when (error) {
        is AllModelsFailed -> error.refused
        is AIError.NoApiKey, is AIError.InvalidKey, is AIError.OutOfCredits, is AIError.NoNetwork,
        is AIError.RateLimited, is AIError.ProviderDown -> false
        is AIError.HttpFailure -> error.statusCode == 403 || refusalWords.containsMatchIn(error.message)
        else -> refusalWords.containsMatchIn(error.message.orEmpty())
    }

    /**
     * Models to try after [primary] declined: one per other provider, Flux Kontext first
     * (Black Forest Labs is the least strict about drawn fan-service), then the rest in the
     * order given.
     */
    fun fallbacks(primary: String, candidates: List<String>): List<String> {
        val tried = provider(primary)
        return candidates
            .filter { it != primary && provider(it) != tried }
            .sortedBy { if (it.contains("flux", ignoreCase = true)) 0 else 1 }
            .distinctBy(::provider)
            .take(MAX_FALLBACKS)
    }

    /** "openrouter/google/gemini-2.5-flash-image" → "google". */
    fun provider(modelRef: String): String =
        modelRef.removePrefix("openrouter/").substringBefore('/').lowercase()

    /** Thrown when the first model and every fallback failed; [message] lists each model's error. */
    class AllModelsFailed(val refused: Boolean, override val message: String) : Exception(message)

    /** "gemini-2.5-flash-image: The Image API returned no image…" */
    fun describe(modelRef: String, error: Throwable): String =
        modelRef.removePrefix("openrouter/").substringAfter('/') + ": " +
            (error.message?.take(160)?.ifBlank { null } ?: error.javaClass.simpleName)

    /** The summary's explanation, with the first failed page's errors so a screenshot shows the cause. */
    fun reasonNote(refused: Int, firstFailure: String?): String = buildString {
        if (refused > 0) append(" $refused were declined by the image providers' own content rules (Weaverse adds no filter).")
        if (firstFailure != null) append(" First failed page — $firstFailure")
    }
}
