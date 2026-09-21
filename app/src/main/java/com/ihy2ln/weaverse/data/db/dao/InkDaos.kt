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
import com.ihy2ln.weaverse.data.db.entities.MediaEntity
import com.ihy2ln.weaverse.data.db.entities.MangaChapterEntity
import com.ihy2ln.weaverse.data.db.entities.MangaFavoriteCategoryEntity
import com.ihy2ln.weaverse.data.db.entities.MangaFavoriteEntity
import com.ihy2ln.weaverse.data.db.entities.MangaPageEntity
import com.ihy2ln.weaverse.data.db.entities.MangaSeriesEntity
import com.ihy2ln.weaverse.data.db.entities.PromptEntity
import com.ihy2ln.weaverse.data.db.entities.PromptFolderEntity
import com.ihy2ln.weaverse.data.db.entities.RpCharacterEntity
import com.ihy2ln.weaverse.data.db.entities.RpChatEntity
import com.ihy2ln.weaverse.data.db.entities.RpMessageEntity
import com.ihy2ln.weaverse.data.db.entities.RpPersonaEntity
import com.ihy2ln.weaverse.data.db.entities.RpRoomMemberEntity
import com.ihy2ln.weaverse.data.db.entities.RpgCampaignSaveEntity
import com.ihy2ln.weaverse.data.db.entities.SceneEntity
import com.ihy2ln.weaverse.data.db.entities.SceneRevisionEntity
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
    @Query("SELECT s.* FROM scenes s INNER JOIN chapters c ON c.id = s.chapterId INNER JOIN acts a ON a.id = c.actId WHERE a.bookId = :bookId ORDER BY a.sortOrder, c.sortOrder, s.sortOrder")
    fun observeBookScenes(bookId: String): Flow<List<SceneEntity>>
    @Query("SELECT * FROM acts WHERE bookId = :bookId ORDER BY sortOrder")
    fun observeActs(bookId: String): Flow<List<ActEntity>>

    @Query("SELECT * FROM acts WHERE bookId = :bookId ORDER BY sortOrder")
    suspend fun getActs(bookId: String): List<ActEntity>

    @Query("SELECT * FROM acts WHERE id = :id LIMIT 1")
    suspend fun getAct(id: String): ActEntity?

    @Query("SELECT * FROM chapters WHERE actId = :actId ORDER BY sortOrder")
    fun observeChapters(actId: String): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapters WHERE actId = :actId ORDER BY sortOrder")
    suspend fun getChapters(actId: String): List<ChapterEntity>

    @Query("SELECT * FROM chapters WHERE id = :id LIMIT 1")
    suspend fun getChapter(id: String): ChapterEntity?

    @Query("SELECT * FROM scenes WHERE chapterId = :chapterId ORDER BY sortOrder")
    fun observeScenes(chapterId: String): Flow<List<SceneEntity>>

    @Query("SELECT * FROM scenes WHERE chapterId = :chapterId ORDER BY sortOrder")
    suspend fun getScenes(chapterId: String): List<SceneEntity>

    @Query("SELECT * FROM scenes WHERE id = :id LIMIT 1")
    fun observeScene(id: String): Flow<SceneEntity?>

    @Query("SELECT * FROM scenes WHERE id = :id LIMIT 1")
    suspend fun getScene(id: String): SceneEntity?

    @Query(
        """
        SELECT s.id AS id, s.title AS title, s.plainText AS plainText, s.docJson AS docJson,
               s.wordCount AS wordCount, c.id AS chapterId, c.title AS chapterTitle
        FROM scenes s
        INNER JOIN chapters c ON c.id = s.chapterId
        INNER JOIN acts a ON a.id = c.actId
        WHERE a.bookId = :bookId
        ORDER BY a.sortOrder, c.sortOrder, s.sortOrder
        """,
    )
    suspend fun getReaderScenes(bookId: String): List<ReaderSceneRow>

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

    @Query("SELECT * FROM scene_revisions WHERE sceneId = :sceneId ORDER BY createdAt DESC")
    fun observeRevisions(sceneId: String): Flow<List<SceneRevisionEntity>>

    @Query("SELECT * FROM scene_revisions WHERE sceneId = :sceneId ORDER BY createdAt DESC")
    suspend fun getRevisions(sceneId: String): List<SceneRevisionEntity>

    @Query("SELECT * FROM scene_revisions WHERE id = :id LIMIT 1")
    suspend fun getRevision(id: String): SceneRevisionEntity?

    @Query("SELECT * FROM scene_revisions WHERE sceneId = :sceneId ORDER BY createdAt DESC LIMIT 1")
    suspend fun latestRevision(sceneId: String): SceneRevisionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRevision(entity: SceneRevisionEntity)

    @Query("DELETE FROM scene_revisions WHERE id = :id")
    suspend fun deleteRevision(id: String)

    @Query(
        "DELETE FROM scene_revisions WHERE sceneId = :sceneId AND id NOT IN " +
            "(SELECT id FROM scene_revisions WHERE sceneId = :sceneId ORDER BY createdAt DESC LIMIT :keep)",
    )
    suspend fun pruneRevisions(sceneId: String, keep: Int)
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

    @Query("DELETE FROM chat_messages WHERE threadId = :threadId")
    suspend fun deleteMessagesForThread(threadId: String)

    @Query("DELETE FROM chat_threads WHERE id = :threadId")
    suspend fun deleteThread(threadId: String)
}

