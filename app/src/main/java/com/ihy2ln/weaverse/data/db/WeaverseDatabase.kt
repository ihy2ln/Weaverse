package com.ihy2ln.weaverse.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.ihy2ln.weaverse.data.db.dao.BookDao
import com.ihy2ln.weaverse.data.db.dao.CodexDao
import com.ihy2ln.weaverse.data.db.dao.MediaDao
import com.ihy2ln.weaverse.data.db.dao.ManuscriptDao
import com.ihy2ln.weaverse.data.db.dao.PromptDao
import com.ihy2ln.weaverse.data.db.dao.RoleplayDao
import com.ihy2ln.weaverse.data.db.dao.SeriesDao
import com.ihy2ln.weaverse.data.db.dao.SnippetDao
import com.ihy2ln.weaverse.data.db.dao.WorkshopChatDao
import com.ihy2ln.weaverse.data.db.entities.ActEntity
import com.ihy2ln.weaverse.data.db.entities.AiProfileEntity
import com.ihy2ln.weaverse.data.db.entities.BookEntity
import com.ihy2ln.weaverse.data.db.entities.ChapterEntity
import com.ihy2ln.weaverse.data.db.entities.ChatMessageEntity
import com.ihy2ln.weaverse.data.db.entities.ChatThreadEntity
import com.ihy2ln.weaverse.data.db.entities.CodexCategoryEntity
import com.ihy2ln.weaverse.data.db.entities.CodexEntryEntity
import com.ihy2ln.weaverse.data.db.entities.CodexEntryLoreEntity
import com.ihy2ln.weaverse.data.db.entities.MediaEntity
import com.ihy2ln.weaverse.data.db.entities.PromptEntity
import com.ihy2ln.weaverse.data.db.entities.PromptFolderEntity
import com.ihy2ln.weaverse.data.db.entities.RpCharacterEntity
import com.ihy2ln.weaverse.data.db.entities.RpChatEntity
import com.ihy2ln.weaverse.data.db.entities.RpMessageEntity
import com.ihy2ln.weaverse.data.db.entities.RpPersonaEntity
import com.ihy2ln.weaverse.data.db.entities.SceneCodexLinkEntity
import com.ihy2ln.weaverse.data.db.entities.SceneEntity
import com.ihy2ln.weaverse.data.db.entities.SeriesEntity
import com.ihy2ln.weaverse.data.db.entities.SnippetEntity

@Database(
    entities = [
        SeriesEntity::class,
        BookEntity::class,
        ActEntity::class,
        ChapterEntity::class,
        SceneEntity::class,
        SceneCodexLinkEntity::class,
        CodexCategoryEntity::class,
        CodexEntryEntity::class,
        CodexEntryLoreEntity::class,
        SnippetEntity::class,
        ChatThreadEntity::class,
        ChatMessageEntity::class,
        RpCharacterEntity::class,
        RpPersonaEntity::class,
        RpChatEntity::class,
        RpMessageEntity::class,
        MediaEntity::class,
        PromptFolderEntity::class,
        PromptEntity::class,
        AiProfileEntity::class,
    ],
    version = 5,
    exportSchema = false,
)
@TypeConverters(InkTypeConverters::class)
abstract class WeaverseDatabase : RoomDatabase() {
    abstract fun seriesDao(): SeriesDao
    abstract fun bookDao(): BookDao
    abstract fun manuscriptDao(): ManuscriptDao
    abstract fun codexDao(): CodexDao
    abstract fun snippetDao(): SnippetDao
    abstract fun workshopChatDao(): WorkshopChatDao
    abstract fun roleplayDao(): RoleplayDao
    abstract fun mediaDao(): MediaDao
    abstract fun promptDao(): PromptDao
}
