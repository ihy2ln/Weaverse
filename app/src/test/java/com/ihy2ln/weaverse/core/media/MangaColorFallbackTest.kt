package com.ihy2ln.weaverse.core.media

import com.ihy2ln.weaverse.ai.AIError
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MangaColorFallbackTest {
    @Test
    fun declinedPagesAreRefusalsButAccountProblemsAreNot() {
        assertTrue(MangaColorFallback.isRefusal(AIError.EmbeddedError("The Image API returned no image. The provider may have declined the request.")))
        assertTrue(MangaColorFallback.isRefusal(AIError.BadRequest("Request blocked: IMAGE_SAFETY")))
        assertTrue(MangaColorFallback.isRefusal(AIError.HttpFailure(403, "flagged by moderation")))
        assertFalse(MangaColorFallback.isRefusal(AIError.OutOfCredits))
        assertFalse(MangaColorFallback.isRefusal(AIError.NoApiKey()))
        assertFalse(MangaColorFallback.isRefusal(AIError.RateLimited(5)))
        assertFalse(MangaColorFallback.isRefusal(AIError.NoNetwork()))
    }

    @Test
    fun fallbacksSkipTheDecliningProviderAndPreferFlux() {
        val candidates = listOf(
            "openrouter/google/gemini-2.5-flash-image",
            "openrouter/google/gemini-3-pro-image",
            "openrouter/openai/gpt-image-1",
            "openrouter/black-forest-labs/flux.1-kontext-pro",
            "openrouter/black-forest-labs/flux.1-kontext-max",
        )
        assertEquals(
            listOf("openrouter/black-forest-labs/flux.1-kontext-pro", "openrouter/openai/gpt-image-1"),
            MangaColorFallback.fallbacks("openrouter/google/gemini-2.5-flash-image", candidates),
        )
        assertEquals(emptyList<String>(), MangaColorFallback.fallbacks("openrouter/google/a", listOf("openrouter/google/b")))
    }
}