@Dao
interface RoleplayDao {
    @Query("SELECT * FROM rpg_campaign_saves WHERE campaignId = :campaignId LIMIT 1")
    suspend fun getRpgCampaignSave(campaignId: String): RpgCampaignSaveEntity?

    /** Lets the Campaign shelf show which campaigns are still an unfinished setup draft. */
    @Query("SELECT * FROM rpg_campaign_saves")
    fun observeAllRpgCampaignSaves(): kotlinx.coroutines.flow.Flow<List<RpgCampaignSaveEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRpgCampaignSave(entity: RpgCampaignSaveEntity)

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

    /** Most recently touched chat for a character, if one exists. */
    @Query("SELECT * FROM rp_chats WHERE characterId = :characterId ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getChatForCharacter(characterId: String): RpChatEntity?

    /** Discord-style text/character rooms scoped to one work's server. */
    @Query(
        "SELECT * FROM rp_chats WHERE bookId = :bookId AND displayMode = 'messenger' " +
            "AND roomKind IN ('channel', 'character') ORDER BY createdAt",
    )
    fun observeRoomsForBook(bookId: String): Flow<List<RpChatEntity>>

    /** Direct messages: explicit DMs plus legacy messenger chats with no owning work. */
    @Query(
        "SELECT * FROM rp_chats WHERE roomKind = 'dm' OR (roomKind = '' AND " +
            "displayMode = 'messenger' AND bookId IS NULL) ORDER BY updatedAt DESC",
    )
    fun observeDmChats(): Flow<List<RpChatEntity>>

    /** Newest message across all modes, for friends-list previews. */
    @Query("SELECT * FROM rp_messages WHERE chatId = :chatId ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestMessage(chatId: String): RpMessageEntity?

    @Query("SELECT COUNT(*) FROM rp_characters")
    suspend fun countCharacters(): Int

    /** Character messages that arrived after the chat was last opened. */
    @Query(
        "SELECT COUNT(*) FROM rp_messages WHERE chatId = :chatId AND role != 'user' " +
            "AND isActiveSwipe = 1 AND createdAt > :since",
    )
    suspend fun countUnread(chatId: String, since: Long): Int

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

    @Query("DELETE FROM rp_chats WHERE id = :id")
    suspend fun deleteChat(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessage(entity: RpMessageEntity)

    @Query("DELETE FROM rp_messages WHERE id = :id")
    suspend fun deleteMessage(id: String)

    @Query("DELETE FROM rp_messages WHERE chatId = :chatId")
    suspend fun deleteMessagesForChat(chatId: String)

    @Query("SELECT * FROM rp_room_members WHERE roomId = :roomId ORDER BY addedAt")
    fun observeMembers(roomId: String): Flow<List<RpRoomMemberEntity>>

    @Query("SELECT * FROM rp_room_members WHERE roomId = :roomId ORDER BY addedAt")
    suspend fun getMembers(roomId: String): List<RpRoomMemberEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMember(entity: RpRoomMemberEntity)

    @Query("DELETE FROM rp_room_members WHERE roomId = :roomId AND characterId = :characterId")
    suspend fun deleteMember(roomId: String, characterId: String)

    @Query(
        "SELECT m.* FROM rp_room_members m INNER JOIN rp_chats c ON c.id = m.roomId WHERE c.bookId = :bookId",
    )
    suspend fun getMembersForBook(bookId: String): List<RpRoomMemberEntity>
}

@Dao
interface MediaDao {
    @Query("SELECT * FROM media WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): MediaEntity?

    @Query("SELECT * FROM media ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media WHERE type = 'image' AND category = :category ORDER BY displayName, id")
    suspend fun getImagesByCategory(category: String): List<MediaEntity>

    @Query("SELECT * FROM media WHERE type = 'image' AND tags LIKE '%' || :tag || '%' ORDER BY displayName, id")
    suspend fun getImagesByTag(tag: String): List<MediaEntity>

    @Query("SELECT * FROM media WHERE type = :type AND tags LIKE '%' || :tag || '%' ORDER BY displayName, id")
    suspend fun getByTypeAndTag(type: String, tag: String): List<MediaEntity>

    @Query("SELECT DISTINCT category FROM media WHERE type = 'image' AND category != '' ORDER BY category")
    suspend fun getImageCategories(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MediaEntity)
}

@Dao
interface MangaDao {
    @Query("SELECT * FROM manga_chapters ORDER BY updatedAt DESC, mangaTitle, chapterNumber")
    fun observeChapters(): Flow<List<MangaChapterEntity>>

    @Query("SELECT * FROM manga_chapters WHERE id = :id LIMIT 1")
    suspend fun getChapter(id: String): MangaChapterEntity?

    @Query("SELECT * FROM manga_chapters WHERE sourceId = :sourceId AND remoteId = :remoteId LIMIT 1")
    suspend fun getChapterByRemoteId(sourceId: String, remoteId: String): MangaChapterEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChapter(entity: MangaChapterEntity)

    @Query("UPDATE manga_chapters SET lastPageRead = :pageIndex, lastReadAt = :readAt, read = CASE WHEN :finished THEN 1 ELSE read END WHERE id = :chapterId")
    suspend fun recordReadingProgress(chapterId: String, pageIndex: Int, readAt: Long, finished: Boolean)

    @Query("UPDATE manga_chapters SET read = :read, lastPageRead = CASE WHEN :read THEN pageCount - 1 ELSE lastPageRead END WHERE id = :chapterId")
    suspend fun setChapterRead(chapterId: String, read: Boolean)

    @Query("UPDATE manga_chapters SET bookmarked = :bookmarked WHERE id = :chapterId")
    suspend fun setChapterBookmarked(chapterId: String, bookmarked: Boolean)

    @Query("UPDATE manga_chapters SET lastReadAt = 0, lastPageRead = 0")
    suspend fun clearReadingHistory()

    @Query("SELECT * FROM manga_chapters WHERE mangaId = :mangaId")
    suspend fun getChaptersByManga(mangaId: String): List<MangaChapterEntity>

    @Query("DELETE FROM manga_pages WHERE chapterId = :chapterId")
    suspend fun deletePagesForChapter(chapterId: String)

    @Query("DELETE FROM manga_chapters WHERE id = :chapterId")
    suspend fun deleteChapter(chapterId: String)

    @Query("SELECT * FROM manga_pages WHERE chapterId = :chapterId ORDER BY pageIndex")
    fun observePages(chapterId: String): Flow<List<MangaPageEntity>>

    @Query("SELECT * FROM manga_pages WHERE pageIndex = 0 ORDER BY updatedAt DESC")
    fun observeCoverPages(): Flow<List<MangaPageEntity>>

    @Query("SELECT * FROM manga_pages WHERE chapterId = :chapterId ORDER BY pageIndex")
    suspend fun getPages(chapterId: String): List<MangaPageEntity>

    @Query("SELECT * FROM manga_pages WHERE id = :id LIMIT 1")
    suspend fun getPage(id: String): MangaPageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPage(entity: MangaPageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPages(entities: List<MangaPageEntity>)

    @Query("SELECT * FROM manga_series ORDER BY title COLLATE NOCASE")
    fun observeSeries(): Flow<List<MangaSeriesEntity>>

    @Query("SELECT * FROM manga_series WHERE sourceId = :sourceId AND remoteId = :remoteId LIMIT 1")
    suspend fun getSeries(sourceId: String, remoteId: String): MangaSeriesEntity?

    @Query("SELECT * FROM manga_chapters WHERE sourceId NOT IN ('local', 'weblink') AND (LOWER(mangaTitle) IN ('last updates', 'latest updates', 'manga', 'series page') OR NOT EXISTS (SELECT 1 FROM manga_series WHERE manga_series.sourceId = manga_chapters.sourceId AND manga_series.remoteId = manga_chapters.mangaId AND manga_series.coverUrl != ''))")
    suspend fun getChaptersWithInvalidTitle(): List<MangaChapterEntity>

    @Query("UPDATE manga_chapters SET mangaTitle = :title WHERE sourceId = :sourceId AND mangaId = :remoteId")
    suspend fun updateSeriesTitle(sourceId: String, remoteId: String, title: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSeries(entity: MangaSeriesEntity)

    @Query("SELECT * FROM manga_favorite_categories ORDER BY sortOrder, name COLLATE NOCASE")
    fun observeFavoriteCategories(): Flow<List<MangaFavoriteCategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFavoriteCategory(entity: MangaFavoriteCategoryEntity)

    @Query("SELECT COUNT(*) FROM manga_favorite_categories")
    suspend fun favoriteCategoryCount(): Int

    @Query("SELECT * FROM manga_favorites ORDER BY addedAt DESC")
    fun observeFavorites(): Flow<List<MangaFavoriteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFavorite(entity: MangaFavoriteEntity)

    @Query("DELETE FROM manga_favorites WHERE seriesId = :seriesId AND categoryId = :categoryId")
    suspend fun removeFavorite(seriesId: String, categoryId: String)

    @Query("DELETE FROM manga_favorites WHERE seriesId = :seriesId")
    suspend fun removeAllFavorites(seriesId: String)

    @Query("DELETE FROM manga_series WHERE id = :seriesId")
    suspend fun deleteSeries(seriesId: String)
}

/** Flattened manuscript row for the Reader — one JOIN instead of acts→chapters→scenes. */
data class ReaderSceneRow(
    val id: String,
    val title: String,
    val plainText: String,
    val docJson: String,
    val wordCount: Int,
    val chapterId: String,
    val chapterTitle: String,
)

