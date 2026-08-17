package com.ihy2ln.weaverse.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.ihy2ln.weaverse.data.db.dao.ActDao
import com.ihy2ln.weaverse.data.db.dao.BookDao
import com.ihy2ln.weaverse.data.db.dao.ChapterDao
import com.ihy2ln.weaverse.data.db.dao.ChatMessageDao
import com.ihy2ln.weaverse.data.db.dao.ChatMessageFtsDao
import com.ihy2ln.weaverse.data.db.dao.ChatThreadDao
import com.ihy2ln.weaverse.data.db.dao.CodexCategoryDao
import com.ihy2ln.weaverse.data.db.dao.ConnectionProfileDao
import com.ihy2ln.weaverse.data.db.dao.CodexEntryDao
import com.ihy2ln.weaverse.data.db.dao.CodexEntryFtsDao
import com.ihy2ln.weaverse.data.db.dao.CodexEntryLoreDao
import com.ihy2ln.weaverse.data.db.dao.CodexRelationDao
import com.ihy2ln.weaverse.data.db.dao.LabelDao
import com.ihy2ln.weaverse.data.db.dao.MediaDao
import com.ihy2ln.weaverse.data.db.dao.MediaUsageDao
import com.ihy2ln.weaverse.data.db.dao.ModelCollectionDao
import com.ihy2ln.weaverse.data.db.dao.ModelCollectionModelDao
import com.ihy2ln.weaverse.data.db.dao.PresetDao
import com.ihy2ln.weaverse.data.db.dao.PromptDao
import com.ihy2ln.weaverse.data.db.dao.PromptFolderDao
import com.ihy2ln.weaverse.data.db.dao.PromptModelDao
import com.ihy2ln.weaverse.data.db.dao.RpCharacterDao
import com.ihy2ln.weaverse.data.db.dao.RpChatDao
import com.ihy2ln.weaverse.data.db.dao.RpExpressionDao
import com.ihy2ln.weaverse.data.db.dao.RpGroupDao
import com.ihy2ln.weaverse.data.db.dao.RpGroupMemberDao
import com.ihy2ln.weaverse.data.db.dao.RpMessageDao
import com.ihy2ln.weaverse.data.db.dao.RpMessageFtsDao
import com.ihy2ln.weaverse.data.db.dao.RpPersonaDao
import com.ihy2ln.weaverse.data.db.dao.SceneCodexLinkDao
import com.ihy2ln.weaverse.data.db.dao.SceneDao
import com.ihy2ln.weaverse.data.db.dao.SceneFtsDao
import com.ihy2ln.weaverse.data.db.dao.SeriesDao
import com.ihy2ln.weaverse.data.db.dao.SeriesMemberDao
import com.ihy2ln.weaverse.data.db.dao.SnippetDao
import com.ihy2ln.weaverse.data.db.dao.SnippetFtsDao
import com.ihy2ln.weaverse.data.db.entity.ActEntity
import com.ihy2ln.weaverse.data.db.entity.BookEntity
import com.ihy2ln.weaverse.data.db.entity.ChapterEntity
import com.ihy2ln.weaverse.data.db.entity.ChatMessageEntity
import com.ihy2ln.weaverse.data.db.entity.ChatMessageFtsEntity
import com.ihy2ln.weaverse.data.db.entity.ChatThreadEntity
import com.ihy2ln.weaverse.data.db.entity.CodexCategoryEntity
import com.ihy2ln.weaverse.data.db.entity.CodexEntryEntity
import com.ihy2ln.weaverse.data.db.entity.CodexEntryFtsEntity
import com.ihy2ln.weaverse.data.db.entity.CodexEntryLoreEntity
import com.ihy2ln.weaverse.data.db.entity.CodexRelationEntity
import com.ihy2ln.weaverse.data.db.entity.ConnectionProfileEntity
import com.ihy2ln.weaverse.data.db.entity.LabelEntity
import com.ihy2ln.weaverse.data.db.entity.MediaEntity
import com.ihy2ln.weaverse.data.db.entity.MediaUsageEntity
import com.ihy2ln.weaverse.data.db.entity.ModelCollectionEntity
import com.ihy2ln.weaverse.data.db.entity.ModelCollectionModelEntity
import com.ihy2ln.weaverse.data.db.entity.PresetEntity
import com.ihy2ln.weaverse.data.db.entity.PromptEntity
import com.ihy2ln.weaverse.data.db.entity.PromptFolderEntity
import com.ihy2ln.weaverse.data.db.entity.PromptModelEntity
import com.ihy2ln.weaverse.data.db.entity.RpCharacterEntity
import com.ihy2ln.weaverse.data.db.entity.RpChatEntity
import com.ihy2ln.weaverse.data.db.entity.RpExpressionEntity
import com.ihy2ln.weaverse.data.db.entity.RpGroupEntity
import com.ihy2ln.weaverse.data.db.entity.RpGroupMemberEntity
import com.ihy2ln.weaverse.data.db.entity.RpMessageEntity
import com.ihy2ln.weaverse.data.db.entity.RpMessageFtsEntity
import com.ihy2ln.weaverse.data.db.entity.RpPersonaEntity
import com.ihy2ln.weaverse.data.db.entity.SceneCodexLinkEntity
import com.ihy2ln.weaverse.data.db.entity.SceneEntity
import com.ihy2ln.weaverse.data.db.entity.SceneFtsEntity
import com.ihy2ln.weaverse.data.db.entity.SeriesEntity
import com.ihy2ln.weaverse.data.db.entity.SeriesMemberEntity
import com.ihy2ln.weaverse.data.db.entity.SnippetEntity
import com.ihy2ln.weaverse.data.db.entity.SnippetFtsEntity

