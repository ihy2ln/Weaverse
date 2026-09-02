package com.ihy2ln.weaverse.ai

object AiRetry {
    const val MAX_ATTEMPTS = 3

    fun waitSeconds(retryAfterSeconds: Long?, attempt: Int): Long {
        val fallback = (2L shl attempt.coerceAtLeast(0)).coerceAtMost(60L)
        return (retryAfterSeconds ?: fallback).coerceIn(1L, 90L)
    }
}
