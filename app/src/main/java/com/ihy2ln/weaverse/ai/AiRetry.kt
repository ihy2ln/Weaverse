package com.ihy2ln.weaverse.ai

object AiRetry {
    const val MAX_ATTEMPTS = 3

    /** Failures that usually pass on their own: rate limits, an overloaded or down provider, a dropped connection. */
    fun isRetryable(error: Throwable): Boolean =
        error is AIError.RateLimited || error is AIError.ProviderDown || error is AIError.NoNetwork

    private fun retryAfter(error: Throwable): Long? = (error as? AIError.RateLimited)?.retryAfterSeconds

    fun waitSecondsFor(error: Throwable, attempt: Int): Long = waitSeconds(retryAfter(error), attempt)

    fun waitSeconds(retryAfterSeconds: Long?, attempt: Int): Long {
        val fallback = (2L shl attempt.coerceAtLeast(0)).coerceAtMost(60L)
        return (retryAfterSeconds ?: fallback).coerceIn(1L, 90L)
    }
}
