package com.ihy2ln.weaverse.ai.openrouter

import com.ihy2ln.weaverse.ai.AIChunk
import com.ihy2ln.weaverse.ai.AIError
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OpenRouterSseParserTest {
    @Test
    fun ignoresKeepAliveComments() {
        val result = OpenRouterSseParser.parseLine(": OPENROUTER PROCESSING")
        assertEquals(OpenRouterSseParser.ParseResult.Ignore, result)
    }

    @Test
    fun parsesDeltaContent() {
        val line = """data: {"choices":[{"delta":{"content":"Hello"}}]}"""
        val result = OpenRouterSseParser.parseLine(line)
        assertTrue(result is OpenRouterSseParser.ParseResult.Chunks)
        assertEquals(
            listOf(AIChunk.Delta("Hello")),
            (result as OpenRouterSseParser.ParseResult.Chunks).chunks,
        )
    }

    @Test
    fun handlesDoneMarker() {
        val result = OpenRouterSseParser.parseLine("data: [DONE]")
        assertEquals(OpenRouterSseParser.ParseResult.Done, result)
    }

    @Test
    fun parsesUsageFromChunk() {
        val line = """data: {"usage":{"prompt_tokens":10,"completion_tokens":5,"cost":0.001}}"""
        val result = OpenRouterSseParser.parseLine(line)
        assertTrue(result is OpenRouterSseParser.ParseResult.Chunks)
        val usage = (result as OpenRouterSseParser.ParseResult.Chunks).chunks.first() as AIChunk.Usage
        assertEquals(10, usage.promptTokens)
        assertEquals(5, usage.completionTokens)
        assertEquals(0.001, usage.cost)
    }

    @Test
    fun throwsOnEmbeddedError() {
        val line = """data: {"error":{"message":"bad request"}}"""
        assertThrows(AIError.EmbeddedError::class.java) {
            OpenRouterSseParser.parseLine(line)
        }
    }
}
