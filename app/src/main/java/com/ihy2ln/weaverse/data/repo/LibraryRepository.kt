package com.ihy2ln.weaverse.data.repo

import androidx.room.withTransaction
import com.ihy2ln.weaverse.data.db.AppDatabase
import com.ihy2ln.weaverse.data.db.entity.ActEntity
import com.ihy2ln.weaverse.data.db.entity.BookEntity
import com.ihy2ln.weaverse.data.db.entity.ChapterEntity
import com.ihy2ln.weaverse.data.db.entity.CodexLinkSource
import com.ihy2ln.weaverse.data.db.entity.SceneCodexLinkEntity
import com.ihy2ln.weaverse.data.db.entity.SceneEntity
import com.ihy2ln.weaverse.data.db.entity.SeriesEntity
import com.ihy2ln.weaverse.data.db.entity.SeriesMemberEntity
import com.ihy2ln.weaverse.data.db.entity.SeriesMemberType
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** Single source of truth for Series/Book/Act/Chapter/Scene and their codex links. */
@Singleton
class LibraryRepository @Inject constructor(private val db: AppDatabase) {
    fun observeSeries(): Flow<List<SeriesEntity>> = db.seriesDao().observeAll()
    fun observeSeries(id: String): Flow<SeriesEntity?> = db.seriesDao().observeById(id)
    suspend fun getSeries(id: String): SeriesEntity? = db.seriesDao().getById(id)
    suspend fun upsertSeries(series: SeriesEntity) = db.seriesDao().upsert(series)
    suspend fun deleteSeries(series: SeriesEntity) = db.seriesDao().delete(series)

    fun observeSeriesMembers(seriesId: String): Flow<List<SeriesMemberEntity>> = db.seriesMemberDao().observeForSeries(seriesId)
    suspend fun getSeriesMemberFor(memberType: SeriesMemberType, memberId: String): SeriesMemberEntity? =
        db.seriesMemberDao().getForMember(memberType, memberId)
    suspend fun upsertSeriesMember(member: SeriesMemberEntity) = db.seriesMemberDao().upsert(member)
    suspend fun deleteSeriesMember(member: SeriesMemberEntity) = db.seriesMemberDao().delete(member)

    /** Joins [bookId] to [seriesId] both ways: the book's own direct `seriesId` FK (used for
     * simple "which series is this in" reads) and a [SeriesMemberEntity] row appended at the end
     * of that series' ordering (used by [observeSeriesMembers] and, eventually, `ContextBuilder`'s
     * series section). Passing `seriesId = null` removes the book from whatever series it was in. */
    suspend fun setBookSeries(bookId: String, seriesId: String?) = db.withTransaction {
        val book = db.bookDao().getById(bookId) ?: return@withTransaction
        db.bookDao().upsert(book.copy(seriesId = seriesId))

        val existingMembership = db.seriesMemberDao().getForMember(SeriesMemberType.Book, bookId)
        if (existingMembership != null) {
            db.seriesMemberDao().delete(existingMembership)
        }
        if (seriesId != null) {
            val nextSortOrder = db.seriesMemberDao().countForSeries(seriesId)
            db.seriesMemberDao().upsert(
                SeriesMemberEntity(
                    seriesId = seriesId,
                    memberType = SeriesMemberType.Book,
                    memberId = bookId,
                    sortOrder = nextSortOrder,
                ),
            )
        }
    }

    /** Swaps two members' `sortOrder` — the reorder control for `SeriesScreen` (real drag-and-
     * drop is a known gap across this app; Plan's act/chapter/scene reordering has the same one,
     * see BUILD_NOTES "Phase 10 deviations" — up/down buttons swapping with the adjacent member
     * are the same pragmatic substitute used there). The caller (`SeriesViewModel`) already holds
     * the ordered list from its own StateFlow, so it passes the two neighbors directly rather
     * than this needing to re-derive adjacency itself. */
    suspend fun swapSeriesMemberOrder(a: SeriesMemberEntity, b: SeriesMemberEntity) = db.withTransaction {
        db.seriesMemberDao().upsert(a.copy(sortOrder = b.sortOrder))
        db.seriesMemberDao().upsert(b.copy(sortOrder = a.sortOrder))
    }

    fun observeBooks(): Flow<List<BookEntity>> = db.bookDao().observeAll()
    fun observeBook(id: String): Flow<BookEntity?> = db.bookDao().observeById(id)
    suspend fun getBook(id: String): BookEntity? = db.bookDao().getById(id)
    suspend fun upsertBook(book: BookEntity) = db.bookDao().upsert(book)
    suspend fun deleteBook(book: BookEntity) = db.bookDao().delete(book)

    fun observeActs(bookId: String): Flow<List<ActEntity>> = db.actDao().observeByBook(bookId)
    suspend fun upsertAct(act: ActEntity) = db.actDao().upsert(act)
    suspend fun reorderActs(acts: List<ActEntity>) = db.actDao().update(acts)
    suspend fun deleteAct(act: ActEntity) = db.actDao().delete(act)

    fun observeChapters(actId: String): Flow<List<ChapterEntity>> = db.chapterDao().observeByAct(actId)
    suspend fun upsertChapter(chapter: ChapterEntity) = db.chapterDao().upsert(chapter)
    suspend fun reorderChapters(chapters: List<ChapterEntity>) = db.chapterDao().update(chapters)
    suspend fun deleteChapter(chapter: ChapterEntity) = db.chapterDao().delete(chapter)

    fun observeScenes(chapterId: String): Flow<List<SceneEntity>> = db.sceneDao().observeByChapter(chapterId)
    fun observeScenesForBook(bookId: String): Flow<List<SceneEntity>> = db.sceneDao().observeByBook(bookId)
    fun observeScene(id: String): Flow<SceneEntity?> = db.sceneDao().observeById(id)
    suspend fun getScene(id: String): SceneEntity? = db.sceneDao().getById(id)
    fun observeChapterWordCount(chapterId: String): Flow<Int> = db.sceneDao().observeChapterWordCount(chapterId)
    fun observeBookWordCount(bookId: String): Flow<Int> = db.sceneDao().observeBookWordCount(bookId)

    suspend fun upsertScene(scene: SceneEntity) = db.withTransaction {
        db.sceneDao().upsert(scene)
        db.sceneFtsDao().reindex(scene.id, scene.plainText)
    }

    suspend fun reorderScenes(scenes: List<SceneEntity>) = db.sceneDao().update(scenes)

    suspend fun deleteScene(scene: SceneEntity) = db.withTransaction {
        db.sceneDao().delete(scene)
        db.sceneFtsDao().deleteByEntityId(scene.id)
    }

    fun observeCodexEntriesForScene(sceneId: String): Flow<List<String>> =
        db.sceneCodexLinkDao().observeEntryIdsForScene(sceneId)

    fun observeScenesForCodexEntry(entryId: String): Flow<List<String>> =
        db.sceneCodexLinkDao().observeSceneIdsForEntry(entryId)

    suspend fun linkSceneToCodexEntry(sceneId: String, entryId: String, source: CodexLinkSource) =
        db.sceneCodexLinkDao().link(SceneCodexLinkEntity(sceneId, entryId, source))

    suspend fun unlinkSceneFromCodexEntry(sceneId: String, entryId: String) =
        db.sceneCodexLinkDao().unlink(sceneId, entryId)
}
