package com.ihy2ln.weaverse.ai.providers

import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode

/**
 * Every provider's failure path used to discard the response body and show
 * just the status code — useless for telling "wrong key" apart from "out of
 * credits" apart from "provider is down." This reads the real body (every
 * provider here returns a JSON `{"error": {...}}` or plain-text body on
 * failure) and prefixes it with what the status code actually means, so the
 * UI can show the same thing a `curl` of the same request would.
 */
internal suspend fun HttpResponse.describeFailure(providerLabel: String): String {
    val bodyText = runCatching { bodyAsText() }.getOrNull().orEmpty().take(500)
    val prefix = when (status) {
        HttpStatusCode.Unauthorized -> "Invalid API key"
        HttpStatusCode.PaymentRequired -> "Out of credits"
        HttpStatusCode.TooManyRequests -> {
            val retryAfter = headers["Retry-After"]
            if (retryAfter != null) "Rate limited — retry after ${retryAfter}s" else "Rate limited"
        }
        HttpStatusCode.BadRequest -> "Bad request"
        HttpStatusCode.BadGateway, HttpStatusCode.ServiceUnavailable -> "$providerLabel is down"
        else -> "$providerLabel request failed"
    }
    return if (bodyText.isBlank()) "$prefix (HTTP ${status.value})" else "$prefix (HTTP ${status.value}): $bodyText"
}
