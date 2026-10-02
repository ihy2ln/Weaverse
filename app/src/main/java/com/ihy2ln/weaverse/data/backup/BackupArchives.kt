package com.ihy2ln.weaverse.data.backup

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Shared zip layout for phone Restore and PC extract-into-Weaverse. */
object BackupArchives {
    const val MOBILE_DB = "weaverse.db"
    const val MOBILE_WAL = "weaverse.db-wal"
    const val MOBILE_SHM = "weaverse.db-shm"
    const val MOBILE_MEDIA = "media/"
    const val MOBILE_SETTINGS = "settings/"
    /** Downloaded chapter pages (files/manga); without them restored chapters had no pictures. */
    const val MOBILE_MANGA = "manga/"
    const val PC_DB = "data/weaverse.db"
    const val PC_MEDIA = "data/media/"
    const val MANIFEST = "manifest.json"
    const val PC_README = "RESTORE-ON-PC.txt"

    val PC_README_TEXT = """
Weaverse PC backup
==================
Extract this zip into your Weaverse folder (the folder that already has a data/ directory,
or the folder that contains Weaverse.exe).

You should end up with:
  data/weaverse.db
  data/media/...

Then start Weaverse.exe. The web hub reads that same data folder.

Do not unzip only the inner files onto the desktop — keep the data/ folder name.
""".trimIndent()

    fun mobileZipName(timestamp: Long): String = "weaverse-backup-mobile-$timestamp.zip"
    fun pcZipName(timestamp: Long): String = "weaverse-backup-pc-$timestamp.zip"

    fun isMobileDbEntry(name: String): Boolean =
        name == MOBILE_DB || name == MOBILE_WAL || name == MOBILE_SHM

    fun isPcDbEntry(name: String): Boolean = name == PC_DB

    /** Relative path under the app media dir, or null if not a media entry. */
    fun mediaRelativePath(entryName: String): String? = when {
        entryName.startsWith(MOBILE_MEDIA) && !entryName.endsWith("/") ->
            safeRelative(entryName.removePrefix(MOBILE_MEDIA))
        entryName.startsWith(PC_MEDIA) && !entryName.endsWith("/") ->
            safeRelative(entryName.removePrefix(PC_MEDIA))
        else -> null
    }

    fun mangaRelativePath(entryName: String): String? {
        if (!entryName.startsWith(MOBILE_MANGA) || entryName.endsWith("/")) return null
        return safeRelative(entryName.removePrefix(MOBILE_MANGA))
    }

    fun settingsRelativePath(entryName: String): String? {
        if (!entryName.startsWith(MOBILE_SETTINGS) || entryName.endsWith("/")) return null
        return safeRelative(entryName.removePrefix(MOBILE_SETTINGS))
    }

    /**
     * A zip entry's path only if it stays inside its folder: no `..`, no absolute or drive
     * paths, no backslashes. A crafted backup can't write anywhere else in the app.
     */
    fun safeRelative(path: String): String? = path.takeUnless {
        it.isBlank() || it.startsWith("/") || it.contains('\\') || it.contains(':') ||
            it.split('/').any { part -> part == ".." || part == "." }
    }

    /** [relative] inside [base], or null if it would land outside it once resolved. */
    fun childInside(base: File, relative: String): File? {
        val safe = safeRelative(relative) ?: return null
        val root = base.canonicalFile
        val child = File(root, safe).canonicalFile
        return child.takeIf { it.path.startsWith(root.path + File.separator) }
    }

    /** The export timestamp in a backup zip's name, which pairs its mobile and PC zips. */
    fun backupTimestamp(fileName: String): Long? =
        Regex("^weaverse-backup-(?:mobile|pc)-(\\d+)\\.zip$").find(fileName)?.groupValues?.get(1)?.toLongOrNull()

    fun packMobile(
        zipFile: File,
        sources: BackupSources,
        manifestJson: String,
    ) {
        zipFile.parentFile?.mkdirs()
        ZipOutputStream(FileOutputStream(zipFile)).use { zip ->
            zip.putFileIfExists(MOBILE_DB, sources.dbFile)
            zip.putFileIfExists(MOBILE_WAL, sources.walFile)
            zip.putFileIfExists(MOBILE_SHM, sources.shmFile)
            zip.putTree(MOBILE_MEDIA, sources.mediaDir)
            zip.putTree(MOBILE_SETTINGS, sources.datastoreDir)
            zip.putTree(MOBILE_MANGA, sources.mangaDir)
            zip.putBytes(MANIFEST, manifestJson.toByteArray())
        }
    }

    fun packPc(
        zipFile: File,
        sources: BackupSources,
        manifestJson: String,
        readme: String = PC_README_TEXT,
    ) {
        zipFile.parentFile?.mkdirs()
        ZipOutputStream(FileOutputStream(zipFile)).use { zip ->
            zip.putFileIfExists(PC_DB, sources.dbFile)
            zip.putTree(PC_MEDIA, sources.mediaDir)
            zip.putBytes(MANIFEST, manifestJson.toByteArray())
            zip.putBytes(PC_README, readme.toByteArray())
        }
    }
}

data class BackupSources(
    val dbFile: File,
    val walFile: File? = null,
    val shmFile: File? = null,
    val mediaDir: File? = null,
    val datastoreDir: File? = null,
    val mangaDir: File? = null,
)

data class BackupExportResult(
    val mobileZip: File,
    val pcZip: File,
) {
    fun statusMessage(): String =
        "Mobile backup: ${mobileZip.absolutePath}\nPC backup: ${pcZip.absolutePath}"
}

internal fun ZipOutputStream.putBytes(entryName: String, bytes: ByteArray) {
    putNextEntry(ZipEntry(entryName))
    write(bytes)
    closeEntry()
}

internal fun ZipOutputStream.putFileIfExists(entryName: String, file: File?) {
    if (file == null || !file.isFile) return
    putNextEntry(ZipEntry(entryName))
    FileInputStream(file).use { it.copyTo(this) }
    closeEntry()
}

internal fun ZipOutputStream.putTree(entryPrefix: String, dir: File?) {
    if (dir == null || !dir.isDirectory) return
    dir.walkTopDown().filter { it.isFile }.forEach { file ->
        val relative = file.relativeTo(dir).path.replace('\\', '/')
        putNextEntry(ZipEntry("$entryPrefix$relative"))
        FileInputStream(file).use { it.copyTo(this) }
        closeEntry()
    }
}
