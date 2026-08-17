package com.ihy2ln.weaverse.data.export.novelcrafter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class NovelcrafterZipParserTest {
    @Test
    fun detectsNovelcrafterLayout() {
        assertTrue(
            NovelcrafterZipParser.looksLikeNovelcrafterZip(
                listOf(
                    "novel.md",
                    "characters/hero-abc/metadata.json",
                    "characters/hero-abc/entry.md",
                    "chats/x.md",
                ),
            ),
        )
        assertTrue(
            NovelcrafterZipParser.looksLikeNovelcrafterZip(
                listOf("novel.docx", "characters/hero-abc/metadata.json"),
            ),
        )
        assertFalse(
            NovelcrafterZipParser.looksLikeNovelcrafterZip(
                listOf("project.json", "manuscript.md"),
            ),
        )
    }

    @Test
    fun parsesNovelMdHierarchy() {
        val md = """
            # Mini Novel
            by Test Author

            ## Act 1

            ### Chapter 1

            A short summary of the opening.

            ---

            The rain hit the docks.

            * * *

            She finds the key under the mat.

            ---

            #### Chapter 1 – Scene 2

            Under the mat: cold iron.
        """.trimIndent()
        val (title, author, acts) = NovelcrafterZipParser.parseNovelMd(md)
        assertEquals("Mini Novel", title)
        assertEquals("Test Author", author)
        assertEquals(1, acts.size)
        assertEquals("Act 1", acts[0].title)
        assertEquals(1, acts[0].chapters.size)
        assertEquals(2, acts[0].chapters[0].scenes.size)
        assertEquals("A short summary of the opening.", acts[0].chapters[0].scenes[0].summary)
        assertTrue(acts[0].chapters[0].scenes[0].prose.contains("rain hit the docks"))
        assertEquals("Chapter 1 – Scene 2", acts[0].chapters[0].scenes[1].title)
        assertTrue(acts[0].chapters[0].scenes[1].prose.contains("cold iron"))
    }

    @Test
    fun parsesIsekaiGachaWordZipIfPresent() {
        val sample = listOf(
            File("samples/isekai-gacha-full-word.zip"),
            File("../samples/isekai-gacha-full-word.zip"),
        ).firstOrNull { it.exists() } ?: return
        val bytes = sample.readBytes()
        assertTrue(NovelcrafterZipParser.looksLikeNovelcrafterZipBytes(bytes))
        val parsed = NovelcrafterZipParser.parse(bytes)
        assertEquals("Isekai Gacha", parsed.bookTitle)
        assertTrue(parsed.acts.isNotEmpty())
        assertTrue(parsed.codexEntries.size > 10)
        assertTrue(parsed.codexEntries.any { it.categoryFolder == "characters" })
        assertTrue(parsed.codexEntries.any { it.categoryFolder == "locations" })
        assertTrue(parsed.chats.isNotEmpty())
    }
}
