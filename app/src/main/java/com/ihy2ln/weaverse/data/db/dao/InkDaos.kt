package com.ihy2ln.weaverse.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ihy2ln.weaverse.data.db.entities.ActEntity
import com.ihy2ln.weaverse.data.db.entities.BookEntity
import com.ihy2ln.weaverse.data.db.entities.ChapterEntity
import com.ihy2ln.weaverse.data.db.entities.ChatMessageEntity
import com.ihy2ln.weaverse.data.db.entities.ChatThreadEntity
import com.ihy2ln.weaverse.data.db.entities.CodexCategoryEntity
import com.ihy2ln.weaverse.data.db.entities.CodexEntryEntity
import com.ihy2ln.weaverse.data.db.entities.CodexEntryLoreEntity
import com.ihy2ln.weaverse.data.db.entities.CodexRelationshipEntity
import com.ihy2ln.weaverse.data.db.entities.MediaEntity
import com.ihy2ln.weaverse.data.db.entities.PromptEntity
import com.ihy2ln.weaverse.data.db.entities.PromptFolderEntity
import com.ihy2ln.weaverse.data.db.entities.RpCharacterEntity
import com.ihy2ln.weaverse.data.db.entities.RpChatEntity
import com.ihy2ln.weaverse.data.db.entities.RpMessageEntity
import com.ihy2ln.weaverse.data.db.entities.RpPersonaEntity
import com.ihy2ln.weaverse.data.db.entities.SceneEntity
import com.ihy2ln.weaverse.data.db.entities.SceneSnapshotEntity
import com.ihy2ln.weaverse.data.db.entities.SeriesEntity
import com.ihy2ln.weaverse.data.db.entities.SnippetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SeriesDao {
    @Query("SELECT * FROM series ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<SeriesEntity>>

    @Query("SELECT * FROM series WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<SeriesEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SeriesEntity)

    @Query("DELETE FROM series WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface BookDao {
    @Query("SELECT COUNT(*) FROM books")
    suspend fun count(): Int

    @Query("SELECT * FROM books ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books ORDER BY updatedAt DESC")
    suspend fun getAll(): List<BookEntity>

    @Query("SELECT * FROM books WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<BookEntity?>

    @Query("SELECT * FROM books WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): BookEntity?

    @Query("SELECT * FROM books WHERE seriesId = :seriesId ORDER BY updatedAt DESC")
    fun observeBySeries(seriesId: String): Flow<List<BookEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: BookEntity)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface ManuscriptDao {
    @Query("SELECT * FROM acts WHERE bookId = :bookId ORDER BY sortOrder")
    fun observeActs(bookId: String): Flow<List<ActEntity>>

    @Query("SELECT * FROM acts WHERE bookId = :bookId ORDER BY sortOrder")
    suspend fun getActs(bookId: String): List<ActEntity>

    @Query("SELECT * FROM chapters WHERE actId = :actId ORDER BY sortOrder")
    fun observeChapters(actId: String): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapters WHERE actId = :actId ORDER BY sortOrder")
    suspend fun getChapters(actId: String): List<ChapterEntity>

    @Query("SELECT * FROM scenes WHERE chapterId = :chapterId ORDER BY sortOrder")
    fun observeScenes(chapterId: String): Flow<List<SceneEntity>>

    @Query("SELECT * FROM scenes WHERE chapterId = :chapterId ORDER BY sortOrder")
    suspend fun getScenes(chapterId: String): List<SceneEntity>

    @Query("SELECT * FROM scenes WHERE id = :id LIMIT 1")
    fun observeScene(id: String): Flow<SceneEntity?>

    @Query("SELECT * FROM scenes WHERE id = :id LIMIT 1")
    suspend fun getScene(id: String): SceneEntity?

    @Query("SELECT * FROM chapters WHERE id = :id LIMIT 1")
    suspend fun getChapter(id: String): ChapterEntity?

    @Query("SELECT * FROM scene_snapshots WHERE sceneId = :sceneId ORDER BY createdAt DESC")
    fun observeSnapshots(sceneId: String): Flow<List<SceneSnapshotEntity>>

    @Query("SELECT * FROM scene_snapshots WHERE id = :id LIMIT 1")
    suspend fun getSnapshot(id: String): SceneSnapshotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSnapshot(entity: SceneSnapshotEntity)

    @Query("DELETE FROM scene_snapshots WHERE id = :id")
    suspend fun deleteSnapshot(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAct(entity: ActEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChapter(entity: ChapterEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertScene(entity: SceneEntity)

    @Query("DELETE FROM scenes WHERE id = :id")
    suspend fun deleteScene(id: String)

    @Query("DELETE FROM chapters WHERE id = :id")
    suspend fun deleteChapter(id: String)
}

@Dao
interface CodexDao {
    @Query("SELECT * FROM codex_categories WHERE scopeId = :scopeId ORDER BY sortOrder")
    fun observeCategories(scopeId: String): Flow<List<CodexCategoryEntity>>

    @Query("SELECT * FROM codex_categories WHERE scopeId = :scopeId ORDER BY sortOrder")
    suspend fun getCategories(scopeId: String): List<CodexCategoryEntity>

    @Query("SELECT * FROM codex_entries WHERE scopeId = :scopeId ORDER BY name")
    fun observeEntries(scopeId: String): Flow<List<CodexEntryEntity>>

    @Query("SELECT * FROM codex_entries WHERE scopeId = :scopeId ORDER BY name")
    suspend fun getEntries(scopeId: String): List<CodexEntryEntity>

    @Query("SELECT * FROM codex_categories ORDER BY sortOrder, name")
    fun observeAllCategories(): Flow<List<CodexCategoryEntity>>

    @Query("SELECT * FROM codex_entries WHERE disabled = 0 ORDER BY name")
    fun observeAllEntries(): Flow<List<CodexEntryEntity>>

    @Query("SELECT * FROM codex_categories ORDER BY sortOrder, name")
    suspend fun getAllCategories(): List<CodexCategoryEntity>

    @Query("SELECT * FROM codex_entries ORDER BY name")
    suspend fun getAllEntries(): List<CodexEntryEntity>

    @Query("DELETE FROM codex_categories WHERE id = :id")
    suspend fun deleteCategory(id: String)

    @Query("SELECT * FROM codex_entries WHERE id = :id LIMIT 1")
    fun observeEntry(id: String): Flow<CodexEntryEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategory(entity: CodexCategoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEntry(entity: CodexEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLore(entity: CodexEntryLoreEntity)

    @Query("DELETE FROM codex_entries WHERE id = :id")
    suspend fun deleteEntry(id: String)

    @Query("DELETE FROM codex_entries_lore WHERE entryId = :entryId")
    suspend fun deleteLore(entryId: String)

    @Query("DELETE FROM codex_entries WHERE scopeId = :scopeId")
    suspend fun deleteEntriesForScope(scopeId: String)

    @Query("DELETE FROM codex_categories WHERE scopeId = :scopeId")
    suspend fun deleteCategoriesForScope(scopeId: String)

    @Query("SELECT * FROM codex_relationships WHERE fromEntryId = :entryId OR toEntryId = :entryId")
    fun observeRelationships(entryId: String): Flow<List<CodexRelationshipEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRelationship(entity: CodexRelationshipEntity)

    @Query("DELETE FROM codex_relationships WHERE id = :id")
    suspend fun deleteRelationship(id: String)
}

@Dao
interface PromptDao {
    @Query("SELECT COUNT(*) FROM prompts")
    suspend fun count(): Int

    @Query("SELECT * FROM prompt_folders ORDER BY name")
    fun observeFolders(): Flow<List<PromptFolderEntity>>

    @Query("SELECT * FROM prompt_folders ORDER BY name")
    suspend fun getFolders(): List<PromptFolderEntity>

    @Query("SELECT * FROM prompts ORDER BY name")
    fun observeAll(): Flow<List<PromptEntity>>

    @Query("SELECT * FROM prompts ORDER BY name")
    suspend fun getAll(): List<PromptEntity>

    @Query("SELECT * FROM prompts WHERE folderId = :folderId ORDER BY name")
    fun observeByFolder(folderId: String): Flow<List<PromptEntity>>

    @Query("SELECT * FROM prompts WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<PromptEntity?>

    @Query("SELECT * FROM prompts WHERE type = :type ORDER BY name")
    fun observeByType(type: String): Flow<List<PromptEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFolder(entity: PromptFolderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PromptEntity)

    @Query("DELETE FROM prompts WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM prompt_folders WHERE id = :id")
    suspend fun deleteFolder(id: String)
}

@Dao
interface SnippetDao {
    @Query("SELECT * FROM snippets WHERE scopeId = :scopeId ORDER BY pinned DESC, createdAt DESC")
    fun observe(scopeId: String): Flow<List<SnippetEntity>>

    @Query("SELECT * FROM snippets WHERE scopeId = :scopeId AND category = :category ORDER BY pinned DESC, createdAt DESC")
    fun observeByCategory(scopeId: String, category: String): Flow<List<SnippetEntity>>

    /** App-wide notes (and similar) — not tied to a book. */
    @Query("SELECT * FROM snippets WHERE category = :category ORDER BY pinned DESC, createdAt DESC")
    fun observeCategory(category: String): Flow<List<SnippetEntity>>

    @Query("SELECT * FROM snippets WHERE category = :category ORDER BY pinned DESC, createdAt DESC")
    suspend fun getByCategory(category: String): List<SnippetEntity>

    @Query(
        "UPDATE snippets SET scopeType = :scopeType, scopeId = :scopeId WHERE category = :category",
    )
    suspend fun reassignCategoryScope(category: String, scopeType: String, scopeId: String)

    @Query("SELECT * FROM snippets WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<SnippetEntity?>

    @Query("SELECT * FROM snippets WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): SnippetEntity?

    @Query("SELECT * FROM snippets WHERE scopeId = :scopeId ORDER BY pinned DESC, createdAt DESC")
    suspend fun get(scopeId: String): List<SnippetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SnippetEntity)

    @Query("DELETE FROM snippets WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface WorkshopChatDao {
    @Query("SELECT * FROM chat_threads WHERE scopeId = :scopeId ORDER BY pinned DESC, updatedAt DESC")
    fun observeThreads(scopeId: String): Flow<List<ChatThreadEntity>>

    @Query("SELECT * FROM chat_threads WHERE scopeId = :scopeId ORDER BY pinned DESC, updatedAt DESC")
    suspend fun getThreads(scopeId: String): List<ChatThreadEntity>

    @Query("SELECT * FROM chat_messages WHERE threadId = :threadId ORDER BY createdAt")
    fun observeMessages(threadId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE threadId = :threadId ORDER BY createdAt")
    suspend fun getMessages(threadId: String): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertThread(entity: ChatThreadEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessage(entity: ChatMessageEntity)

    @Query("DELETE FROM chat_messages WHERE id = :id")
    suspend fun deleteMessage(id: String)
}

@Dao
interface RoleplayDao {
    @Query("SELECT * FROM rp_characters ORDER BY name")
    fun observeCharacters(): Flow<List<RpCharacterEntity>>

    @Query("SELECT * FROM rp_characters WHERE id = :id LIMIT 1")
    fun observeCharacter(id: String): Flow<RpCharacterEntity?>

    @Query("SELECT * FROM rp_characters WHERE id = :id LIMIT 1")
    suspend fun getCharacter(id: String): RpCharacterEntity?

    @Query("SELECT * FROM rp_characters ORDER BY name")
    suspend fun getCharacters(): List<RpCharacterEntity>

    @Query("SELECT * FROM rp_chats ORDER BY updatedAt DESC")
    fun observeChats(): Flow<List<RpChatEntity>>

    @Query("SELECT * FROM rp_chats WHERE id = :id LIMIT 1")
    suspend fun getChat(id: String): RpChatEntity?

    @Query("SELECT * FROM rp_chats ORDER BY updatedAt DESC")
    suspend fun getChats(): List<RpChatEntity>

    @Query(
        "SELECT * FROM rp_messages WHERE chatId = :chatId AND displayMode = :displayMode ORDER BY createdAt",
    )
    fun observeMessages(chatId: String, displayMode: String): Flow<List<RpMessageEntity>>

    /** All modes for a chat (export / migration helpers). */
    @Query("SELECT * FROM rp_messages WHERE chatId = :chatId ORDER BY createdAt")
    suspend fun getMessages(chatId: String): List<RpMessageEntity>

    @Query(
        "SELECT * FROM rp_messages WHERE chatId = :chatId AND displayMode = :displayMode ORDER BY createdAt",
    )
    suspend fun getMessagesForMode(chatId: String, displayMode: String): List<RpMessageEntity>

    @Query("SELECT * FROM rp_personas ORDER BY isDefault DESC, name")
    fun observePersonas(): Flow<List<RpPersonaEntity>>

    @Query("SELECT * FROM rp_personas WHERE id = :id LIMIT 1")
    fun observePersona(id: String): Flow<RpPersonaEntity?>

    @Query("SELECT * FROM rp_personas WHERE id = :id LIMIT 1")
    suspend fun getPersona(id: String): RpPersonaEntity?

    @Query("SELECT * FROM rp_personas ORDER BY isDefault DESC, name")
    suspend fun getPersonas(): List<RpPersonaEntity>

    @Query("SELECT * FROM rp_messages WHERE chatId = :chatId AND swipeGroupId = :groupId ORDER BY swipeIndex")
    suspend fun getSwipes(chatId: String, groupId: String): List<RpMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCharacter(entity: RpCharacterEntity)

    @Query("DELETE FROM rp_characters WHERE id = :id")
    suspend fun deleteCharacter(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPersona(entity: RpPersonaEntity)

    @Query("DELETE FROM rp_personas WHERE id = :id")
    suspend fun deletePersona(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChat(entity: RpChatEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessage(entity: RpMessageEntity)

    @Query("DELETE FROM rp_messages WHERE id = :id")
    suspend fun deleteMessage(id: String)
}

@Dao
interface MediaDao {
    @Query("SELECT * FROM media WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): MediaEntity?

    @Query("SELECT * FROM media ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<MediaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MediaEntity)
}
