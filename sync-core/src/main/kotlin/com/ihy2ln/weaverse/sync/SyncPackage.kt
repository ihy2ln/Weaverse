package com.ihy2ln.weaverse.sync

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object SyncPackage {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun writePackage(
        dbFile: File?,
        mediaDir: File?,
        outZip: File,
        deviceId: String,
        deviceName: String,
        appVersion: String = "",
        bookCount: Int = 0,
        noteCount: Int = 0,
    ): SyncManifest {
        outZip.parentFile?.mkdirs()
        var mediaFileCount = 0
        val exportedAt = System.currentTimeMillis()
        ZipOutputStream(FileOutputStream(outZip)).use { zip ->
            if (dbFile != null && dbFile.exists()) {
                zip.putNextEntry(ZipEntry("weaverse.db"))
                FileInputStream(dbFile).use { it.copyTo(zip) }
                zip.closeEntry()
                listOf("-wal", "-shm").forEach { suffix ->
                    val extra = File(dbFile.path + suffix)
                    if (extra.exists() && extra.length() > 0) {
                        zip.putNextEntry(ZipEntry("weaverse.db$suffix"))
                        FileInputStream(extra).use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            }
            if (mediaDir != null && mediaDir.exists()) {
                mediaDir.walkTopDown().filter { it.isFile }.forEach { file ->
                    val relative = file.relativeTo(mediaDir).invariantSeparatorsPath
                    zip.putNextEntry(ZipEntry("media/$relative"))
                    FileInputStream(file).use { it.copyTo(zip) }
                    zip.closeEntry()
                    mediaFileCount++
                }
            }
            val manifest = SyncManifest(
                protocolVersion = SYNC_PROTOCOL_VERSION,
                exportedAt = exportedAt,
                deviceId = deviceId,
                deviceName = deviceName,
                appVersion = appVersion,
                noteCount = noteCount,
                bookCount = bookCount,
                mediaFileCount = mediaFileCount,
            )
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(json.encodeToString(manifest).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            return manifest
        }
    }

    fun readManifest(zipFile: File): SyncManifest? {
        if (!zipFile.exists()) return null
        ZipInputStream(FileInputStream(zipFile)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "manifest.json") {
                    val text = zip.readBytes().toString(Charsets.UTF_8)
                    return json.decodeFromString(SyncManifest.serializer(), text)
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return null
    }

    /**
     * Extracts a sync ZIP into [workDir]:
     * - weaverse.db
     * - media/
     * - manifest.json
     */
    fun extractTo(zipFile: File, workDir: File) {
        workDir.mkdirs()
        ZipInputStream(FileInputStream(zipFile)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val out = File(workDir, entry.name)
                    out.parentFile?.mkdirs()
                    FileOutputStream(out).use { zip.copyTo(it) }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    fun restoreInto(
        zipFile: File,
        dbFile: File,
        mediaDir: File,
    ) {
        val staging = File(zipFile.parentFile, "restore-staging-${System.currentTimeMillis()}")
        try {
            extractTo(zipFile, staging)
            val stagedDb = File(staging, "weaverse.db")
            if (stagedDb.exists()) {
                dbFile.parentFile?.mkdirs()
                stagedDb.copyTo(dbFile, overwrite = true)
                listOf("-wal", "-shm").forEach { suffix ->
                    val staged = File(staging, "weaverse.db$suffix")
                    val target = File(dbFile.path + suffix)
                    if (staged.exists()) {
                        staged.copyTo(target, overwrite = true)
                    } else if (target.exists()) {
                        target.delete()
                    }
                }
            }
            val stagedMedia = File(staging, "media")
            if (stagedMedia.exists()) {
                mediaDir.mkdirs()
                stagedMedia.walkTopDown().filter { it.isFile }.forEach { file ->
                    val target = File(mediaDir, file.relativeTo(stagedMedia).invariantSeparatorsPath)
                    target.parentFile?.mkdirs()
                    file.copyTo(target, overwrite = true)
                }
            }
        } finally {
            staging.deleteRecursively()
        }
    }
}
