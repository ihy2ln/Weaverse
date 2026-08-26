package com.ihy2ln.weaverse.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 9,
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

    companion object {
        /** Adds storyboard page metadata to roleplay chats — additive, no data loss. */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE rp_chats ADD COLUMN pagesJson TEXT NOT NULL DEFAULT '[]'",
                )
            }
        }

        /** Tracks when a chat was last opened, so unread badges are real. */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE rp_chats ADD COLUMN lastReadAt INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        /** Party membership and equipment slots for the RPG Roster/Inventory. */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE rp_characters ADD COLUMN inParty INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "ALTER TABLE rp_characters ADD COLUMN equipmentJson TEXT NOT NULL DEFAULT '{}'",
                )
            }
        }

        /** Gives each character a carried-items list for the RPG Inventory. */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE rp_characters ADD COLUMN inventoryJson TEXT NOT NULL DEFAULT '[]'",
                )
            }
        }
    }
}
