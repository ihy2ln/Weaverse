package com.ihy2ln.weaverse.ai.context

import com.ihy2ln.weaverse.data.db.entities.CodexEntryEntity
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ContextBuilderTest {
    private val builder = ContextBuilder()

    @Test
    fun detectsEntry_byNameInScanText() {
        val entries = listOf(
            entry("1", "John Z", alwaysInclude = false),
            entry("2", "Harbor", alwaysInclude = false),
        )
        val result = builder.build(
            entries,
            ContextBuildRequest(scanText = "John Z stepped off the ferry", userMessage = "continue"),
        )
        assertTrue(result.usedEntries.any { it.entryId == "1" })
        assertFalse(result.usedEntries.any { it.entryId == "2" })
    }

    @Test
    fun manualExclude_removesEntryFromPrompt() {
        val entries = listOf(entry("1", "John Z", alwaysInclude = true))
        val result = builder.build(
            entries,
            ContextBuildRequest(
                scanText = "John Z",
                manualExcludeIds = setOf("1"),
            ),
        )
        assertFalse(result.usedEntries.any { it.entryId == "1" })
    }

    @Test
    fun tokenBreakdownSplitsCodexAndScene() {
        val entries = listOf(entry("1", "John Z", alwaysInclude = true))
        val scene = "John Z walked the harbor for many paragraphs of scene text."
        val result = builder.build(
            entries,
            ContextBuildRequest(
                scanText = scene,
                sceneText = scene,
                userMessage = "continue the beat",
            ),
        )
        assertTrue(result.tokenBreakdown.any { it.section == "Codex" && it.tokens > 0 })
        assertTrue(result.tokenBreakdown.any { it.section == "Scene" && it.tokens > 0 })
        assertTrue(result.tokenBreakdown.any { it.section == "User" && it.tokens > 0 })
    }

    @Test
    fun tokenBudget_dropsOverflowFromPromptText() {
        val huge = "x".repeat(20_000)
        val entries = listOf(
            entry("1", "Keep", alwaysInclude = true, body = "small"),
            entry("2", "Drop", alwaysInclude = true, body = huge),
        )
        val result = builder.build(
            entries,
            ContextBuildRequest(
                scanText = "Keep Drop",
                maxContextTokens = 500,
                reserveResponseTokens = 100,
            ),
        )
        assertTrue(result.usedEntries.any { it.entryId == "1" })
        assertTrue(result.droppedEntryIds.contains("2"))
        assertFalse(result.codexBlock.contains("[[Drop]]"))
        assertTrue(result.codexBlock.contains("[[Keep]]"))
    }

    private fun entry(
        id: String,
        name: String,
        alwaysInclude: Boolean,
        body: String = "$name body",
    ) = CodexEntryEntity(
        id = id,
        categoryId = "cat-0",
        scopeType = "book",
        scopeId = "book-1",
        name = name,
        docJson = "{}",
        plainText = body,
        alwaysInclude = alwaysInclude,
        createdAt = 0L,
        updatedAt = 0L,
    )
}
