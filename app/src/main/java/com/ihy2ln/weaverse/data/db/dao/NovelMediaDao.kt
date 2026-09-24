package com.ihy2ln.weaverse.data.db.dao

import androidx.room.*
import com.ihy2ln.weaverse.data.db.entities.NovelMediaLink
import kotlinx.coroutines.flow.Flow

@Dao
interface NovelMediaDao {
    @Query("UPDATE scenes SET summary = :summary, updatedAt = :updatedAt WHERE id = :sceneId")
    suspend fun updateSceneSummary(sceneId: String, summary: String, updatedAt: Long)
    @Query("UPDATE scenes SET sortOrder = :position, updatedAt = :updatedAt WHERE id = :sceneId")
    suspend fun setSceneOrder(sceneId: String, position: Int, updatedAt: Long)
    @Query("UPDATE scenes SET summary = :summary, pov = :pov, status = :status, updatedAt = :updatedAt WHERE id = :sceneId")
    suspend fun updateSceneMetadata(sceneId: String, summary: String, pov: String, status: String, updatedAt: Long)
    @Query("SELECT l.* FROM scene_codex_links l INNER JOIN scenes s ON s.id = l.sceneId INNER JOIN chapters c ON c.id = s.chapterId INNER JOIN acts a ON a.id = c.actId WHERE a.bookId = :bookId")
    fun observeContextLinks(bookId: String): Flow<List<com.ihy2ln.weaverse.data.db.entities.SceneCodexLinkEntity>>
    @Query("SELECT entryId FROM scene_codex_links WHERE sceneId = :sceneId")
    suspend fun contextIds(sceneId: String): List<String>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun pin(link: com.ihy2ln.weaverse.data.db.entities.SceneCodexLinkEntity)
    @Query("DELETE FROM scene_codex_links WHERE sceneId = :sceneId AND entryId = :entryId")
    suspend fun unpin(sceneId: String, entryId: String)
    @Query("SELECT * FROM novel_media_links WHERE bookId = :bookId ORDER BY createdAt DESC")
    fun observe(bookId: String): Flow<List<NovelMediaLink>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(link: NovelMediaLink)
    @Query("DELETE FROM novel_media_links WHERE id = :id AND bookId = :bookId")
    suspend fun remove(id: String, bookId: String)
}
