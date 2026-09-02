package com.ihy2ln.weaverse.ai.openrouter

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OpenRouterModelsTest {
    @Test
    fun reasoningCanBeKeptSmallAndExcludedFromVisibleReply() {
        val payload = Json { encodeDefaults = true }.encodeToString(
            OpenRouterChatRequest(
                model = "deepseek/test",
                messages = listOf(OpenRouterChatMessage("user", JsonPrimitive("Act"))),
                reasoning = OpenRouterReasoning(effort = "minimal", exclude = true),
            ),
        )

        assertTrue(payload.contains("\"reasoning\""))
        assertTrue(payload.contains("\"effort\":\"minimal\""))
        assertTrue(payload.contains("\"exclude\":true"))
    }
}
