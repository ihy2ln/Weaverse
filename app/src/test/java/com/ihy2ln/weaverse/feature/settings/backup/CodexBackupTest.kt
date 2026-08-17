package com.ihy2ln.weaverse.feature.settings.backup

import com.ihy2ln.weaverse.core.export.parseMarkdownOutline
import com.ihy2ln.weaverse.core.export.toMarkdown
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CodexBackupTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val backup = CodexBackup(
        title = "Adams Haven Codex",
        categories = listOf(
            CodexCategoryBackup(
                name = "Characters",
                colorHex = "#4A90D9",
                entries = listOf(
                    CodexEntryBackup(name = "Mara Voss", aliases = listOf("Mara", "the engineer"), plainText = "Ship's engineer.", colorHex = null, alwaysInclude = true),
                    CodexEntryBackup(name = "Halcyon", aliases = emptyList(), plainText = "The drifting freighter.", colorHex = "#8B6FD1", alwaysInclude = false),
                ),
            ),
        ),
    )

    @Test
    fun `decoding an encoded codex backup recovers every field`() {
        val decoded = json.decodeFromString<CodexBackup>(json.encodeToString(backup))
        assertEquals(backup, decoded)
    }

    @Test
    fun `markdown round-trip recovers category, entry names, aliases, and body`() {
        val markdown = backup.toOutline().toMarkdown()
        val reimported = markdown.parseMarkdownOutline(fallbackTitle = "fallback").toCodexBackup()

        assertEquals(backup.title, reimported.title)
        assertEquals(backup.categories.map { it.name }, reimported.categories.map { it.name })

        val originalEntries = backup.categories[0].entries
        val reimportedEntries = reimported.categories[0].entries
        assertEquals(originalEntries.map { it.name }, reimportedEntries.map { it.name })
        assertEquals(originalEntries.map { it.aliases }, reimportedEntries.map { it.aliases })
        assertEquals(originalEntries.map { it.plainText }, reimportedEntries.map { it.plainText })
    }
}
