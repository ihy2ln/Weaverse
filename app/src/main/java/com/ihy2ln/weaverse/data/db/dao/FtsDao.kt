package com.ihy2ln.weaverse.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.ihy2ln.weaverse.data.db.entity.ChatMessageFtsEntity
import com.ihy2ln.weaverse.data.db.entity.CodexEntryFtsEntity
import com.ihy2ln.weaverse.data.db.entity.RpMessageFtsEntity
import com.ihy2ln.weaverse.data.db.entity.SceneFtsEntity
import com.ihy2ln.weaverse.data.db.entity.SnippetFtsEntity

@Dao
interface SceneFtsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(row: SceneFtsEntity)

    @Query("DELETE FROM scenes_fts WHERE entityId = :entityId")
    suspend fun deleteByEntityId(entityId: String)

    @Transaction
    suspend fun reindex(entityId: String, plainText: String) {
        deleteByEntityId(entityId)
        insert(SceneFtsEntity(entityId = entityId, plainText = plainText))
    }

    @Query("SELECT entityId FROM scenes_fts WHERE scenes_fts MATCH :query")
    suspend fun search(query: String): List<String>
}

@Dao
interface CodexEntryFtsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(row: CodexEntryFtsEntity)

    @Query("DELETE FROM codex_entries_fts WHERE entityId = :entityId")
    suspend fun deleteByEntityId(entityId: String)

    @Transaction
    suspend fun reindex(entityId: String, plainText: String) {
        deleteByEntityId(entityId)
        insert(CodexEntryFtsEntity(entityId = entityId, plainText = plainText))
    }

    @Query("SELECT entityId FROM codex_entries_fts WHERE codex_entries_fts MATCH :query")
    suspend fun search(query: String): List<String>
}

@Dao
interface ChatMessageFtsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(row: ChatMessageFtsEntity)

    @Query("DELETE FROM chat_messages_fts WHERE entityId = :entityId")
    suspend fun deleteByEntityId(entityId: String)

    @Transaction
    suspend fun reindex(entityId: String, plainText: String) {
        deleteByEntityId(entityId)
        insert(ChatMessageFtsEntity(entityId = entityId, plainText = plainText))
    }

    @Query("SELECT entityId FROM chat_messages_fts WHERE chat_messages_fts MATCH :query")
    suspend fun search(query: String): List<String>
}

@Dao
interface RpMessageFtsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(row: RpMessageFtsEntity)

    @Query("DELETE FROM rp_messages_fts WHERE entityId = :entityId")
    suspend fun deleteByEntityId(entityId: String)

    @Transaction
    suspend fun reindex(entityId: String, plainText: String) {
        deleteByEntityId(entityId)
        insert(RpMessageFtsEntity(entityId = entityId, plainText = plainText))
    }

    @Query("SELECT entityId FROM rp_messages_fts WHERE rp_messages_fts MATCH :query")
    suspend fun search(query: String): List<String>
}

@Dao
interface SnippetFtsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(row: SnippetFtsEntity)

    @Query("DELETE FROM snippets_fts WHERE entityId = :entityId")
    suspend fun deleteByEntityId(entityId: String)

    @Transaction
    suspend fun reindex(entityId: String, body: String) {
        deleteByEntityId(entityId)
        insert(SnippetFtsEntity(entityId = entityId, body = body))
    }

    @Query("SELECT entityId FROM snippets_fts WHERE snippets_fts MATCH :query")
    suspend fun search(query: String): List<String>
}
