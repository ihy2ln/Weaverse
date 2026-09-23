package com.ihy2ln.weaverse.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ihy2ln.weaverse.data.db.dao.BookDao
import com.ihy2ln.weaverse.data.db.dao.CodexDao
import com.ihy2ln.weaverse.data.db.dao.MediaDao
import com.ihy2ln.weaverse.data.db.dao.MangaDao
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
import com.ihy2ln.weaverse.data.db.entities.MangaChapterEntity
import com.ihy2ln.weaverse.data.db.entities.MangaFavoriteCategoryEntity
import com.ihy2ln.weaverse.data.db.entities.MangaFavoriteEntity
import com.ihy2ln.weaverse.data.db.entities.MangaPageEntity
import com.ihy2ln.weaverse.data.db.entities.MangaSeriesEntity
import com.ihy2ln.weaverse.data.db.entities.MangaTrackingEntity
import com.ihy2ln.weaverse.data.db.entities.MangaUpdateErrorEntity
import com.ihy2ln.weaverse.data.db.entities.PromptEntity
import com.ihy2ln.weaverse.data.db.entities.PromptFolderEntity
import com.ihy2ln.weaverse.data.db.entities.RpCharacterEntity
import com.ihy2ln.weaverse.data.db.entities.RpChatEntity
import com.ihy2ln.weaverse.data.db.entities.RpMessageEntity
import com.ihy2ln.weaverse.data.db.entities.RpPersonaEntity
import com.ihy2ln.weaverse.data.db.entities.RpRoomMemberEntity
import com.ihy2ln.weaverse.data.db.entities.SceneCodexLinkEntity
import com.ihy2ln.weaverse.data.db.entities.SceneEntity
import com.ihy2ln.weaverse.data.db.entities.SceneRevisionEntity
import com.ihy2ln.weaverse.data.db.entities.SeriesEntity
import com.ihy2ln.weaverse.data.db.entities.SnippetEntity
import com.ihy2ln.weaverse.data.db.entities.TextGameSaveEntity
import com.ihy2ln.weaverse.data.db.entities.RpgCampaignSaveEntity

@Database(
    entities = [com.ihy2ln.weaverse.feature.library.BookBrowsing::class,com.ihy2ln.weaverse.feature.shell.HomeAccess::class,
        com.ihy2ln.weaverse.data.db.entities.NovelPromptDraft::class,
        com.ihy2ln.weaverse.data.db.entities.NovelWritingSettings::class,
        SeriesEntity::class,
        BookEntity::class,
        ActEntity::class,
        ChapterEntity::class,
        SceneEntity::class,
        SceneRevisionEntity::class,
        SceneCodexLinkEntity::class,
        com.ihy2ln.weaverse.data.db.entities.NovelMediaLink::class,
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
        MangaChapterEntity::class,
        MangaPageEntity::class,
        MangaSeriesEntity::class,
        MangaFavoriteCategoryEntity::class,
        MangaFavoriteEntity::class,
        MangaTrackingEntity::class,
        MangaUpdateErrorEntity::class,
        PromptFolderEntity::class,
        PromptEntity::class,
        AiProfileEntity::class,
        TextGameSaveEntity::class,
        RpgCampaignSaveEntity::class,
        RpRoomMemberEntity::class,
    ],
    version = 29,
    exportSchema = false,
)
@TypeConverters(InkTypeConverters::class)
abstract class WeaverseDatabase : RoomDatabase() {
    abstract fun novelWritingDao(): com.ihy2ln.weaverse.data.db.dao.NovelWritingDao
    abstract fun novelMediaDao(): com.ihy2ln.weaverse.data.db.dao.NovelMediaDao
    abstract fun seriesDao(): SeriesDao
    abstract fun bookDao(): BookDao
    abstract fun manuscriptDao(): ManuscriptDao
    abstract fun codexDao(): CodexDao
    abstract fun snippetDao(): SnippetDao
    abstract fun workshopChatDao(): WorkshopChatDao
    abstract fun roleplayDao(): RoleplayDao
    abstract fun mediaDao(): MediaDao
    abstract fun mangaDao(): MangaDao
    abstract fun promptDao(): PromptDao
    abstract fun textGameSaveDao(): TextGameSaveDao

