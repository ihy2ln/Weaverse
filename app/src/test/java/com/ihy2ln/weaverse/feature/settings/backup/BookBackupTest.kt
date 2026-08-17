package com.ihy2ln.weaverse.feature.settings.backup

import com.ihy2ln.weaverse.core.export.toMarkdown
import com.ihy2ln.weaverse.core.export.parseMarkdownOutline
import com.ihy2ln.weaverse.data.db.entity.SceneStatus
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BookBackupTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val backup = BookBackup(
        title = "Adams Haven",
        genre = "Sci-fi",
        pov = "Third limited",
        tense = "Past",
        styleGuide = "Terse, sensory.",
        acts = listOf(
            ActBackup(
                title = "Act One",
                sortOrder = 0,
                chapters = listOf(
                    ChapterBackup(
                        title = "Arrival",
                        summary = "The crew reaches the station.",
                        sortOrder = 0,
                        scenes = listOf(
                            SceneBackup(
                                title = "Docking",
                                plainText = "The airlock hissed open.",
                                summary = "",
                                wordCount = 4,
                                status = SceneStatus.Final,
                                pov = "Mara",
                                sortOrder = 0,
                            ),
                        ),
                    ),
                ),
            ),
        ),
        codexCategories = listOf(
            CodexCategoryBackup(
                name = "Characters",
                colorHex = "#4A90D9",
                entries = listOf(
                    CodexEntryBackup(name = "Mara Voss", aliases = listOf("Mara"), plainText = "Ship's engineer.", colorHex = null, alwaysInclude = true),
                ),
            ),
        ),
    )

    @Test
    fun `decoding an encoded backup recovers every field`() {
        val encoded = json.encodeToString(backup)
        val decoded = json.decodeFromString<BookBackup>(encoded)

        assertEquals(backup, decoded)
    }

    @Test
    fun `an empty book still round-trips`() {
        val empty = BookBackup(title = "Untitled", genre = "", pov = "", tense = "", styleGuide = "", acts = emptyList(), codexCategories = emptyList())
        val decoded = json.decodeFromString<BookBackup>(json.encodeToString(empty))

        assertEquals(empty, decoded)
    }

    @Test
    fun `manuscript survives an outline round-trip through markdown, minus codex and metadata`() {
        val markdown = backup.toOutline().toMarkdown()
        val reimported = markdown.parseMarkdownOutline(fallbackTitle = "fallback").toBookBackup()

        assertEquals(backup.title, reimported.title)
        assertEquals(backup.acts.map { it.title }, reimported.acts.map { it.title })
        assertEquals(backup.acts[0].chapters.map { it.title }, reimported.acts[0].chapters.map { it.title })
        assertEquals(backup.acts[0].chapters[0].scenes.map { it.title }, reimported.acts[0].chapters[0].scenes.map { it.title })
        assertEquals(backup.acts[0].chapters[0].scenes[0].plainText, reimported.acts[0].chapters[0].scenes[0].plainText)
        // Outline import is manuscript-only by design -- codex and scene metadata don't survive.
        assertEquals(emptyList<CodexCategoryBackup>(), reimported.codexCategories)
    }
}
