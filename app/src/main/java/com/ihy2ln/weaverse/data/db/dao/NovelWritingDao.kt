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
    @Query("UPDATE books SET styleGuide = :text, updatedAt = :stamp WHERE id = :bookId")
    suspend fun saveStyle(bookId: String, text: String, stamp: Long)
}
