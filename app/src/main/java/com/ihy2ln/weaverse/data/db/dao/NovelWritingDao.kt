package com.ihy2ln.weaverse.data.db.dao

import androidx.room.*
import com.ihy2ln.weaverse.data.db.entities.*

@Dao
interface NovelWritingDao {
    @Query("SELECT * FROM novel_prompt_drafts WHERE sceneId = :sceneId")
    suspend fun draft(sceneId: String): NovelPromptDraft?
    @Upsert suspend fun saveDraft(draft: NovelPromptDraft)
    @Query("SELECT * FROM novel_writing_settings WHERE bookId = :bookId")
    suspend fun settings(bookId: String): NovelWritingSettings?
    @Upsert suspend fun saveSettings(settings: NovelWritingSettings)
    /** Creates the book's settings row with defaults; a no-op when it already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun ensureSettings(settings: NovelWritingSettings)
    // Single-column writes, so concurrent saves of different fields never overwrite
    // each other the way a read-modify-write of the whole row would.
    @Query("UPDATE novel_writing_settings SET startProgress = :json WHERE bookId = :bookId")
    suspend fun setStartProgress(bookId: String, json: String)
    @Query("UPDATE novel_writing_settings SET companions = :companions WHERE bookId = :bookId")
    suspend fun setCompanions(bookId: String, companions: String)
    @Query("UPDATE novel_writing_settings SET companions = :companions, memory = :memory WHERE bookId = :bookId")
    suspend fun setCompanionsAndMemory(bookId: String, companions: String, memory: String)
    @Query("UPDATE books SET styleGuide = :text, updatedAt = :stamp WHERE id = :bookId")
    suspend fun saveStyle(bookId: String, text: String, stamp: Long)
}
