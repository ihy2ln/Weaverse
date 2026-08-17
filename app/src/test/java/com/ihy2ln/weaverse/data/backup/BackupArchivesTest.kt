package com.ihy2ln.weaverse.data.backup

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.zip.ZipFile

class BackupArchivesTest {
    @TempDir
    lateinit var temp: File

    @Test
    fun packMobileAndPcWriteSeparateZips() {
        val db = File(temp, "weaverse.db").apply { writeText("sqlite") }
        val media = File(temp, "media").apply { mkdirs() }
        File(media, "cover.png").writeText("png")
        val sources = BackupSources(dbFile = db, mediaDir = media)
        val mobile = File(temp, BackupArchives.mobileZipName(1L))
        val pc = File(temp, BackupArchives.pcZipName(1L))
        BackupArchives.packMobile(mobile, sources, """{"exportedAt":1,"version":2}""")
        BackupArchives.packPc(pc, sources, """{"exportedAt":1,"version":2}""")

        ZipFile(mobile).use { zip ->
            val names = zip.entries().toList().map { it.name }.toSet()
            assertTrue(names.contains(BackupArchives.MOBILE_DB))
            assertTrue(names.any { it.startsWith(BackupArchives.MOBILE_MEDIA) })
            assertTrue(names.contains(BackupArchives.MANIFEST))
        }
        ZipFile(pc).use { zip ->
            val names = zip.entries().toList().map { it.name }.toSet()
            assertTrue(names.contains(BackupArchives.PC_DB))
            assertTrue(names.any { it.startsWith(BackupArchives.PC_MEDIA) })
            assertTrue(names.contains(BackupArchives.PC_README))
            val readme = zip.getInputStream(zip.getEntry(BackupArchives.PC_README)).reader().readText()
            assertTrue(readme.contains("Weaverse.exe"))
        }
    }

    @Test
    fun mediaRelativePathReadsBothLayouts() {
        assertEquals("cover.png", BackupArchives.mediaRelativePath("media/cover.png"))
        assertEquals("cover.png", BackupArchives.mediaRelativePath("data/media/cover.png"))
        assertNull(BackupArchives.mediaRelativePath("manifest.json"))
    }

    @Test
    fun zipNamesIdentifyPlatform() {
        assertTrue(BackupArchives.mobileZipName(9).contains("-mobile-"))
        assertTrue(BackupArchives.pcZipName(9).contains("-pc-"))
    }
}
