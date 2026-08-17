package com.ihy2ln.weaverse.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ihy2ln.weaverse.data.db.entity.ChatMessageEntity
import com.ihy2ln.weaverse.data.db.entity.ChatThreadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatThreadDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(thread: ChatThreadEntity)

    @Delete
    suspend fun delete(thread: ChatThreadEntity)

    @Query("SELECT * FROM chat_threads WHERE id = :id")
    suspend fun getById(id: String): ChatThreadEntity?

    @Query("SELECT * FROM chat_threads WHERE id = :id")
    fun observeById(id: String): Flow<ChatThreadEntity?>

    @Query("SELECT * FROM chat_threads WHERE scopeId = :scopeId ORDER BY pinned DESC, updatedAt DESC")
    fun observeByScope(scopeId: String): Flow<List<ChatThreadEntity>>
}

@Dao
interface ChatMessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(message: ChatMessageEntity)

    @Delete
    suspend fun delete(message: ChatMessageEntity)

    @Query("SELECT * FROM chat_messages WHERE id = :id")
    suspend fun getById(id: String): ChatMessageEntity?

    @Query("SELECT * FROM chat_messages WHERE threadId = :threadId ORDER BY createdAt")
    fun observeByThread(threadId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT COUNT(*) FROM chat_messages WHERE threadId = :threadId")
    fun observeMessageCount(threadId: String): Flow<Int>
}
