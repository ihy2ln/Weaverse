package com.ihy2ln.weaverse.sync

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SyncPackageTest {
    @TempDir
    lateinit var temp: File

    @Test
    fun roundTripPackage() {
        val db = File(temp, "weaverse.db").also { it.writeText("db-bytes") }
        val media = File(temp, "media").also { it.mkdirs() }
        File(media, "a.png").writeText("png")
        val zip = File(temp, "pack.zip")
        val manifest = SyncPackage.writePackage(
            dbFile = db,
            mediaDir = media,
            outZip = zip,
            deviceId = "dev-1",
            deviceName = "Test",
            appVersion = "0.4.0",
            bookCount = 2,
            noteCount = 3,
        )
        assertEquals(2, manifest.bookCount)
        assertEquals(1, manifest.mediaFileCount)
        assertTrue(zip.exists())

        val outDb = File(temp, "out/weaverse.db")
        val outMedia = File(temp, "out/media")
        SyncPackage.restoreInto(zip, outDb, outMedia)
        assertEquals("db-bytes", outDb.readText())
        assertEquals("png", File(outMedia, "a.png").readText())
        assertEquals("dev-1", SyncPackage.readManifest(zip)?.deviceId)
    }

    @Test
    fun extractIgnoresEntriesThatEscapeTheWorkDir() {
        val zip = File(temp, "evil.zip")
        ZipOutputStream(FileOutputStream(zip)).use { out ->
            out.putNextEntry(ZipEntry("../escaped.txt"))
            out.write("pwned".toByteArray())
            out.closeEntry()
            out.putNextEntry(ZipEntry("media/ok.png"))
            out.write("png".toByteArray())
            out.closeEntry()
        }
        val work = File(temp, "work")
        SyncPackage.extractTo(zip, work)

        assertFalse(File(temp, "escaped.txt").exists(), "entry escaped the work dir")
        assertEquals("png", File(work, "media/ok.png").readText())
    }

    @Test
    fun safeChildRejectsTraversalAndAbsolutePaths() {
        val root = File(temp, "root").also { it.mkdirs() }
        assertNull(SyncPackage.safeChild(root, "../out.txt"))
        assertNull(SyncPackage.safeChild(root, "a/../../out.txt"))
        assertNull(SyncPackage.safeChild(root, "/etc/passwd"))
        assertNull(SyncPackage.safeChild(root, "..\\out.txt"))
        assertNull(SyncPackage.safeChild(root, "C:\\out.txt"))
        assertNull(SyncPackage.safeChild(root, ""))
        assertNotNull(SyncPackage.safeChild(root, "media/a.png"))
    }
}
