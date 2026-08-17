package com.ihy2ln.weaverse.core.media

import android.content.Context
import android.net.Uri
import com.ihy2ln.weaverse.core.text.MediaKind
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.data.db.entities.MediaEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: WeaverseDatabase,
) {
    private fun ensureMediaDir(): File =
        File(context.filesDir, "media").also { it.mkdirs() }

    fun observeAll(): Flow<List<MediaEntity>> = db.mediaDao().observeAll()

    suspend fun getById(id: String): MediaEntity? = db.mediaDao().getById(id)

    suspend fun importFromUri(uri: Uri): MediaEntity = withContext(Dispatchers.IO) {
        ensureMediaDir()
        // Best-effort persistable grant (OpenDocument); Photo Picker often denies this.
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        val id = UUID.randomUUID().toString()
        val mimeType = context.contentResolver.getType(uri) ?: guessMimeFromUri(uri)
        val isVideo = mimeType.startsWith("video/")
        val isAudio = mimeType.startsWith("audio/") ||
            mimeType == "application/ogg" ||
            uri.toString().lowercase().let { it.endsWith(".mp3") || it.endsWith(".wav") }
        val ext = when {
            mimeType.contains("png") -> "png"
            mimeType.contains("webp") -> "webp"
            mimeType.contains("gif") -> "gif"
            mimeType.contains("mp4") -> "mp4"
            mimeType.contains("webm") -> "webm"
            mimeType.contains("mpeg") || mimeType.contains("mp3") -> "mp3"
            mimeType.contains("wav") || mimeType.contains("x-wav") -> "wav"
            mimeType.contains("ogg") -> "ogg"
            mimeType.contains("aac") || mimeType.contains("m4a") -> "m4a"
            isAudio -> "mp3"
            isVideo -> "mp4"
            else -> "jpg"
        }
        val relativePath = "media/$id.$ext"
        val file = File(context.filesDir, relativePath)
        val input = context.contentResolver.openInputStream(uri)
            ?: error("Could not open selected media")
        input.use { stream ->
            file.outputStream().use { output -> stream.copyTo(output) }
        }
        if (!file.exists() || file.length() == 0L) {
            error("Failed to copy media into app storage")
        }
        val type = when {
            isAudio -> "audio"
            isVideo -> "video"
            else -> "image"
        }
        val entity = MediaEntity(
            id = id,
            type = type,
            relativePath = relativePath,
            mimeType = mimeType,
            byteSize = file.length(),
            thumbnailPath = if (type == "image") relativePath else null,
            createdAt = System.currentTimeMillis(),
        )
        db.mediaDao().upsert(entity)
        entity
    }

    suspend fun importFromUris(uris: List<Uri>): List<MediaEntity> {
        val out = ArrayList<MediaEntity>(uris.size)
        for (uri in uris) {
            out += importFromUri(uri)
        }
        return out
    }

    suspend fun importFromBytes(
        bytes: ByteArray,
        id: String = UUID.randomUUID().toString(),
        fileName: String = "$id.jpg",
        mimeType: String = "image/jpeg",
    ): MediaEntity = withContext(Dispatchers.IO) {
        ensureMediaDir()
        val ext = fileName.substringAfterLast('.', "jpg")
        val relativePath = "media/$id.$ext"
        val file = File(context.filesDir, relativePath)
        file.writeBytes(bytes)
        val entity = MediaEntity(
            id = id,
            type = "image",
            relativePath = relativePath,
            mimeType = mimeType,
            byteSize = file.length(),
            thumbnailPath = relativePath,
            createdAt = System.currentTimeMillis(),
        )
        db.mediaDao().upsert(entity)
        entity
    }

    suspend fun registerPlaceholderImage(
        width: Int = 800,
        height: Int = 600,
    ): MediaEntity = withContext(Dispatchers.IO) {
        ensureMediaDir()
        val id = UUID.randomUUID().toString()
        val fileName = "$id.png"
        val relativePath = "media/$fileName"
        val file = File(context.filesDir, relativePath)
        file.writeBytes(ByteArray(0)) // placeholder until import wired
        val entity = MediaEntity(
            id = id,
            type = "image",
            relativePath = relativePath,
            mimeType = "image/png",
            byteSize = file.length(),
            width = width,
            height = height,
            thumbnailPath = relativePath,
            createdAt = System.currentTimeMillis(),
        )
        db.mediaDao().upsert(entity)
        entity
    }

    fun resolveFile(entity: MediaEntity): File = File(context.filesDir, entity.relativePath)

    companion object {
        fun kindForType(type: String): MediaKind = when (type) {
            "video" -> MediaKind.Video
            "audio" -> MediaKind.Audio
            else -> MediaKind.Image
        }

        private fun guessMimeFromUri(uri: Uri): String {
            val path = uri.toString().lowercase()
            return when {
                path.endsWith(".mp3") -> "audio/mpeg"
                path.endsWith(".wav") -> "audio/wav"
                path.endsWith(".mp4") -> "video/mp4"
                path.endsWith(".webm") -> "video/webm"
                path.endsWith(".png") -> "image/png"
                path.endsWith(".webp") -> "image/webp"
                path.endsWith(".gif") -> "image/gif"
                else -> "image/jpeg"
            }
        }
    }
}
