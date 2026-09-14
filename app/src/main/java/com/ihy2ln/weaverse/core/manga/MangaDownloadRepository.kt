package com.ihy2ln.weaverse.core.manga

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ihy2ln.weaverse.core.media.MediaRepository
import com.ihy2ln.weaverse.core.text.Document
import com.ihy2ln.weaverse.core.text.MediaBlock
import com.ihy2ln.weaverse.core.text.MediaKind
import com.ihy2ln.weaverse.core.text.toJson
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.data.db.entities.MangaChapterEntity
import com.ihy2ln.weaverse.data.db.entities.MangaFavoriteCategoryEntity
import com.ihy2ln.weaverse.data.db.entities.MangaFavoriteEntity
import com.ihy2ln.weaverse.data.db.entities.MangaPageEntity
import com.ihy2ln.weaverse.data.db.entities.MangaSeriesEntity
import com.ihy2ln.weaverse.data.db.entities.MediaEntity
import com.ihy2ln.weaverse.data.db.entities.RpPageMeta
import com.ihy2ln.weaverse.data.db.entities.RpMessageEntity
import com.ihy2ln.weaverse.data.db.entities.decodePages
import com.ihy2ln.weaverse.data.db.entities.encodePages
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

@Singleton
class MangaDownloadRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: WeaverseDatabase,
    private val registry: MangaSourceRegistry,
    private val webLinkImporter: MangaWebLinkImporter,
    private val mangaFileImporter: com.ihy2ln.weaverse.core.media.MangaFileImporter,
    private val mediaRepository: MediaRepository,
    private val okHttpClient: OkHttpClient,
) {
    fun observeChapters(): Flow<List<MangaChapterEntity>> = db.mangaDao().observeChapters()

    fun observeCoverPages(): Flow<List<MangaPageEntity>> = db.mangaDao().observeCoverPages()

    fun observePages(chapterId: String): Flow<List<MangaPageEntity>> = db.mangaDao().observePages(chapterId)

    fun observeSeries(): Flow<List<MangaSeriesEntity>> = db.mangaDao().observeSeries()

    fun observeFavoriteCategories(): Flow<List<MangaFavoriteCategoryEntity>> = db.mangaDao().observeFavoriteCategories()

    fun observeFavorites(): Flow<List<MangaFavoriteEntity>> = db.mangaDao().observeFavorites()

    suspend fun search(sourceId: String, query: String): List<MangaSearchResult> =
        registry.get(sourceId)?.search(query).orEmpty()

    suspend fun browse(sourceId: String, mode: MangaBrowseMode): List<MangaSearchResult> =
        registry.get(sourceId)?.browse(mode).orEmpty()

    suspend fun loadChapters(manga: MangaSearchResult): List<MangaChapter> =
        registry.get(manga.sourceId)?.chapters(manga).orEmpty()

    suspend fun loadDetails(manga: MangaSearchResult): MangaSearchResult =
        registry.get(manga.sourceId)?.details(manga) ?: manga

    suspend fun ensureDefaultFavoriteCategory() {
        if (db.mangaDao().favoriteCategoryCount() == 0) {
            db.mangaDao().insertFavoriteCategory(
                MangaFavoriteCategoryEntity("favorites", "Favorites", createdAt = System.currentTimeMillis()),
            )
        }
    }

    suspend fun createFavoriteCategory(name: String) {
        val clean = name.trim()
        require(clean.isNotBlank()) { "Enter a category name." }
        db.mangaDao().insertFavoriteCategory(
            MangaFavoriteCategoryEntity(
                id = "favorite-category-${sha256(clean.lowercase().toByteArray()).take(20)}",
                name = clean,
                sortOrder = db.mangaDao().favoriteCategoryCount(),
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun setFavorite(manga: MangaSearchResult, categoryId: String, favorite: Boolean) {
        val seriesId = seriesId(manga.sourceId, manga.remoteId)
        db.mangaDao().upsertSeries(
            MangaSeriesEntity(
                id = seriesId,
                sourceId = manga.sourceId,
                remoteId = manga.remoteId,
                title = manga.title,
                description = manga.description,
                coverUrl = manga.coverUrl.orEmpty(),
                canonicalUrl = manga.canonicalUrl,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        if (favorite) {
            db.mangaDao().upsertFavorite(MangaFavoriteEntity(seriesId, categoryId, System.currentTimeMillis()))
        } else {
            db.mangaDao().removeFavorite(seriesId, categoryId)
        }
    }

    suspend fun previewWebLink(url: String): WebLinkSnapshot = webLinkImporter.inspect(url)

    suspend fun addDiscoveredChapter(chapter: MangaChapter): MangaChapterEntity {
        val existing = db.mangaDao().getChapterByRemoteId(chapter.sourceId, chapter.remoteId)
        val entity = MangaChapterEntity(
            id = existing?.id ?: "manga-chapter-${chapter.sourceId}-${chapter.remoteId}",
            sourceId = chapter.sourceId,
            remoteId = chapter.remoteId,
            mangaId = chapter.mangaId,
            mangaTitle = chapter.mangaTitle,
            title = chapter.title,
            volume = chapter.volume,
            chapterNumber = chapter.chapterNumber,
            language = chapter.language,
            canonicalUrl = chapter.canonicalUrl,
            readingOrder = chapter.readingOrder,
            pageCount = existing?.pageCount ?: 0,
            status = existing?.status ?: "discovered",
            progress = existing?.progress ?: 0,
            errorMessage = "",
            updatedAt = System.currentTimeMillis(),
            downloadedAt = existing?.downloadedAt,
        )
        db.mangaDao().upsertChapter(entity)
        return entity
    }

    suspend fun enqueue(chapter: MangaChapterEntity) {
        val pages = db.mangaDao().getPages(chapter.id)
        if (pages.isEmpty()) {
            val adapter = registry.get(chapter.sourceId) ?: error("Source is not installed: ${chapter.sourceId}")
            val discovered = adapter.pages(chapter.toModel())
            if (discovered.isEmpty()) error("The source returned no pages for this chapter")
            db.mangaDao().upsertPages(discovered.map { page ->
                MangaPageEntity(
                    id = "manga-page-${chapter.id}-${page.pageIndex}",
                    chapterId = chapter.id,
                    sourceId = page.sourceId,
                    pageIndex = page.pageIndex,
                    remoteUrl = page.remoteUrl,
                    fileName = page.fileName,
                    updatedAt = System.currentTimeMillis(),
                )
            })
            db.mangaDao().upsertChapter(chapter.copy(pageCount = discovered.size))
        }
        db.mangaDao().upsertChapter(
            chapter.copy(
                status = "queued",
                errorMessage = "",
                updatedAt = System.currentTimeMillis(),
            ),
        )
        schedule(chapter.id)
    }

    suspend fun enqueueWebLink(url: String, title: String = ""): MangaChapterEntity {
        val snapshot = previewWebLink(url)
        return enqueueWebSnapshot(snapshot, title)
    }

    suspend fun enqueueWebSnapshot(snapshot: WebLinkSnapshot, title: String = ""): MangaChapterEntity {
        val remoteId = "url-${sha256(snapshot.url.toByteArray()).take(24)}"
        val existing = db.mangaDao().getChapterByRemoteId("weblink", remoteId)
        val chapter = MangaChapterEntity(
            id = existing?.id ?: "manga-chapter-weblink-$remoteId",
            sourceId = "weblink",
            remoteId = remoteId,
            mangaId = snapshot.url,
            mangaTitle = title.trim().ifBlank { snapshot.title },
            title = snapshot.title,
            canonicalUrl = snapshot.url,
            pageCount = snapshot.pages.size,
            status = "queued",
            progress = 0,
            errorMessage = "",
            updatedAt = System.currentTimeMillis(),
            downloadedAt = existing?.downloadedAt,
        )
        db.mangaDao().upsertChapter(chapter)
        db.mangaDao().upsertPages(snapshot.pages.map { page ->
            MangaPageEntity(
                id = "manga-page-${chapter.id}-${page.index}",
                chapterId = chapter.id,
                sourceId = "weblink",
                pageIndex = page.index,
                remoteUrl = page.url,
                fileName = page.fileName,
                updatedAt = System.currentTimeMillis(),
            )
        })
        schedule(chapter.id)
        return chapter
    }

    /** Saves CBZ/ZIP/PDF/image imports into the same local chapter library. */
    suspend fun importLocalFiles(uris: List<android.net.Uri>): List<MangaChapterEntity> = withContext(Dispatchers.IO) {
        uris.mapNotNull { uri ->
            val pages = mutableListOf<MediaEntity>()
            runCatching {
                mangaFileImporter.importPages(uri, onPage = { media, _ -> pages += media })
            }
            if (pages.isEmpty()) return@mapNotNull null
            val name = mangaFileImporter.queryDisplayName(uri)
            val id = "manga-chapter-local-${UUID.randomUUID()}"
            val chapter = MangaChapterEntity(
                id = id,
                sourceId = "local",
                remoteId = id,
                mangaId = id,
                mangaTitle = name.substringBeforeLast('.').ifBlank { "Local import" },
                title = name,
                readingOrder = "ltr",
                pageCount = pages.size,
                status = "completed",
                progress = 100,
                updatedAt = System.currentTimeMillis(),
                downloadedAt = System.currentTimeMillis(),
            )
            db.mangaDao().upsertChapter(chapter)
            db.mangaDao().upsertPages(pages.mapIndexed { index, media ->
                MangaPageEntity(
                    id = "manga-page-$id-$index",
                    chapterId = id,
                    sourceId = "local",
                    pageIndex = index,
                    remoteUrl = "",
                    fileName = name,
                    localPath = media.relativePath,
                    checksum = media.checksum,
                    status = "downloaded",
                    mediaId = media.id,
                    updatedAt = System.currentTimeMillis(),
                )
            })
            chapter
        }
    }

    private fun schedule(chapterId: String) {
        val request = OneTimeWorkRequestBuilder<MangaDownloadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(androidx.work.workDataOf(MangaDownloadWorker.CHAPTER_ID to chapterId))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            workName(chapterId),
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    private fun seriesId(sourceId: String, remoteId: String): String =
        "manga-series-${sourceId}-${sha256(remoteId.toByteArray()).take(24)}"

    suspend fun stop(chapter: MangaChapterEntity) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(chapter.id))
        db.mangaDao().upsertChapter(
            chapter.copy(status = "stopped", errorMessage = "Stopped by user", updatedAt = System.currentTimeMillis()),
        )
    }

    suspend fun retry(chapter: MangaChapterEntity) = enqueue(
        chapter.copy(status = "queued", progress = chapter.progress.coerceAtLeast(0)),
    )

    suspend fun downloadChapter(chapterId: String) = withContext(Dispatchers.IO) {
        val dao = db.mangaDao()
        val chapter = dao.getChapter(chapterId) ?: error("Chapter not found")
        val pages = dao.getPages(chapterId)
        if (pages.isEmpty()) error("No page manifest is available")
        dao.upsertChapter(chapter.copy(status = "downloading", pageCount = pages.size, errorMessage = ""))
        val root = File(context.filesDir, "manga/$chapterId").also { it.mkdirs() }
        var completed = 0
        pages.forEach { page ->
            coroutineContext.ensureActive()
            val existing = page.localPath.takeIf { it.isNotBlank() }?.let { File(context.filesDir, it) }
            if (existing?.isFile == true && existing.length() > 0L) {
                completed++
                dao.upsertPage(page.copy(status = "downloaded", errorMessage = "", updatedAt = System.currentTimeMillis()))
                updateProgress(dao, chapter, completed, pages.size)
                return@forEach
            }
            dao.upsertPage(page.copy(status = "downloading", errorMessage = "", updatedAt = System.currentTimeMillis()))
            val name = safePageName(page.pageIndex, page.fileName)
            val relative = "manga/$chapterId/$name"
            val destination = File(context.filesDir, relative)
            val temporary = File(root, "$name.part")
            val request = Request.Builder()
                .url(page.remoteUrl)
                .header("User-Agent", "Weaverse/1.0")
                .header("Accept", "image/avif,image/webp,image/png,image/jpeg,image/*;q=0.9,*/*;q=0.5")
                .apply {
                    chapter.canonicalUrl.takeIf { it.startsWith("http") }?.let { header("Referer", it) }
                }
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Page ${page.pageIndex + 1} returned HTTP ${response.code}")
                val bytes = response.body?.bytes() ?: error("Page ${page.pageIndex + 1} was empty")
                val contentType = response.header("Content-Type").orEmpty().lowercase()
                if (!contentType.startsWith("image/") && !looksLikeImage(bytes)) {
                    error("Page ${page.pageIndex + 1} did not return an image")
                }
                temporary.writeBytes(bytes)
                if (!temporary.renameTo(destination)) {
                    temporary.copyTo(destination, overwrite = true)
                    temporary.delete()
                }
                val checksum = sha256(destination.readBytes())
                dao.upsertPage(
                    page.copy(
                        localPath = relative,
                        checksum = checksum,
                        status = "downloaded",
                        errorMessage = "",
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
            }
            completed++
            updateProgress(dao, chapter, completed, pages.size)
        }
        dao.upsertChapter(
            chapter.copy(
                status = "completed",
                progress = 100,
                pageCount = pages.size,
                errorMessage = "",
                updatedAt = System.currentTimeMillis(),
                downloadedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun markFailed(chapterId: String, message: String) {
        val chapter = db.mangaDao().getChapter(chapterId) ?: return
        db.mangaDao().upsertChapter(
            chapter.copy(status = "failed", errorMessage = message.take(300), updatedAt = System.currentTimeMillis()),
        )
    }

    suspend fun offlinePageFiles(chapterId: String): List<File> = withContext(Dispatchers.IO) {
        db.mangaDao().getPages(chapterId)
            .sortedBy { it.pageIndex }
            .mapNotNull { page ->
                page.localPath.takeIf { it.isNotBlank() }
                    ?.let { File(context.filesDir, it) }
                    ?.takeIf { it.isFile && it.length() > 0L }
                    ?: page.mediaId?.let { mediaRepository.getById(it) }
                        ?.let(mediaRepository::resolveFile)
                        ?.takeIf { it.isFile && it.length() > 0L }
            }
    }

    /** Imports original downloaded pages into the shared media library without replacing them. */
    suspend fun importChapterPages(chapterId: String): List<MediaEntity> = withContext(Dispatchers.IO) {
        val pages = db.mangaDao().getPages(chapterId)
        pages.mapNotNull { page ->
            val file = page.localPath.takeIf { it.isNotBlank() }?.let { File(context.filesDir, it) }
            if (file == null || !file.isFile || file.length() == 0L) return@mapNotNull null
            val media = page.mediaId?.let { mediaRepository.getById(it) }
                ?: mediaRepository.importFromFile(file, mimeFor(file.name)).also {
                    db.mangaDao().upsertPage(page.copy(mediaId = it.id, updatedAt = System.currentTimeMillis()))
                }
            media
        }
    }

    data class MangaStoryboardImportResult(
        val importedCount: Int,
        val pageIds: List<String>,
    )

    /** MCP/CLI-safe import: one original page becomes one editable full-page panel. */
    suspend fun importChapterToStoryboard(chatId: String, chapterId: String): Int =
        importChapterToStoryboardResult(chatId, chapterId).importedCount

    /**
     * Same import as [importChapterToStoryboard], but also returns the generated page ids.
     * The editor uses these ids to reopen the exact page the reader was editing.
     */
    suspend fun importChapterToStoryboardResult(
        chatId: String,
        chapterId: String,
    ): MangaStoryboardImportResult = withContext(Dispatchers.IO) {
        val chat = db.roleplayDao().getChat(chatId) ?: error("Storyboard chat not found: $chatId")
        val chapter = db.mangaDao().getChapter(chapterId) ?: error("Chapter not found: $chapterId")
        if (chapter.status != "completed") error("Chapter is not fully downloaded")
        val media = importChapterPages(chapterId)
        if (media.isEmpty()) error("No downloaded pages are available")
        val pages = decodePages(chat.pagesJson).toMutableList()
        val importedPageIds = mutableListOf<String>()
        val blocks = media.mapIndexed { index, item ->
            val pageId = "page-${java.util.UUID.randomUUID()}"
            importedPageIds += pageId
            pages += RpPageMeta(
                id = pageId,
                order = (pages.maxOfOrNull { it.order } ?: -1) + 1,
                title = "${chapter.mangaTitle} · ${chapter.title} · Page ${index + 1}",
                templateId = "classic-6",
                readingOrder = chapter.readingOrder,
                generationStatus = "downloaded",
                sourceChapterId = chapterId,
            )
            MediaBlock(
                id = "mb-${java.util.UUID.randomUUID()}",
                mediaId = item.id,
                kind = MediaKind.Image,
                gridCol = 0,
                gridRow = 0,
                gridColSpan = 6,
                gridRowSpan = 6,
                pageId = pageId,
            )
        }
        val now = System.currentTimeMillis()
        db.roleplayDao().upsertChat(chat.copy(pagesJson = encodePages(pages), updatedAt = now))
        db.roleplayDao().upsertMessage(
            RpMessageEntity(
                id = "rpm-$now",
                chatId = chat.id,
                swipeGroupId = "sw-$now",
                swipeIndex = 0,
                isActiveSwipe = true,
                role = "user",
                contentJson = Document(blocks = blocks).toJson(),
                createdAt = now,
                displayMode = "roleplay",
            ),
        )
        MangaStoryboardImportResult(
            importedCount = media.size,
            pageIds = importedPageIds,
        )
    }

    private suspend fun updateProgress(
        dao: com.ihy2ln.weaverse.data.db.dao.MangaDao,
        chapter: MangaChapterEntity,
        completed: Int,
        total: Int,
    ) {
        dao.upsertChapter(
            chapter.copy(
                status = "downloading",
                progress = (completed * 100 / total.coerceAtLeast(1)).coerceIn(0, 99),
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    private fun workName(chapterId: String) = "manga-download-$chapterId"

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    private fun safePageName(index: Int, input: String): String {
        val extension = input.substringAfterLast('.', "jpg").lowercase()
            .filter { it.isLetterOrDigit() }.ifBlank { "jpg" }
        return "%04d.%s".format(index + 1, extension)
    }

    private fun mimeFor(name: String): String = when (name.substringAfterLast('.').lowercase()) {
        "png" -> "image/png"
        "webp" -> "image/webp"
        else -> "image/jpeg"
    }

    private fun looksLikeImage(bytes: ByteArray): Boolean {
        if (bytes.size < 12) return false
        val png = bytes[0] == 0x89.toByte() && bytes.copyOfRange(1, 4).contentEquals(byteArrayOf(0x50, 0x4E, 0x47))
        val jpeg = bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()
        val gif = bytes.copyOfRange(0, 3).contentEquals("GIF".toByteArray())
        val webp = bytes.copyOfRange(0, 4).contentEquals("RIFF".toByteArray()) &&
            bytes.copyOfRange(8, 12).contentEquals("WEBP".toByteArray())
        return png || jpeg || gif || webp
    }

    private fun MangaChapterEntity.toModel() = MangaChapter(
        sourceId = sourceId,
        remoteId = remoteId,
        mangaId = mangaId,
        mangaTitle = mangaTitle,
        title = title,
        volume = volume,
        chapterNumber = chapterNumber,
        language = language,
        canonicalUrl = canonicalUrl,
        readingOrder = readingOrder,
    )
}

class MangaDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
    private val repository: MangaDownloadRepository,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val chapterId = inputData.getString(CHAPTER_ID) ?: return Result.failure()
        return try {
            repository.downloadChapter(chapterId)
            Result.success()
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            repository.markFailed(chapterId, error.message ?: "Download failed")
            Result.failure()
        }
    }

    companion object {
        const val CHAPTER_ID = "chapterId"
    }
}
