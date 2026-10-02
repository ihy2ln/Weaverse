package com.ihy2ln.weaverse

import com.ihy2ln.weaverse.ai.AIError
import com.ihy2ln.weaverse.ai.AiRetry
import com.ihy2ln.weaverse.ai.openrouter.OpenRouterErrorMapper
import com.ihy2ln.weaverse.feature.roleplay.chat.RoleplayGeneration
import com.ihy2ln.weaverse.feature.search.SearchText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AuditTier2Test {
    @Test
    fun `search excerpts show the words around the match`() {
        val text = "a ".repeat(200) + "the dragon wakes " + "b ".repeat(200)
        val excerpt = SearchText.excerpt(text, "Dragon")
        assertTrue(excerpt.contains("dragon"), excerpt)
        assertTrue(excerpt.startsWith("…"))
        assertEquals("short text", SearchText.excerpt("short   text", "zzz"))
    }

    @Test
    fun `roleplay history keeps the newest turns that fit`() {
        val history = (1..40).map { (if (it % 2 == 0) "assistant" else "user") to "message $it " + "x".repeat(400) }
        val kept = RoleplayGeneration.fitHistory(history, budgetTokens = 1_000)
        assertTrue(kept.size in RoleplayGeneration.MIN_KEPT_MESSAGES until history.size)
        assertEquals(history.last(), kept.last())
        assertEquals(history.takeLast(kept.size), kept)
        // A tiny budget still sends the latest exchanges.
        assertEquals(RoleplayGeneration.MIN_KEPT_MESSAGES, RoleplayGeneration.fitHistory(history, 0).size)
        assertEquals(history, RoleplayGeneration.fitHistory(history, 1_000_000))
    }

    @Test
    fun `transient provider failures are retried, others are not`() {
        assertTrue(AiRetry.isRetryable(AIError.RateLimited(5)))
        assertTrue(AiRetry.isRetryable(OpenRouterErrorMapper.fromHttp(529, "overloaded")))
        assertTrue(AiRetry.isRetryable(OpenRouterErrorMapper.fromHttp(500, "")))
        assertTrue(AiRetry.isRetryable(AIError.NoNetwork()))
        assertFalse(AiRetry.isRetryable(AIError.InvalidKey()))
        assertFalse(AiRetry.isRetryable(OpenRouterErrorMapper.fromHttp(400, "bad")))
    }
}
