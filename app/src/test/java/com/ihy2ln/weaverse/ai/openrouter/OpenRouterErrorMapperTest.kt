package com.ihy2ln.weaverse.ai.openrouter

import com.ihy2ln.weaverse.ai.AIError
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OpenRouterErrorMapperTest {
    @Test
    fun maps401() {
        val err = OpenRouterErrorMapper.fromHttp(401, "")
        assertTrue(err is AIError.InvalidKey)
        assertEquals("Invalid API key", err.message)
    }

    @Test
    fun maps402() {
        assertEquals(AIError.OutOfCredits, OpenRouterErrorMapper.fromHttp(402, ""))
    }

    @Test
    fun maps429WithRetryAfter() {
        val err = OpenRouterErrorMapper.fromHttp(429, "", 30L)
        assertTrue(err is AIError.RateLimited)
        assertEquals(30L, (err as AIError.RateLimited).retryAfterSeconds)
    }

    @Test
    fun maps400WithBodyMessage() {
        val err = OpenRouterErrorMapper.fromHttp(400, """{"error":{"message":"Invalid model"}}""")
        assertTrue(err is AIError.BadRequest)
        assertEquals("Invalid model", err.message)
    }

    @Test
    fun maps502() {
        assertEquals(AIError.ProviderDown, OpenRouterErrorMapper.fromHttp(502, ""))
    }

    @Test
    fun mapsEmbeddedErrorIn200Range() {
        val err = OpenRouterErrorMapper.fromHttp(200, """{"error":{"message":"Provider failed"}}""")
        assertTrue(err is AIError.EmbeddedError)
        assertEquals("Provider failed", err.message)
    }
}
