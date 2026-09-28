package com.ihy2ln.weaverse.core.manga

import com.ihy2ln.weaverse.data.backup.BackupArchives
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class MihonBackupTest {
    /** Tiny protobuf writer, enough to build a backup the way Mihon serializes one. */
    private class Pb {
        val out = ByteArrayOutputStream()
        private fun varint(v: Long) { var x = v; while (x and 0x7fL.inv() != 0L) { out.write(((x and 0x7f) or 0x80).toInt()); x = x ushr 7 }; out.write(x.toInt()) }
        fun int(field: Int, v: Long) = apply { varint((field shl 3).toLong()); varint(v) }
        fun bytes(field: Int, b: ByteArray) = apply { varint(((field shl 3) or 2).toLong()); varint(b.size.toLong()); out.write(b) }
        fun str(field: Int, s: String) = bytes(field, s.toByteArray())
        fun msg(field: Int, m: Pb) = bytes(field, m.out.toByteArray())
        fun float(field: Int, f: Float) = apply {
            varint(((field shl 3) or 5).toLong())
            val bits = java.lang.Float.floatToIntBits(f)
            for (i in 0 until 4) out.write(bits ushr (8 * i) and 0xff)
        }
    }

    private fun sampleBackup(): ByteArray {
        val chapter = Pb().str(1, "/chapter/1").str(2, "Chapter 1").int(4, 1).int(5, 1).int(6, 12).float(9, 1f)
        val chapter2 = Pb().str(1, "/chapter/2").str(2, "Chapter 2").float(9, 2f)
        val manga = Pb().int(1, 2499283573021220255).str(2, "/manga/abc").str(3, "Sample Title")
            .str(5, "Author A").str(7, "Action, Comedy").int(8, 1).str(9, "https://img/cover.jpg")
            .msg(16, chapter).msg(16, chapter2).int(17, 0)
            .msg(104, Pb().str(1, "/chapter/1").int(2, 1_700_000_000_000))
            // an unknown tracking field must be skipped
            .msg(18, Pb().int(1, 5).str(2, "tracker"))
        val notInLibrary = Pb().int(1, 1).str(2, "/manga/old").str(3, "Old").int(100, 0)
        val backup = Pb().msg(1, manga).msg(1, notInLibrary)
            .msg(2, Pb().str(1, "Reading").int(2, 0))
            .msg(101, Pb().str(1, "MangaDex").int(2, 2499283573021220255))
        val gz = ByteArrayOutputStream()
        GZIPOutputStream(gz).use { it.write(backup.out.toByteArray()) }
        return gz.toByteArray()
    }

    @Test
    fun readsLibraryChaptersCategoriesAndReadingState() {
        val backup = MihonBackup.parse(sampleBackup())

        assertEquals(2, backup.manga.size)
        val manga = backup.manga.first()
        assertEquals(2499283573021220255, manga.source)
        assertEquals("/manga/abc", manga.url)
        assertEquals("Sample Title", manga.title)
        assertEquals(listOf("Action, Comedy"), manga.genres)
        assertEquals("Ongoing", MihonBackup.statusLabel(manga.status))
        assertTrue(manga.favorite)
        assertEquals(listOf(0L), manga.categories)
        assertEquals(2, manga.chapters.size)
        with(manga.chapters.first()) {
            assertTrue(read); assertTrue(bookmark); assertEquals(12L, lastPageRead); assertEquals(1f, chapterNumber)
        }
        assertFalse(manga.chapters[1].read)
        assertEquals(1_700_000_000_000, manga.history.single().lastRead)
        assertFalse(backup.manga[1].favorite)
        assertEquals("Reading", backup.categories.single().name)
        assertEquals("MangaDex", backup.sources.single().name)
    }

    @Test
    fun rejectsFilesThatAreNotBackups() {
        assertThrows<IllegalArgumentException> { MihonBackup.parse(ByteArray(0)) }
    }

    @Test
    fun downloadedChapterPagesAreRestoredSafely() {
        assertEquals("manga-chapter-x/1.jpg", BackupArchives.mangaRelativePath("manga/manga-chapter-x/1.jpg"))
        assertNull(BackupArchives.mangaRelativePath("manga/../weaverse.db"))
        assertNull(BackupArchives.mangaRelativePath("media/a.png"))
    }
}
