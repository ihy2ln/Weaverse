package com.ihy2ln.weaverse.data.export

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.zip.ZipFile

class EpubWriterTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun write_storesMimetypeUncompressedAndIncludesChapters() {
        val file = File(tempDir, "book.epub")
        EpubWriter.write(
            file,
            "Harbor Lights",
            listOf(
                EpubChapter("Act I · Dawn", "The tide came in.\n\nMira drew the pier."),
                EpubChapter("Act II · Dusk", "Lamps woke along the quay."),
            ),
        )
        assertTrue(file.isFile)
        ZipFile(file).use { zip ->
            val names = zip.entries().toList().map { it.name }
            assertEquals("mimetype", names.first())
            val mime = zip.getEntry("mimetype")
            assertEquals(java.util.zip.ZipEntry.STORED, mime.method)
            assertEquals(
                "application/epub+zip",
                zip.getInputStream(mime).readBytes().toString(Charsets.US_ASCII),
            )
            assertTrue(names.contains("META-INF/container.xml"))
            assertTrue(names.contains("OEBPS/content.opf"))
            assertTrue(names.contains("OEBPS/chapter-1.xhtml"))
            assertTrue(names.contains("OEBPS/chapter-2.xhtml"))
            val chapter = zip.getInputStream(zip.getEntry("OEBPS/chapter-1.xhtml"))
                .readBytes().toString(Charsets.UTF_8)
            assertTrue(chapter.contains("Harbor") || chapter.contains("tide") || chapter.contains("Dawn"))
        }
    }
}
