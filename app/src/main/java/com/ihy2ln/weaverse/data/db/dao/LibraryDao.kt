package com.ihy2ln.weaverse.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ihy2ln.weaverse.data.db.entity.ActEntity
import com.ihy2ln.weaverse.data.db.entity.BookEntity
import com.ihy2ln.weaverse.data.db.entity.ChapterEntity
import com.ihy2ln.weaverse.data.db.entity.SceneCodexLinkEntity
import com.ihy2ln.weaverse.data.db.entity.SceneEntity
import com.ihy2ln.weaverse.data.db.entity.SeriesEntity
import com.ihy2ln.weaverse.data.db.entity.SeriesMemberEntity
import com.ihy2ln.weaverse.data.db.entity.SeriesMemberType
import kotlinx.coroutines.flow.Flow

@Dao
interface SeriesDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(series: SeriesEntity)

    @Delete
    suspend fun delete(series: SeriesEntity)

    @Query("SELECT * FROM series ORDER BY title")
    fun observeAll(): Flow<List<SeriesEntity>>

    @Query("SELECT * FROM series WHERE id = :id")
    fun observeById(id: String): Flow<SeriesEntity?>

    @Query("SELECT * FROM series WHERE id = :id")
    suspend fun getById(id: String): SeriesEntity?
}

@Dao
interface SeriesMemberDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(member: SeriesMemberEntity)

    @Delete
    suspend fun delete(member: SeriesMemberEntity)

    @Query("SELECT * FROM series_members WHERE seriesId = :seriesId ORDER BY sortOrder")
    fun observeForSeries(seriesId: String): Flow<List<SeriesMemberEntity>>

    @Query("SELECT COUNT(*) FROM series_members WHERE seriesId = :seriesId")
    suspend fun countForSeries(seriesId: String): Int

    @Query("SELECT * FROM series_members WHERE memberType = :memberType AND memberId = :memberId LIMIT 1")
    suspend fun getForMember(memberType: SeriesMemberType, memberId: String): SeriesMemberEntity?
}

@Dao
interface BookDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(book: BookEntity)

    @Delete
    suspend fun delete(book: BookEntity)

    @Query("SELECT * FROM books ORDER BY title")
    fun observeAll(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE seriesId = :seriesId ORDER BY title")
    fun observeBySeries(seriesId: String): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getById(id: String): BookEntity?

    @Query("SELECT * FROM books WHERE id = :id")
    fun observeById(id: String): Flow<BookEntity?>
}

@Dao
interface ActDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(act: ActEntity)

    @Update
    suspend fun update(acts: List<ActEntity>)

    @Delete
    suspend fun delete(act: ActEntity)

    @Query("SELECT * FROM acts WHERE bookId = :bookId ORDER BY sortOrder")
    fun observeByBook(bookId: String): Flow<List<ActEntity>>
}

@Dao
interface ChapterDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(chapter: ChapterEntity)

    @Update
    suspend fun update(chapters: List<ChapterEntity>)

    @Delete
    suspend fun delete(chapter: ChapterEntity)

    @Query("SELECT * FROM chapters WHERE actId = :actId ORDER BY sortOrder")
    fun observeByAct(actId: String): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapters WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<ChapterEntity>
}

@Dao
interface SceneDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(scene: SceneEntity)

    @Update
    suspend fun update(scenes: List<SceneEntity>)

    @Delete
    suspend fun delete(scene: SceneEntity)

    @Query("SELECT * FROM scenes WHERE chapterId = :chapterId ORDER BY sortOrder")
    fun observeByChapter(chapterId: String): Flow<List<SceneEntity>>

    @Query("SELECT * FROM scenes WHERE id = :id")
    suspend fun getById(id: String): SceneEntity?

    @Query("SELECT * FROM scenes WHERE id = :id")
    fun observeById(id: String): Flow<SceneEntity?>

    @Query("SELECT * FROM scenes WHERE chapterId IN (SELECT id FROM chapters WHERE actId IN (SELECT id FROM acts WHERE bookId = :bookId)) ORDER BY sortOrder")
    fun observeByBook(bookId: String): Flow<List<SceneEntity>>

    @Query("SELECT COUNT(*) FROM scenes")
    suspend fun count(): Int

    @Query("SELECT COALESCE(SUM(wordCount), 0) FROM scenes WHERE chapterId = :chapterId")
    fun observeChapterWordCount(chapterId: String): Flow<Int>

    @Query(
        "SELECT COALESCE(SUM(wordCount), 0) FROM scenes WHERE chapterId IN " +
            "(SELECT id FROM chapters WHERE actId IN (SELECT id FROM acts WHERE bookId = :bookId))",
    )
    fun observeBookWordCount(bookId: String): Flow<Int>
}

@Dao
interface SceneCodexLinkDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun link(link: SceneCodexLinkEntity)

    @Query("DELETE FROM scene_codex_links WHERE sceneId = :sceneId AND entryId = :entryId")
    suspend fun unlink(sceneId: String, entryId: String)

    @Query("SELECT entryId FROM scene_codex_links WHERE sceneId = :sceneId")
    fun observeEntryIdsForScene(sceneId: String): Flow<List<String>>

    @Query("SELECT sceneId FROM scene_codex_links WHERE entryId = :entryId")
    fun observeSceneIdsForEntry(entryId: String): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM scene_codex_links WHERE entryId = :entryId")
    suspend fun sceneCountForEntry(entryId: String): Int
}
