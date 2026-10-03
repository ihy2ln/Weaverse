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

    /** One line for the run summary explaining why pages stayed black and white. */
    fun reasonNote(refused: Int, otherReason: String?): String = when {
        refused > 0 && otherReason == null ->
            " The image models' providers declined those pages under their own content rules, " +
                "including every fallback model tried; Weaverse itself does not filter them."
        refused > 0 ->
            " $refused were declined by the image providers' own content rules; others failed with: $otherReason"
        otherReason != null -> " Reason: $otherReason"
        else -> ""
    }
}
