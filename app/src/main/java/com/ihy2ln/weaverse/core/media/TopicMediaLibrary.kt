package com.ihy2ln.weaverse.core.media

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.ihy2ln.weaverse.data.db.entities.MediaEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class TopicMediaSnapshot(
    val root: String = "",
    val topics: List<String> = emptyList(),
) {
    val isReady: Boolean get() = root.isNotBlank() && topics.isNotEmpty()

    fun promptDirective(): String? = topics.takeIf { isReady }?.joinToString(", ")?.let { inventory ->
        "A local image/video library is available for these exact topics: $inventory. " +
            "When the visible response substantially brings one of those topics into focus and a visual would help, " +
            "append a private marker after the visible response: [[MEDIA|topic=exact topic|kind=any]]. " +
            "Use kind=image or kind=video only when that format matters. Use at most two markers. " +
            "Never expose or explain the marker, filenames, or the device's library path."
    }
}

data class TopicMediaAttachment(
    val topic: String,
    val fileName: String,
    val media: MediaEntity,
)

private data class LibraryFolder(
    val name: String,
    val file: File? = null,
    val documentId: String? = null,
)

private data class LibraryAsset(
    val name: String,
    val mimeType: String,
    val file: File? = null,
    val uri: Uri? = null,
)

/** Resolves AI topic requests locally. The model never receives the root path or filenames. */
@Singleton
class TopicMediaLibrary @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaRepository: MediaRepository,
) {
    suspend fun snapshot(root: String): TopicMediaSnapshot = withContext(Dispatchers.IO) {
        val cleanRoot = root.trim()
        if (cleanRoot.isBlank()) return@withContext TopicMediaSnapshot()
        TopicMediaSnapshot(
            root = cleanRoot,
            topics = folders(cleanRoot)
                .map { it.name.trim() }
                .filter { sanitizeTopicMediaName(it).isNotBlank() }
                .distinctBy(::normalizedTopicMediaName)
                .sortedBy(String::lowercase)
                .take(60),
        )
    }

    suspend fun importRequested(
        snapshot: TopicMediaSnapshot,
        requests: List<TopicMediaRequest>,
    ): List<TopicMediaAttachment> = withContext(Dispatchers.IO) {
        if (!snapshot.isReady) return@withContext emptyList()
        val available = folders(snapshot.root)
        requests.take(2).mapNotNull { request ->
            val folder = available.firstOrNull {
                normalizedTopicMediaName(it.name) == normalizedTopicMediaName(request.topic)
            } ?: return@mapNotNull null
            val candidates = assets(snapshot.root, folder).filter { asset ->
                when (request.kind) {
                    "image" -> asset.mimeType.startsWith("image/")
                    "video" -> asset.mimeType.startsWith("video/")
                    else -> asset.mimeType.startsWith("image/") || asset.mimeType.startsWith("video/")
                }
            }
            if (candidates.isEmpty()) return@mapNotNull null
            val selected = candidates.random()
            val media = selected.file?.let { mediaRepository.importFromFile(it, selected.mimeType) }
                ?: selected.uri?.let { mediaRepository.importFromUri(it) }
                ?: return@mapNotNull null
            TopicMediaAttachment(folder.name, selected.name, media)
        }
    }

    private fun folders(root: String): List<LibraryFolder> = if (root.startsWith("content://")) {
        documentChildren(Uri.parse(root), DocumentsContract.getTreeDocumentId(Uri.parse(root)))
            .filter { it.mimeType == DocumentsContract.Document.MIME_TYPE_DIR }
            .map { LibraryFolder(name = it.name, documentId = it.documentId) }
    } else {
        val directory = File(root)
        if (!directory.isDirectory) emptyList()
        else directory.listFiles().orEmpty().filter(File::isDirectory).map { LibraryFolder(it.name, file = it) }
    }

    private fun assets(root: String, folder: LibraryFolder): List<LibraryAsset> = when {
        folder.file != null -> folder.file.listFiles().orEmpty()
            .filter(File::isFile)
            .mapNotNull { file ->
                mimeFor(file.name)?.let { mime -> LibraryAsset(file.name, mime, file = file) }
            }
        folder.documentId != null -> {
            val tree = Uri.parse(root)
            documentChildren(tree, folder.documentId).mapNotNull { child ->
                if (child.mimeType == DocumentsContract.Document.MIME_TYPE_DIR) null
                else supportedMime(child.name, child.mimeType)?.let { mime ->
                    LibraryAsset(
                        name = child.name,
                        mimeType = mime,
                        uri = DocumentsContract.buildDocumentUriUsingTree(tree, child.documentId),
                    )
                }
            }
        }
        else -> emptyList()
    }

    private data class DocumentChild(
        val documentId: String,
        val name: String,
        val mimeType: String,
    )

    private fun documentChildren(treeUri: Uri, parentDocumentId: String): List<DocumentChild> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        return runCatching {
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(projection[0])
                val nameIndex = cursor.getColumnIndexOrThrow(projection[1])
                val mimeIndex = cursor.getColumnIndexOrThrow(projection[2])
                buildList {
                    while (cursor.moveToNext()) {
                        add(DocumentChild(cursor.getString(idIndex), cursor.getString(nameIndex), cursor.getString(mimeIndex)))
                    }
                }
            }.orEmpty()
        }.getOrDefault(emptyList())
    }

    private fun supportedMime(name: String, providerMime: String): String? = when {
        providerMime.startsWith("image/") || providerMime.startsWith("video/") -> providerMime
        else -> mimeFor(name)
    }

    private fun mimeFor(name: String): String? = when (name.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "bmp" -> "image/bmp"
        "heic", "heif" -> "image/heic"
        "mp4", "m4v" -> "video/mp4"
        "webm" -> "video/webm"
        "mov" -> "video/quicktime"
        "mkv" -> "video/x-matroska"
        else -> null
    }
}
