package com.ihy2ln.weaverse.feature.settings.backup

import com.ihy2ln.weaverse.core.export.parseMarkdownOutline
import com.ihy2ln.weaverse.core.export.toMarkdown
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SnippetBackupTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val backup = SnippetBackup(
        bookTitle = "Adams Haven",
        snippets = listOf(
            SnippetBackupItem(title = "Airlock hiss", body = "The airlock hissed and groaned open.", category = "SFX", pinned = true),
            SnippetBackupItem(title = "Mara's tell", body = "She rubs her thumb across her knuckles when she's scared.", category = "", pinned = false),
        ),
    )

    @Test
    fun `decoding an encoded snippet backup recovers every field`() {
        val decoded = json.decodeFromString<SnippetBackup>(json.encodeToString(backup))
        assertEquals(backup, decoded)
    }

    @Test
    fun `markdown round-trip preserves title and body for every snippet`() {
        val markdown = backup.toOutline().toMarkdown()
        val reimported = markdown.parseMarkdownOutline(fallbackTitle = "fallback").toSnippetBackup()

        assertEquals(backup.snippets.map { it.title }, reimported.snippets.map { it.title })
        assertEquals(backup.snippets.map { it.body }, reimported.snippets.map { it.body })
    }
}
