package com.ihy2ln.weaverse.sync

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

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
}