@Database(
    entities = [
        SeriesEntity::class, SeriesMemberEntity::class, BookEntity::class, ActEntity::class, ChapterEntity::class,
        SceneEntity::class, SceneCodexLinkEntity::class,
        CodexCategoryEntity::class, CodexEntryEntity::class, CodexEntryLoreEntity::class, CodexRelationEntity::class,
        SnippetEntity::class, LabelEntity::class,
        PromptFolderEntity::class, PromptEntity::class, PromptModelEntity::class,
        ModelCollectionEntity::class, ModelCollectionModelEntity::class, PresetEntity::class,
        ChatThreadEntity::class, ChatMessageEntity::class,
        RpCharacterEntity::class, RpPersonaEntity::class, RpGroupEntity::class, RpGroupMemberEntity::class,
        RpChatEntity::class, RpMessageEntity::class, RpExpressionEntity::class,
        MediaEntity::class, MediaUsageEntity::class,
        SceneFtsEntity::class, CodexEntryFtsEntity::class, ChatMessageFtsEntity::class,
        RpMessageFtsEntity::class, SnippetFtsEntity::class,
        ConnectionProfileEntity::class,
    ],
    version = 1,
    // No migrations exist yet at v1, so there's nothing to test against a
    // schema snapshot yet — flip this on (and configure the KSP
    // `room.schemaLocation` arg) once v2 introduces the first migration.
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun seriesDao(): SeriesDao
    abstract fun seriesMemberDao(): SeriesMemberDao
    abstract fun bookDao(): BookDao
    abstract fun actDao(): ActDao
    abstract fun chapterDao(): ChapterDao
    abstract fun sceneDao(): SceneDao
    abstract fun sceneCodexLinkDao(): SceneCodexLinkDao

    abstract fun codexCategoryDao(): CodexCategoryDao
    abstract fun codexEntryDao(): CodexEntryDao
    abstract fun codexEntryLoreDao(): CodexEntryLoreDao
    abstract fun codexRelationDao(): CodexRelationDao

    abstract fun snippetDao(): SnippetDao
    abstract fun labelDao(): LabelDao

    abstract fun promptFolderDao(): PromptFolderDao
    abstract fun promptDao(): PromptDao
    abstract fun promptModelDao(): PromptModelDao
    abstract fun modelCollectionDao(): ModelCollectionDao
    abstract fun modelCollectionModelDao(): ModelCollectionModelDao
    abstract fun presetDao(): PresetDao

    abstract fun chatThreadDao(): ChatThreadDao
    abstract fun chatMessageDao(): ChatMessageDao

    abstract fun rpCharacterDao(): RpCharacterDao
    abstract fun rpPersonaDao(): RpPersonaDao
    abstract fun rpGroupDao(): RpGroupDao
    abstract fun rpGroupMemberDao(): RpGroupMemberDao
    abstract fun rpChatDao(): RpChatDao
    abstract fun rpMessageDao(): RpMessageDao
    abstract fun rpExpressionDao(): RpExpressionDao

    abstract fun mediaDao(): MediaDao
    abstract fun mediaUsageDao(): MediaUsageDao

    abstract fun sceneFtsDao(): SceneFtsDao
    abstract fun codexEntryFtsDao(): CodexEntryFtsDao
    abstract fun chatMessageFtsDao(): ChatMessageFtsDao
    abstract fun rpMessageFtsDao(): RpMessageFtsDao
    abstract fun snippetFtsDao(): SnippetFtsDao

    abstract fun connectionProfileDao(): ConnectionProfileDao

    companion object {
        const val NAME = "weaverse.db"
    }
}
