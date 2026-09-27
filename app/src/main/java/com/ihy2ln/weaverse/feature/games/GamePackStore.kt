package com.ihy2ln.weaverse.feature.games

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton

/** The Godot runtime bundled in the APK (`app/libs/godot-lib-*.aar`). A pack must be exported with it. */
object GodotRuntime {
    const val ENGINE_VERSION = "4.7.1.stable"
}

/** `weaverse-game.json` at the root of a pack, written by `tools/build_game_pack.py`. */
@Serializable
data class GamePackManifest(
    val schema: Int = 1,
    val id: String = "adams-haven",
    val title: String = "Adams Haven",
    val version: String = "",
    val engine: String = "",
    val files: Int = 0,
    val byteSize: Long = 0,
    val source: String = "",
)

data class InstalledGamePack(
    val manifest: GamePackManifest,
    val file: File,
    val sizeOnDisk: Long,
)

/**
 * Where the Adams Haven game data lives on the device.
 *
 * The game itself is the Godot project, exported and packed as one zip (about 1.3 GB), so it
 * is never inside the APK. It is copied into app-specific external storage, where the Godot
 * runtime in [AdamsHavenGameActivity] opens it with `--main-pack`. For testing, the same file
 * can be pushed there with adb; see [packFile].
 */
@Singleton
class GamePackStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun packFile(): File = packFile(context)

    suspend fun installed(): InstalledGamePack? = withContext(Dispatchers.IO) {
        val file = packFile()
        if (!file.isFile) return@withContext null
        val manifest = runCatching { readManifest(file) }.getOrNull() ?: return@withContext null
        InstalledGamePack(manifest, file, file.length())
    }

    /**
     * Copies the pack at [uri] into place. The copy goes to a `.part` file and only replaces the
     * installed pack once it has been checked, so a failed or cancelled import leaves the old
     * game playable.
     */
    suspend fun import(uri: Uri, onProgress: (progress: Float, copied: Long) -> Unit): Result<GamePackManifest> =
        withContext(Dispatchers.IO) {
            val target = packFile()
            val source = File(target.parentFile, target.name + ".source.part")
            runCatching {
                val total = sizeOf(uri)
                val free = target.parentFile?.usableSpace ?: Long.MAX_VALUE
                if (total > 0 && total * 2 > free) {
                    error("Not enough space: importing needs about ${formatBytes(total * 2)} free.")
                }
                val input = context.contentResolver.openInputStream(uri) ?: error("Could not open the file.")
                input.use { src ->
                    source.outputStream().use { dst ->
                        val buffer = ByteArray(1 shl 20)
                        var copied = 0L
                        var lastReport = 0L
                        while (true) {
                            val read = src.read(buffer)
                            if (read < 0) break
                            dst.write(buffer, 0, read)
                            copied += read
                            if (copied - lastReport >= (8 shl 20)) {
                                lastReport = copied
                                onProgress(if (total > 0) 0.5f * copied / total else -1f, copied)
                            }
                        }
                        onProgress(if (total > 0) 0.5f else -1f, copied)
                    }
                }
                installSource(source, target, versionFromName(nameOf(uri))) { copied, size ->
                    onProgress(if (size > 0) 0.5f + 0.5f * copied / size else -1f, copied)
                }
            }.also { source.delete() }
        }

    /** Downloads the official release, checks GitHub's SHA-256, then imports its APK assets. */
    suspend fun update(release: GameRelease, onProgress: (Float, Long) -> Unit): Result<GamePackManifest> =
        withContext(Dispatchers.IO) {
            val target = packFile()
            val source = File(target.parentFile, target.name + ".download.part")
            runCatching {
                val connection = (URL(release.downloadUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 20_000
                    readTimeout = 30_000
                    setRequestProperty("User-Agent", "Weaverse-Games")
                }
                try {
                    if (connection.responseCode != 200) error("Download failed: HTTP ${connection.responseCode}.")
                    val total = connection.contentLengthLong
                    val free = target.parentFile?.usableSpace ?: Long.MAX_VALUE
                    if (total > 0 && total * 2 > free) error("Not enough space: the update needs about ${formatBytes(total * 2)} free.")
                    val digest = MessageDigest.getInstance("SHA-256")
                    var copied = 0L
                    connection.inputStream.use { input ->
                        source.outputStream().use { output ->
                            val buffer = ByteArray(1 shl 20)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                output.write(buffer, 0, count)
                                digest.update(buffer, 0, count)
                                copied += count
                                if (copied % (8L shl 20) < count) {
                                    onProgress(if (total > 0) 0.5f * copied / total else -1f, copied)
                                }
                            }
                        }
                    }
                    val actual = digest.digest().joinToString("") { "%02x".format(it) }
                    require(actual == release.sha256) { "The download did not match the release checksum." }
                } finally {
                    connection.disconnect()
                }
                installSource(source, target, release.version) { copied, size ->
                    onProgress(if (size > 0) 0.5f + 0.5f * copied / size else -1f, copied)
                }
            }.also { source.delete() }
        }

    private fun installSource(
        source: File,
        target: File,
        version: String,
        onProgress: (Long, Long) -> Unit,
    ): GamePackManifest {
        val part = File(target.parentFile, target.name + ".part")
        try {
            val isApk = ZipFile(source).use { it.getEntry("assets/project.binary") != null }
            if (isApk) {
                val apkVersion = version.ifBlank {
                    runCatching {
                        context.packageManager.getPackageArchiveInfo(source.absolutePath, 0)?.versionName.orEmpty()
                    }.getOrDefault("")
                }
                GameApkImporter.convert(source, part, apkVersion, onProgress)
            } else {
                Files.move(source.toPath(), part.toPath(), StandardCopyOption.REPLACE_EXISTING)
                onProgress(part.length(), part.length())
            }
            val manifest = readManifest(part)
            require(manifest.id == "adams-haven") { "This is not an Adams Haven game pack." }
            require(manifest.engine == GodotRuntime.ENGINE_VERSION) {
                "This game needs Godot ${manifest.engine}; Weaverse runs ${GodotRuntime.ENGINE_VERSION}."
            }
            Files.move(part.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            return manifest
        } finally {
            part.delete()
        }
    }

    private fun versionFromName(name: String): String =
        Regex("v(\\d+\\.\\d+\\.\\d+)").find(name)?.groupValues?.get(1).orEmpty()

    private fun nameOf(uri: Uri): String = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: uri.lastPathSegment.orEmpty()
    }.getOrDefault(uri.lastPathSegment.orEmpty())

    suspend fun remove(): Boolean = withContext(Dispatchers.IO) {
        val file = packFile()
        !file.exists() || file.delete()
    }

    /** Reads the manifest, and proves the zip is a Godot export by finding `project.binary`. */
    private fun readManifest(file: File): GamePackManifest = ZipFile(file).use { zip ->
        if (zip.getEntry("project.binary") == null) {
            error("That file is not an Adams Haven game pack (no Godot project inside).")
        }
        if (zip.getEntry(OVERRIDE_ENTRY) == null) {
            error("That Godot export was not packed for Weaverse. Build it with tools/build_game_pack.py.")
        }
        zip.getEntry(MANIFEST)?.let { entry ->
            json.decodeFromString<GamePackManifest>(zip.getInputStream(entry).bufferedReader().readText())
        } ?: error("This game pack has no Weaverse manifest.")
    }

    private fun sizeOf(uri: Uri): Long = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else -1L
        } ?: -1L
    }.getOrDefault(-1L)

    companion object {
        private const val MANIFEST = "weaverse-game.json"

        /** The game's project settings, as the boot project's override file (see AdamsHavenGameActivity). */
        const val OVERRIDE_ENTRY = "weaverse-game/override.cfg"
        private const val PACK_NAME = "adams-haven.zip"

        /** `Android/data/<package>/files/games/adams-haven.zip`, or internal storage if there is no external volume. */
        fun packFile(context: Context): File {
            val dir = context.getExternalFilesDir("games") ?: File(context.filesDir, "games")
            dir.mkdirs()
            return File(dir, PACK_NAME)
        }
    }
}

internal fun formatBytes(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f GB".format(bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> "%.0f MB".format(bytes / (1L shl 20).toDouble())
    else -> "%.0f KB".format(bytes / 1024.0)
}