    abstract fun homeAccessDao(): com.ihy2ln.weaverse.feature.shell.HomeAccessDao
    abstract fun bookBrowsingDao(): com.ihy2ln.weaverse.feature.library.BookBrowsingDao
    companion object {
        val MIGRATION_28_29 = object : Migration(28, 29) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE novel_writing_settings ADD COLUMN startProgress TEXT NOT NULL DEFAULT ''")
            }
        }
        val MIGRATION_27_28 = object : Migration(27, 28) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE novel_writing_settings ADD COLUMN companions TEXT NOT NULL DEFAULT 'party'")
            }
        }
        val MIGRATION_26_27 = object : Migration(26, 27) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS rp_room_members (roomId TEXT NOT NULL, characterId TEXT NOT NULL, codexEntryId TEXT, seeded INTEGER NOT NULL DEFAULT 1, addedAt INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(roomId, characterId))",
                )
                db.execSQL("ALTER TABLE rp_messages ADD COLUMN speakerName TEXT NOT NULL DEFAULT ''")
            }
        }
        val MIGRATION_25_26 = object : Migration(25, 26) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS book_browsing (bookId TEXT NOT NULL PRIMARY KEY, synopsis TEXT NOT NULL, backdropMediaId TEXT, listedAt INTEGER NOT NULL, readAt INTEGER NOT NULL, writeAt INTEGER NOT NULL, writeSceneId TEXT NOT NULL)")
            }
        }
        val MIGRATION_24_25 = object : Migration(24, 25) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS home_access (mode TEXT NOT NULL, kind TEXT NOT NULL, contentId TEXT NOT NULL, accessedAt INTEGER NOT NULL, target TEXT NOT NULL, PRIMARY KEY(mode, kind, contentId))")
            }
        }
        val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS novel_prompt_drafts (sceneId TEXT NOT NULL PRIMARY KEY, stateJson TEXT NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS novel_writing_settings (bookId TEXT NOT NULL PRIMARY KEY, memory TEXT NOT NULL, authorNote TEXT NOT NULL, modelRef TEXT NOT NULL, outputWords INTEGER NOT NULL)")
            }
        }
        val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS novel_media_links (id TEXT NOT NULL PRIMARY KEY, bookId TEXT NOT NULL, sceneId TEXT NOT NULL, entryId TEXT NOT NULL, mediaId TEXT NOT NULL, caption TEXT NOT NULL, altText TEXT NOT NULL, provenance TEXT NOT NULL, createdAt INTEGER NOT NULL)")
            }
        }
        val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf("publicationType", "releaseYear", "contentRating", "catalogScore").forEach { column ->
                    db.execSQL("ALTER TABLE manga_series ADD COLUMN $column TEXT NOT NULL DEFAULT ''")
                }
            }
        }

        val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE manga_series ADD COLUMN authors TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE manga_series ADD COLUMN artists TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE manga_series ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE manga_series ADD COLUMN languages TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE manga_series ADD COLUMN publicationStatus TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE manga_chapters ADD COLUMN scanlator TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE manga_chapters ADD COLUMN dateUpload INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE manga_chapters ADD COLUMN read INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE manga_chapters ADD COLUMN bookmarked INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE manga_chapters ADD COLUMN lastPageRead INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE manga_chapters ADD COLUMN lastReadAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS manga_tracks (" +
                        "id TEXT NOT NULL PRIMARY KEY, seriesId TEXT NOT NULL, serviceId TEXT NOT NULL, remoteId TEXT NOT NULL, " +
                        "remoteTitle TEXT NOT NULL, status TEXT NOT NULL, score REAL NOT NULL, progress INTEGER NOT NULL, " +
                        "startedAt INTEGER, completedAt INTEGER, notes TEXT NOT NULL, updatedAt INTEGER NOT NULL)",
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_manga_tracks_seriesId_serviceId ON manga_tracks(seriesId, serviceId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_manga_tracks_serviceId ON manga_tracks(serviceId)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS manga_update_errors (" +
                        "id TEXT NOT NULL PRIMARY KEY, seriesId TEXT NOT NULL, sourceId TEXT NOT NULL, message TEXT NOT NULL, createdAt INTEGER NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_manga_update_errors_seriesId ON manga_update_errors(seriesId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_manga_update_errors_createdAt ON manga_update_errors(createdAt)")
            }
        }
        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS manga_series (" +
                        "id TEXT NOT NULL PRIMARY KEY, sourceId TEXT NOT NULL, remoteId TEXT NOT NULL, title TEXT NOT NULL, " +
                        "description TEXT NOT NULL, coverUrl TEXT NOT NULL, canonicalUrl TEXT NOT NULL, " +
                        "updatedAt INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_manga_series_sourceId_remoteId " +
                        "ON manga_series(sourceId, remoteId)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS manga_favorite_categories (" +
                        "id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, sortOrder INTEGER NOT NULL, " +
                        "createdAt INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_manga_favorite_categories_name " +
                        "ON manga_favorite_categories(name)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS manga_favorites (" +
                        "seriesId TEXT NOT NULL, categoryId TEXT NOT NULL, addedAt INTEGER NOT NULL, " +
                        "PRIMARY KEY(seriesId, categoryId))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_manga_favorites_categoryId ON manga_favorites(categoryId)")
                db.execSQL(
                    "INSERT OR IGNORE INTO manga_favorite_categories(id, name, sortOrder, createdAt) " +
                        "VALUES('favorites', 'Favorites', 0, 0)",
                )
            }
        }
        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS manga_chapters (" +
                        "id TEXT NOT NULL PRIMARY KEY, sourceId TEXT NOT NULL, remoteId TEXT NOT NULL, " +
                        "mangaId TEXT NOT NULL, mangaTitle TEXT NOT NULL, title TEXT NOT NULL, volume TEXT NOT NULL DEFAULT '', " +
                        "chapterNumber TEXT NOT NULL DEFAULT '', language TEXT NOT NULL DEFAULT 'en', canonicalUrl TEXT NOT NULL DEFAULT '', " +
                        "readingOrder TEXT NOT NULL DEFAULT 'ltr', pageCount INTEGER NOT NULL DEFAULT 0, status TEXT NOT NULL DEFAULT 'discovered', " +
                        "progress INTEGER NOT NULL DEFAULT 0, errorMessage TEXT NOT NULL DEFAULT '', updatedAt INTEGER NOT NULL DEFAULT 0, downloadedAt INTEGER, " +
                        "UNIQUE(sourceId, remoteId))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_manga_chapters_mangaId ON manga_chapters(mangaId)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS manga_pages (" +
                        "id TEXT NOT NULL PRIMARY KEY, chapterId TEXT NOT NULL, sourceId TEXT NOT NULL, pageIndex INTEGER NOT NULL, " +
                        "remoteUrl TEXT NOT NULL, fileName TEXT NOT NULL, localPath TEXT NOT NULL DEFAULT '', checksum TEXT NOT NULL DEFAULT '', " +
                        "status TEXT NOT NULL DEFAULT 'queued', errorMessage TEXT NOT NULL DEFAULT '', mediaId TEXT, updatedAt INTEGER NOT NULL DEFAULT 0, " +
                        "UNIQUE(chapterId, pageIndex))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_manga_pages_chapterId ON manga_pages(chapterId)")
            }
        }
        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS rpg_campaign_saves (campaignId TEXT NOT NULL PRIMARY KEY, schemaVersion INTEGER NOT NULL DEFAULT 1, stateJson TEXT NOT NULL, updatedAt INTEGER NOT NULL)")
            }
        }
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
