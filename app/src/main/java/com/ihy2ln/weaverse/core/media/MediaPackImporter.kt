package com.ihy2ln.weaverse.core.media

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/** Manifest at the root of a media pack zip, produced by `tools/build_media_packs.py`. */
@Serializable
data class MediaPackManifest(
    val schema: Int = 1,
    val id: String,
    val name: String = "",
    val version: Int = 1,
    val description: String = "",
    val byteSize: Long = 0,
    val items: List<MediaPackItem> = emptyList(),
)

@Serializable
data class MediaPackItem(
    val mediaId: String,
    val relativePath: String,
    val type: String = "image",
    val width: Int = 0,
    val height: Int = 0,
    val displayName: String = "",
    val category: String = "",
    val tags: String = "",
)

/** One installed pack, as recorded in `filesDir/media-packs/installed.json`. */
@Serializable
data class InstalledMediaPack(
    val id: String,
    val name: String,
    val version: Int,
    val itemCount: Int,
    val byteSize: Long,
    val installedAt: Long,
)

data class MediaPackResult(
    val id: String,
    val name: String,
    val version: Int,
    val installed: Int,
    val failed: Int,
    val byteSize: Long,
)

/**
 * Installs downloadable art packs on top of the small core art bundled in the APK.
 *
 * A pack writes its files to the same `filesDir` paths the bundled art uses and tags every
 * row `pack:<id>`, which [MediaRepository.registerBundledImage] honours by leaving those
 * rows alone — so a pack survives app restarts and can be removed to fall back to core art.
 */
@Singleton
class MediaPackImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaRepository: MediaRepository,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private fun packStateFile(): File =
        File(context.filesDir, "media-packs/installed.json").also { it.parentFile?.mkdirs() }

    suspend fun installed(): List<InstalledMediaPack> = withContext(Dispatchers.IO) {
        val file = packStateFile()
        if (!file.isFile) return@withContext emptyList()
        runCatching {
            json.decodeFromString<List<InstalledMediaPack>>(file.readText())
        }.getOrDefault(emptyList())
    }

    /** Cheap check so a generic zip import can hand off to this importer. */
    suspend fun looksLikePack(file: File): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            ZipFile(file).use { zip -> zip.getEntry(MANIFEST_ENTRY) != null }
        }.getOrDefault(false)
    }

    fun looksLikePack(bytes: ByteArray): Boolean = runCatching {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name.replace('\\', '/') == MANIFEST_ENTRY) return@runCatching true
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        false
    }.getOrDefault(false)

    suspend fun installFromUri(uri: Uri, onProgress: (Int, Int) -> Unit = { _, _ -> }): MediaPackResult =
        withContext(Dispatchers.IO) {
            val staged = File(context.cacheDir, "media-pack-${System.currentTimeMillis()}.zip")
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    staged.outputStream().use { output -> input.copyTo(output) }
                } ?: error("Could not open the selected file.")
                install(staged, onProgress)
            } finally {
                staged.delete()
            }
        }

    suspend fun installFromBytes(bytes: ByteArray, onProgress: (Int, Int) -> Unit = { _, _ -> }): MediaPackResult =
        withContext(Dispatchers.IO) {
            val staged = File(context.cacheDir, "media-pack-${System.currentTimeMillis()}.zip")
            try {
                staged.writeBytes(bytes)
                install(staged, onProgress)
            } finally {
                staged.delete()
            }
        }

    /**
     * Extracts [packFile] into app storage and registers every item in the media library.
     *
     * Uses [ZipFile] rather than a streaming read so the manifest can be located before any
     * file is written, which keeps a malformed pack from leaving loose files behind.
     */
    private suspend fun install(packFile: File, onProgress: (Int, Int) -> Unit): MediaPackResult {
        val filesRoot = context.filesDir.canonicalFile
        ZipFile(packFile).use { zip ->
            val manifestEntry = zip.getEntry(MANIFEST_ENTRY)
                ?: error("Not a Weaverse media pack: $MANIFEST_ENTRY is missing.")
            val manifest = json.decodeFromString<MediaPackManifest>(
                zip.getInputStream(manifestEntry).use { it.readBytes().decodeToString() },
            )
            require(manifest.schema == 1) { "Unsupported media pack schema ${manifest.schema}." }
            require(manifest.items.isNotEmpty()) { "Media pack ${manifest.id} lists no items." }

            var installed = 0
            var failed = 0
            var byteSize = 0L
            manifest.items.forEachIndexed { index, item ->
                onProgress(index, manifest.items.size)
                val target = File(context.filesDir, item.relativePath)
                // Zip-slip guard: a crafted relativePath must not escape app storage.
                if (!target.canonicalPath.startsWith(filesRoot.path + File.separator)) {
                    failed++
                    return@forEachIndexed
                }
                val entry = zip.getEntry("media/${item.relativePath}")
                if (entry == null) {
                    failed++
                    return@forEachIndexed
                }
                target.parentFile?.mkdirs()
                zip.getInputStream(entry).use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                mediaRepository.upsertImage(
                    id = item.mediaId,
                    relativePath = item.relativePath,
                    file = target,
                    width = item.width,
                    height = item.height,
                    displayName = item.displayName,
                    category = item.category,
                    tags = item.tags.ensurePackTag(manifest.id),
                )
                installed++
                byteSize += target.length()
            }
            onProgress(manifest.items.size, manifest.items.size)
            recordInstalled(
                InstalledMediaPack(
                    id = manifest.id,
                    name = manifest.name.ifBlank { manifest.id },
                    version = manifest.version,
                    itemCount = installed,
                    byteSize = byteSize,
                    installedAt = System.currentTimeMillis(),
                ),
            )
            return MediaPackResult(
                id = manifest.id,
                name = manifest.name.ifBlank { manifest.id },
                version = manifest.version,
                installed = installed,
                failed = failed,
                byteSize = byteSize,
            )
        }
    }

    private fun recordInstalled(pack: InstalledMediaPack) {
        val file = packStateFile()
        val current = runCatching {
            if (file.isFile) json.decodeFromString<List<InstalledMediaPack>>(file.readText()) else emptyList()
        }.getOrDefault(emptyList())
        val merged = current.filterNot { it.id == pack.id } + pack
        file.writeText(json.encodeToString(merged.sortedBy(InstalledMediaPack::name)))
    }

    private fun String.ensurePackTag(packId: String): String {
        val tag = "$MEDIA_PACK_TAG_PREFIX$packId"
        val existing = parseMediaTags(this)
        return if (tag in existing) this else (existing + tag).joinToString(",")
    }

    private companion object {
        const val MANIFEST_ENTRY = "pack.json"
    }
}
