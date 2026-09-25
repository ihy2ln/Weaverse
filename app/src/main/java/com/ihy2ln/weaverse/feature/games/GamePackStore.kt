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
    suspend fun import(uri: Uri, onProgress: (copied: Long, total: Long) -> Unit): Result<GamePackManifest> =
        withContext(Dispatchers.IO) {
            val target = packFile()
            val part = File(target.parentFile, target.name + ".part")
            runCatching {
                val total = sizeOf(uri)
                val free = target.parentFile?.usableSpace ?: Long.MAX_VALUE
                if (total > 0 && total > free) {
                    error("Not enough space: the pack needs ${formatBytes(total)}, ${formatBytes(free)} is free.")
                }
                val input = context.contentResolver.openInputStream(uri) ?: error("Could not open the file.")
                input.use { src ->
                    part.outputStream().use { dst ->
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
                                onProgress(copied, total)
                            }
                        }
                        onProgress(copied, total)
                    }
                }
                val manifest = readManifest(part)
                if (manifest.engine.isNotBlank() && manifest.engine != GodotRuntime.ENGINE_VERSION) {
                    error(
                        "This pack was exported with Godot ${manifest.engine}. Weaverse runs " +
                            "Godot ${GodotRuntime.ENGINE_VERSION}; re-export it with that version.",
                    )
                }
                if (target.exists() && !target.delete()) error("Could not replace the installed game.")
                if (!part.renameTo(target)) error("Could not move the game into place.")
                manifest
            }.onFailure { part.delete() }
        }

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
        } ?: GamePackManifest(byteSize = file.length())
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
