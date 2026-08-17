package com.ihy2ln.weaverse.sync.novelcrafter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class NovelcrafterZipParserTest {
    @Test
    fun detectsMdAndDocxLayouts() {
        assertTrue(
            NovelcrafterZipParser.looksLikeNovelcrafterZip(
                listOf("novel.md", "characters/hero-abc/metadata.json"),
            ),
        )
        assertTrue(
            NovelcrafterZipParser.looksLikeNovelcrafterZip(
                listOf("novel.docx", "characters/hero-abc/entry.md", "locations/town/metadata.json"),
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
    fun wordHeadingHeuristicsPromoteDayAndChapter() {
        val raw = """
            Isekai Gacha

            Day 1

            Chapter 1

            The loading screen had been going.

            Chapter 2

            Chapter 2 - Scene 1

            Elowen raised her blade.
        """.trimIndent()
        val headed = WordHeadingHeuristics.apply(raw)
        val (title, _, acts) = NovelcrafterZipParser.parseNovelMd(headed)
        assertEquals("Isekai Gacha", title)
        assertEquals("Day 1", acts[0].title)
        assertEquals("Chapter 1", acts[0].chapters[0].title)
        assertTrue(acts[0].chapters[0].scenes[0].prose.contains("loading screen"))
        assertEquals("Chapter 2", acts[0].chapters[1].title)
        assertEquals("Chapter 2 - Scene 1", acts[0].chapters[1].scenes[0].title)
        assertTrue(acts[0].chapters[1].scenes[0].prose.contains("Elowen"))
    }

    @Test
    fun parsesInMemoryDocxZip() {
        val bytes = miniDocxZip()
        assertTrue(NovelcrafterZipParser.looksLikeNovelcrafterZipBytes(bytes))
        val parsed = NovelcrafterZipParser.parse(bytes)
        assertEquals("Mini Novel", parsed.bookTitle)
        assertEquals("novel.docx", parsed.manuscriptSource)
        assertEquals(1, parsed.acts.size)
        assertEquals("Day 1", parsed.acts[0].title)
        assertEquals("Chapter 1", parsed.acts[0].chapters[0].title)
        assertTrue(parsed.acts[0].chapters[0].scenes[0].prose.contains("rain hit the docks"))
        assertEquals(1, parsed.codexEntries.size)
        assertEquals("Mira", parsed.codexEntries[0].name)
        assertEquals("characters", parsed.codexEntries[0].categoryFolder)
    }

    @Test
    fun sqliteImportWritesCategoriesAndArt(@TempDir dir: File) {
        val db = File(dir, "weaverse.db")
        val media = File(dir, "media")
        val result = NovelcrafterSqliteImporter.importZip(miniDocxZip(), db, media)
        assertEquals("Mini Novel", result.bookTitle)
        assertTrue(result.sceneCount >= 1)
        assertEquals(1, result.codexCount)
        assertTrue(result.rpCharacterCount >= 1)
        assertEquals(3, result.rpChatCount)
        assertTrue(result.mediaCount >= 1)
        assertTrue(ImportArt.loadBytes("novel-forest-path.jpg") != null)
        assertTrue(sqliteBooks(db).contains("Mini Novel"))
    }

    @Test
    fun parsesRealIsekaiGachaWordZipIfPresent() {
        val candidates = listOf(
            File("samples/isekai-gacha-full-word.zip"),
            File("../samples/isekai-gacha-full-word.zip"),
            File("/workspace/samples/isekai-gacha-full-word.zip"),
        )
        val sample = candidates.firstOrNull { it.exists() } ?: return
        val parsed = NovelcrafterZipParser.parse(sample.readBytes())
        assertEquals("Isekai Gacha", parsed.bookTitle)
        assertEquals("novel.docx", parsed.manuscriptSource)
        assertTrue(parsed.acts.isNotEmpty(), "expected Day/Chapter acts from Word headings")
        assertTrue(parsed.acts.any { it.title.startsWith("Day") })
        assertTrue(parsed.acts.flatMap { it.chapters }.any { it.title.startsWith("Chapter") })
        assertTrue(parsed.codexEntries.size >= 50)
        assertTrue(parsed.codexEntries.any { it.categoryFolder == "characters" })
        assertTrue(parsed.codexEntries.any { it.categoryFolder == "locations" })
        assertTrue(parsed.codexEntries.any { it.categoryFolder == "lore" })
        assertTrue(parsed.codexEntries.any { it.categoryFolder == "objects" })
        assertTrue(parsed.chats.isNotEmpty())
        assertTrue(parsed.snippets.isNotEmpty())
    }

    private fun miniDocxZip(): ByteArray {
        val docx = minimalDocx(
            listOf(
                "Mini Novel",
                "Day 1",
                "Chapter 1",
                "The rain hit the docks.",
            ),
        )
        val meta = """{"id":"mira1","attributes":{"name":"Mira","color":"blue","aliases":["M"],"alwaysIncludeInContext":true}}"""
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun put(name: String, data: ByteArray) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(data)
                zip.closeEntry()
            }
            put("novel.docx", docx)
            put("characters/mira-mira1/metadata.json", meta.toByteArray())
            put("characters/mira-mira1/entry.md", "A sharp-eyed local.\n".toByteArray())
        }
        return out.toByteArray()
    }

    private fun minimalDocx(paragraphs: List<String>): ByteArray {
        val documentXml = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">""")
            append("<w:body>")
            paragraphs.forEach { para ->
                append("<w:p><w:r><w:t xml:space=\"preserve\">")
                append(para.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"))
                append("</w:t></w:r></w:p>")
            }
            append("<w:sectPr/></w:body></w:document>")
        }
        val contentTypes = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
              <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
              <Default Extension="xml" ContentType="application/xml"/>
              <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
            </Types>
        """.trimIndent()
        val rels = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
            </Relationships>
        """.trimIndent()
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun put(name: String, text: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray())
                zip.closeEntry()
            }
            put("[Content_Types].xml", contentTypes)
            put("_rels/.rels", rels)
            put("word/document.xml", documentXml)
        }
        return out.toByteArray()
    }
}

private fun sqliteBooks(db: File): String {
    Class.forName("org.sqlite.JDBC")
    return java.sql.DriverManager.getConnection("jdbc:sqlite:${db.absolutePath}").use { conn ->
        conn.createStatement().use { st ->
            st.executeQuery("SELECT title FROM books").use { rs ->
                buildString {
                    while (rs.next()) append(rs.getString(1)).append('\n')
                }
            }
        }
    }
}
