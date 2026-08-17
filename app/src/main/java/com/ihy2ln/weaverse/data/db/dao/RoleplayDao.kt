package com.ihy2ln.weaverse.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ihy2ln.weaverse.data.db.entity.RpCharacterEntity
import com.ihy2ln.weaverse.data.db.entity.RpChatEntity
import com.ihy2ln.weaverse.data.db.entity.RpExpressionEntity
import com.ihy2ln.weaverse.data.db.entity.RpGroupEntity
import com.ihy2ln.weaverse.data.db.entity.RpGroupMemberEntity
import com.ihy2ln.weaverse.data.db.entity.RpMessageEntity
import com.ihy2ln.weaverse.data.db.entity.RpPersonaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RpCharacterDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(character: RpCharacterEntity)

    @Delete
    suspend fun delete(character: RpCharacterEntity)

    @Query("SELECT * FROM rp_characters WHERE id = :id")
    suspend fun getById(id: String): RpCharacterEntity?

    @Query("SELECT * FROM rp_characters WHERE id = :id")
    fun observeById(id: String): Flow<RpCharacterEntity?>

    @Query("SELECT * FROM rp_characters ORDER BY name")
    fun observeAll(): Flow<List<RpCharacterEntity>>
}

@Dao
interface RpPersonaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(persona: RpPersonaEntity)

    @Delete
    suspend fun delete(persona: RpPersonaEntity)

    @Query("SELECT * FROM rp_personas ORDER BY name")
    fun observeAll(): Flow<List<RpPersonaEntity>>

    @Query("SELECT * FROM rp_personas WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefault(): RpPersonaEntity?
}

@Dao
interface RpGroupDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(group: RpGroupEntity)

    @Delete
    suspend fun delete(group: RpGroupEntity)

    @Query("SELECT * FROM rp_groups ORDER BY name")
    fun observeAll(): Flow<List<RpGroupEntity>>
}

@Dao
interface RpGroupMemberDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(member: RpGroupMemberEntity)

    @Delete
    suspend fun delete(member: RpGroupMemberEntity)

    @Query("SELECT * FROM rp_group_members WHERE groupId = :groupId ORDER BY sortOrder")
    fun observeByGroup(groupId: String): Flow<List<RpGroupMemberEntity>>
}

@Dao
interface RpChatDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(chat: RpChatEntity)

    @Delete
    suspend fun delete(chat: RpChatEntity)

    @Query("SELECT * FROM rp_chats WHERE id = :id")
    suspend fun getById(id: String): RpChatEntity?

    @Query("SELECT * FROM rp_chats WHERE id = :id")
    fun observeById(id: String): Flow<RpChatEntity?>

    @Query("SELECT * FROM rp_chats ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<RpChatEntity>>

    @Query("SELECT * FROM rp_chats WHERE characterId = :characterId ORDER BY updatedAt DESC")
    fun observeByCharacter(characterId: String): Flow<List<RpChatEntity>>
}

@Dao
interface RpMessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(message: RpMessageEntity)

    @Delete
    suspend fun delete(message: RpMessageEntity)

    @Query("SELECT * FROM rp_messages WHERE id = :id")
    suspend fun getById(id: String): RpMessageEntity?

    @Query("SELECT * FROM rp_messages WHERE chatId = :chatId AND isActiveSwipe = 1 ORDER BY createdAt")
    fun observeActiveByChat(chatId: String): Flow<List<RpMessageEntity>>

    @Query("SELECT * FROM rp_messages WHERE swipeGroupId = :swipeGroupId ORDER BY swipeIndex")
    fun observeSwipeGroup(swipeGroupId: String): Flow<List<RpMessageEntity>>

    @Query("UPDATE rp_messages SET isActiveSwipe = 0 WHERE swipeGroupId = :swipeGroupId")
    suspend fun deactivateSwipeGroup(swipeGroupId: String)

    @Query("UPDATE rp_messages SET isActiveSwipe = 1 WHERE id = :id")
    suspend fun activateSwipe(id: String)
}

@Dao
interface RpExpressionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(expression: RpExpressionEntity)

    @Delete
    suspend fun delete(expression: RpExpressionEntity)

    @Query("SELECT * FROM rp_expressions WHERE characterId = :characterId")
    fun observeByCharacter(characterId: String): Flow<List<RpExpressionEntity>>
}
