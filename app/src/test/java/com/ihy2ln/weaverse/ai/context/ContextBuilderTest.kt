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
    fun overBudgetEntries_areLeftOutOfCodexText() {
        val entries = listOf(
            entry("1", "Alpha", alwaysInclude = true),
            entry("2", "Beta", alwaysInclude = true, body = "x".repeat(4000)),
        )
        val result = builder.build(
            entries,
            ContextBuildRequest(scanText = "", maxContextTokens = 600, reserveResponseTokens = 100),
        )
        assertTrue(result.droppedEntryIds == listOf("2"))
        assertTrue("[[Alpha]]" in result.codexBlock)
        assertFalse("[[Beta]]" in result.codexBlock)
        assertFalse(result.systemBlocks.any { "[[Beta]]" in it })
    }

    private fun entry(id: String, name: String, alwaysInclude: Boolean, body: String = "$name body") = CodexEntryEntity(
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
