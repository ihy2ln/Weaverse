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
import com.ihy2ln.weaverse.data.db.dao.TextGameSaveDao
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
import com.ihy2ln.weaverse.data.db.entities.SceneRevisionEntity
import com.ihy2ln.weaverse.data.db.entities.SeriesEntity
import com.ihy2ln.weaverse.data.db.entities.SnippetEntity
import com.ihy2ln.weaverse.data.db.entities.TextGameSaveEntity

@Database(
    entities = [
        SeriesEntity::class,
        BookEntity::class,
        ActEntity::class,
        ChapterEntity::class,
        SceneEntity::class,
        SceneRevisionEntity::class,
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
        TextGameSaveEntity::class,
    ],
    version = 17,
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
    abstract fun textGameSaveDao(): TextGameSaveDao

    companion object {
        /** Adds first-class Pictures organization and machine-searchable scene labels. */
        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE media ADD COLUMN displayName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE media ADD COLUMN category TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE media ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
            }
        }

        /** Dedicated campaign saves for deterministic Text Games. */
        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS text_game_saves (
                        campaignId TEXT NOT NULL,
                        gameId TEXT NOT NULL,
                        schemaVersion INTEGER NOT NULL,
                        persistentStateJson TEXT NOT NULL,
                        runStateJson TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        PRIMARY KEY(campaignId, gameId)
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_text_game_saves_campaignId ON text_game_saves(campaignId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_text_game_saves_gameId ON text_game_saves(gameId)")
            }
        }

        /** Codex entries gain RPG roster sheets and inventories (Roster/Inventory parity). */
        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE codex_entries ADD COLUMN sheetJson TEXT NOT NULL DEFAULT '{}'")
                db.execSQL("ALTER TABLE codex_entries ADD COLUMN inventoryJson TEXT NOT NULL DEFAULT '[]'")
            }
        }

        /** Brainstorm sub-categories: nest chat threads under a parent. */
        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE chat_threads ADD COLUMN parentThreadId TEXT",
                )
            }
        }

        /** Discord-style Chatting rooms: classify rp_chats rows by room kind. */
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE rp_chats ADD COLUMN roomKind TEXT NOT NULL DEFAULT ''",
                )
            }
        }

        /** Usage columns on chat messages plus hourly scene snapshots. */
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE chat_messages ADD COLUMN promptTokens INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "ALTER TABLE chat_messages ADD COLUMN completionTokens INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "ALTER TABLE chat_messages ADD COLUMN costUsd REAL NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "ALTER TABLE rp_messages ADD COLUMN promptTokens INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "ALTER TABLE rp_messages ADD COLUMN completionTokens INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "ALTER TABLE rp_messages ADD COLUMN costUsd REAL NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS scene_revisions (
                        id TEXT NOT NULL PRIMARY KEY,
                        sceneId TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        docJson TEXT NOT NULL,
                        plainText TEXT NOT NULL,
                        wordCount INTEGER NOT NULL DEFAULT 0,
                        kind TEXT NOT NULL DEFAULT 'hourly'
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_scene_revisions_sceneId ON scene_revisions(sceneId)",
                )
            }
        }

        /** Separates novel/campaign/storyboard shelves and links canvas chats to their work. */
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE books ADD COLUMN workType TEXT NOT NULL DEFAULT 'novel'",
                )
                db.execSQL("ALTER TABLE rp_chats ADD COLUMN bookId TEXT")
            }
        }

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

        /** Personas carry gear too, so You has a real inventory row. */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE rp_personas ADD COLUMN inventoryJson TEXT NOT NULL DEFAULT '[]'",
                )
                db.execSQL(
                    "ALTER TABLE rp_personas ADD COLUMN equipmentJson TEXT NOT NULL DEFAULT '{}'",
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
