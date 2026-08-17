package com.ihy2ln.weaverse.data.repo

import androidx.room.withTransaction
import com.ihy2ln.weaverse.data.db.AppDatabase
import com.ihy2ln.weaverse.data.db.entity.ChatMessageEntity
import com.ihy2ln.weaverse.data.db.entity.ChatThreadEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** Single source of truth for novel-mode Workshop Chats (threads + messages). */
@Singleton
class ChatRepository @Inject constructor(private val db: AppDatabase) {
    fun observeThreads(scopeId: String): Flow<List<ChatThreadEntity>> = db.chatThreadDao().observeByScope(scopeId)
    fun observeThread(id: String): Flow<ChatThreadEntity?> = db.chatThreadDao().observeById(id)
    suspend fun getThread(id: String): ChatThreadEntity? = db.chatThreadDao().getById(id)
    suspend fun upsertThread(thread: ChatThreadEntity) = db.chatThreadDao().upsert(thread)
    suspend fun deleteThread(thread: ChatThreadEntity) = db.chatThreadDao().delete(thread)

    fun observeMessages(threadId: String): Flow<List<ChatMessageEntity>> = db.chatMessageDao().observeByThread(threadId)
    fun observeMessageCount(threadId: String): Flow<Int> = db.chatMessageDao().observeMessageCount(threadId)
    suspend fun getMessage(id: String): ChatMessageEntity? = db.chatMessageDao().getById(id)

    suspend fun upsertMessage(message: ChatMessageEntity) = db.withTransaction {
        db.chatMessageDao().upsert(message)
        db.chatMessageFtsDao().reindex(message.id, message.plainText)
    }

    suspend fun deleteMessage(message: ChatMessageEntity) = db.withTransaction {
        db.chatMessageDao().delete(message)
        db.chatMessageFtsDao().deleteByEntityId(message.id)
    }
}
