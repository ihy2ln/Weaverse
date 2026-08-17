package com.ihy2ln.weaverse.ai.openrouter

import com.ihy2ln.weaverse.ai.AIError
import java.io.IOException

object OpenRouterErrorMapper {
    fun fromHttp(statusCode: Int, body: String, retryAfterSeconds: Long? = null): AIError =
        when (statusCode) {
            401 -> AIError.InvalidKey()
            402 -> AIError.OutOfCredits
            429 -> AIError.RateLimited(retryAfterSeconds)
            400 -> AIError.BadRequest(extractMessage(body) ?: body.ifBlank { "Bad request" })
            502, 503 -> AIError.ProviderDown
            in 200..299 -> AIError.EmbeddedError(extractMessage(body) ?: "Unknown API error")
            else -> AIError.HttpFailure(statusCode, body.ifBlank { "HTTP $statusCode" })
        }

    fun fromEmbeddedError(body: OpenRouterErrorBody?): AIError? =
        body?.message?.takeIf { it.isNotBlank() }?.let { AIError.EmbeddedError(it) }

    fun fromThrowable(throwable: Throwable): AIError = when (throwable) {
        is AIError -> throwable
        is IOException -> AIError.NoNetwork(throwable)
        else -> AIError.HttpFailure(-1, throwable.message ?: throwable.toString())
    }

    private fun extractMessage(body: String): String? {
        if (body.isBlank()) return null
        val messageMatch = Regex(""""message"\s*:\s*"([^"\\]*(?:\\.[^"\\]*)*)"""")
            .find(body)
        return messageMatch?.groupValues?.getOrNull(1)?.replace("\\\"", "\"")
    }
}
