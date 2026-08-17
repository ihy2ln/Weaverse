package com.ihy2ln.weaverse.core.media

import android.content.ClipData
import android.content.Context
import android.net.Uri
import com.ihy2ln.weaverse.data.db.entity.MediaEntity
import com.ihy2ln.weaverse.data.db.entity.MediaType
import com.ihy2ln.weaverse.data.db.entity.newId
import com.ihy2ln.weaverse.data.repo.MediaRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Import pipeline shared by every entry point in spec §7 — Photo Picker,
 * camera capture, and clipboard paste all end up as a `content://` [Uri]
 * handed to [importFromUri], so there's exactly one code path that copies
 * into app storage, dedupes by checksum, downscales, and thumbnails.
 */
@Singleton
class MediaImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaRepository: MediaRepository,
) {
    suspend fun importFromUri(uri: Uri): MediaEntity = withContext(Dispatchers.IO) {
        val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
        val checksum = context.contentResolver.openInputStream(uri)?.use(::computeSha256)
            ?: error("Could not open $uri for import")

        mediaRepository.getByChecksum(checksum)?.let { existing -> return@withContext existing }

        val id = newId()
        val type = MimeTypes.mediaTypeFor(mimeType)
        val extension = MimeTypes.extensionFor(mimeType)
        val destFile = MediaPaths.mediaFile(context, id, extension)

        context.contentResolver.openInputStream(uri)?.use { input ->
            destFile.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Could not open $uri for import")

        var width: Int? = null
        var height: Int? = null
        var durationMs: Long? = null
        var thumbnailPath: String? = null

        when (type) {
            MediaType.Image -> {
                ImageDownscaler.downscaleIfNeeded(destFile)
                readImageDimensions(destFile)?.let { (w, h) -> width = w; height = h }
                val thumbFile = MediaPaths.thumbFile(context, id)
                if (Thumbnailer.generateImageThumbnail(destFile, thumbFile)) thumbnailPath = thumbFile.name
            }
            MediaType.Video -> {
                val metadata = VideoMetadataExtractor.extract(destFile)
                width = metadata.width
                height = metadata.height
                durationMs = metadata.durationMs
                val thumbFile = MediaPaths.thumbFile(context, id)
                if (Thumbnailer.generateVideoThumbnail(destFile, thumbFile)) thumbnailPath = thumbFile.name
            }
            MediaType.Audio -> Unit // not a first-class import target per spec §7's insert points
        }

        val entity = MediaEntity(
            id = id,
            type = type,
            relativePath = destFile.name,
            mimeType = mimeType,
            byteSize = destFile.length(),
            width = width,
            height = height,
            durationMs = durationMs,
            thumbnailPath = thumbnailPath,
            checksum = checksum,
        )
        mediaRepository.upsert(entity)
        entity
    }

    /** Most apps put a `content://` URI per item on the clip for an image/video copy. */
    suspend fun importFromClipboard(clipData: ClipData): List<MediaEntity> {
        val uris = (0 until clipData.itemCount).mapNotNull { index -> clipData.getItemAt(index).uri }
        return uris.map { importFromUri(it) }
    }
}
