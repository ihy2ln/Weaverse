package com.ihy2ln.weaverse.data.repo

import androidx.room.withTransaction
import com.ihy2ln.weaverse.data.db.AppDatabase
import com.ihy2ln.weaverse.data.db.entity.RpCharacterEntity
import com.ihy2ln.weaverse.data.db.entity.RpChatEntity
import com.ihy2ln.weaverse.data.db.entity.RpExpressionEntity
import com.ihy2ln.weaverse.data.db.entity.RpGroupEntity
import com.ihy2ln.weaverse.data.db.entity.RpGroupMemberEntity
import com.ihy2ln.weaverse.data.db.entity.RpMessageEntity
import com.ihy2ln.weaverse.data.db.entity.RpPersonaEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** Single source of truth for roleplay characters, personas, groups, chats, and messages. */
@Singleton
class RoleplayRepository @Inject constructor(private val db: AppDatabase) {
    fun observeCharacters(): Flow<List<RpCharacterEntity>> = db.rpCharacterDao().observeAll()
    fun observeCharacter(id: String): Flow<RpCharacterEntity?> = db.rpCharacterDao().observeById(id)
    suspend fun getCharacter(id: String): RpCharacterEntity? = db.rpCharacterDao().getById(id)
    suspend fun upsertCharacter(character: RpCharacterEntity) = db.rpCharacterDao().upsert(character)
    suspend fun deleteCharacter(character: RpCharacterEntity) = db.rpCharacterDao().delete(character)

    fun observePersonas(): Flow<List<RpPersonaEntity>> = db.rpPersonaDao().observeAll()
    suspend fun getDefaultPersona(): RpPersonaEntity? = db.rpPersonaDao().getDefault()
    suspend fun upsertPersona(persona: RpPersonaEntity) = db.rpPersonaDao().upsert(persona)
    suspend fun deletePersona(persona: RpPersonaEntity) = db.rpPersonaDao().delete(persona)

    fun observeGroups(): Flow<List<RpGroupEntity>> = db.rpGroupDao().observeAll()
    suspend fun upsertGroup(group: RpGroupEntity) = db.rpGroupDao().upsert(group)
    suspend fun deleteGroup(group: RpGroupEntity) = db.rpGroupDao().delete(group)

    fun observeGroupMembers(groupId: String): Flow<List<RpGroupMemberEntity>> = db.rpGroupMemberDao().observeByGroup(groupId)
    suspend fun upsertGroupMember(member: RpGroupMemberEntity) = db.rpGroupMemberDao().upsert(member)
    suspend fun removeGroupMember(member: RpGroupMemberEntity) = db.rpGroupMemberDao().delete(member)

    fun observeChats(): Flow<List<RpChatEntity>> = db.rpChatDao().observeAll()
    fun observeChat(id: String): Flow<RpChatEntity?> = db.rpChatDao().observeById(id)
    suspend fun getChat(id: String): RpChatEntity? = db.rpChatDao().getById(id)
    fun observeChatsForCharacter(characterId: String): Flow<List<RpChatEntity>> = db.rpChatDao().observeByCharacter(characterId)
    suspend fun upsertChat(chat: RpChatEntity) = db.rpChatDao().upsert(chat)
    suspend fun deleteChat(chat: RpChatEntity) = db.rpChatDao().delete(chat)

    fun observeActiveMessages(chatId: String): Flow<List<RpMessageEntity>> = db.rpMessageDao().observeActiveByChat(chatId)
    fun observeSwipeGroup(swipeGroupId: String): Flow<List<RpMessageEntity>> = db.rpMessageDao().observeSwipeGroup(swipeGroupId)
    suspend fun getMessage(id: String): RpMessageEntity? = db.rpMessageDao().getById(id)

    suspend fun upsertMessage(message: RpMessageEntity) = db.withTransaction {
        db.rpMessageDao().upsert(message)
        db.rpMessageFtsDao().reindex(message.id, message.plainText)
    }

    suspend fun deleteMessage(message: RpMessageEntity) = db.withTransaction {
        db.rpMessageDao().delete(message)
        db.rpMessageFtsDao().deleteByEntityId(message.id)
    }

    /** Cycles a swipe group to show [activeId] and deactivates its siblings — spec §10 message swipes. */
    suspend fun activateSwipe(swipeGroupId: String, activeId: String) = db.withTransaction {
        db.rpMessageDao().deactivateSwipeGroup(swipeGroupId)
        db.rpMessageDao().activateSwipe(activeId)
    }

    fun observeExpressions(characterId: String): Flow<List<RpExpressionEntity>> = db.rpExpressionDao().observeByCharacter(characterId)
    suspend fun upsertExpression(expression: RpExpressionEntity) = db.rpExpressionDao().upsert(expression)
    suspend fun deleteExpression(expression: RpExpressionEntity) = db.rpExpressionDao().delete(expression)
}
