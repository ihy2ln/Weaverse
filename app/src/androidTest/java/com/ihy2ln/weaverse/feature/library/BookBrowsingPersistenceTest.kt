package com.ihy2ln.weaverse.feature.library

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.ihy2ln.weaverse.data.backup.*
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.data.db.entities.*
import com.ihy2ln.weaverse.data.settings.SettingsRepository
import com.ihy2ln.weaverse.feature.shell.HomeHistory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class BookBrowsingPersistenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun migrationAndFullBackupRestorePreserveLibraryAndBrowsing() = runBlocking {
        val name = "browsing-${UUID.randomUUID()}.db"
        val dir = File(context.cacheDir, name).apply { mkdirs() }
        var db = Room.databaseBuilder(context, WeaverseDatabase::class.java, name).build()
        val settings = SettingsRepository(context, com.ihy2ln.weaverse.data.settings.SecureKeyStore(context))
        try {
            val book = BookEntity("migration-book", null, "Retained title", coverMediaId = "cover", createdAt = 1, updatedAt = 2)
            val scene = SceneEntity("migration-scene", "chapter", "Formatted scene", 0,
                """{"blocks":[{"type":"paragraph","spans":[{"text":"Preserved emphasis","bold":true,"italic":true}]}]}""", "Preserved emphasis", createdAt = 1, updatedAt = 2)
            val prompt = NovelPromptDraft(scene.id, """{"prompt":"Keep my draft","candidates":[{"streamingText":"Keep this candidate"}]}""")
            db.bookDao().upsert(book)
            db.manuscriptDao().upsertAct(ActEntity("act", book.id, "Act", 0))
            db.manuscriptDao().upsertChapter(ChapterEntity("chapter", "act", "Chapter", 0))
            db.manuscriptDao().upsertScene(scene)
            db.novelWritingDao().saveDraft(prompt)
            val media = MediaEntity("cover", "image", "media/cover.png", "image/png", 4, createdAt = 1)
            db.mediaDao().upsert(media)
            HomeHistory(db).record("Novel", "book", book.id, scene.id)
            settings.setReaderScroll(book.id, scene.id, 3, 27)
            db.openHelper.writableDatabase.execSQL("DROP TABLE book_browsing")
            db.openHelper.writableDatabase.version = 25
            db.close()
            db = Room.databaseBuilder(context, WeaverseDatabase::class.java, name).addMigrations(WeaverseDatabase.MIGRATION_25_26).build()
            assertEquals(book, db.bookDao().getById(book.id))
            assertEquals(scene, db.manuscriptDao().getScene(scene.id))
            assertEquals(prompt, db.novelWritingDao().draft(scene.id))
            assertEquals(scene.id, db.homeAccessDao().get("Novel", "book", book.id)?.target)
            assertTrue(db.bookBrowsingDao().observeAll().first().isEmpty())
            coroutineScope {
                launch { db.bookBrowsingDao().synopsis(book.id, "My synopsis") }
                launch { db.bookBrowsingDao().toggleList(book.id, 11) }
                launch { db.bookBrowsingDao().write(book.id, scene.id, 12) }
                launch { db.bookBrowsingDao().read(book.id, 13) }
                launch { db.bookBrowsingDao().backdrop(book.id, "cover") }
            }
            val expected = BookBrowsing(book.id, "My synopsis", "cover", 11, 13, 12, scene.id)
            assertEquals(expected, db.bookBrowsingDao().get(book.id))
            db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()
            db.close()
            val mediaDir = File(dir, "source-media").apply { mkdirs() }
            File(mediaDir, "cover.png").writeBytes(byteArrayOf(1, 3, 5, 7))
            File(mediaDir, "linked-audio.wav").writeBytes(byteArrayOf(8, 6, 4, 2))
            val archive = File(dir, "full.zip")
            BackupArchives.packMobile(archive, BackupSources(context.getDatabasePath(name), mediaDir = mediaDir,
                datastoreDir = File(context.filesDir, "datastore")), "{}")
            val restoreRoot = File(dir, "restored").apply { mkdirs() }
            val restoreContext = object : ContextWrapper(context) {
                override fun getFilesDir() = restoreRoot
                override fun getDatabasePath(name: String) = File(restoreRoot, name)
            }
            BackupManager(restoreContext, db, settings).restoreFrom(archive)
            db = Room.databaseBuilder(context, WeaverseDatabase::class.java, File(restoreRoot, "weaverse.db").absolutePath).build()
            assertEquals(expected, db.bookBrowsingDao().get(book.id))
            assertEquals(book, db.bookDao().getById(book.id))
            assertEquals(scene, db.manuscriptDao().getScene(scene.id))
            assertEquals(prompt, db.novelWritingDao().draft(scene.id))
            assertEquals(media, db.mediaDao().getById("cover"))
            assertArrayEquals(byteArrayOf(1, 3, 5, 7), File(restoreRoot, "media/cover.png").readBytes())
            assertArrayEquals(byteArrayOf(8, 6, 4, 2), File(restoreRoot, "media/linked-audio.wav").readBytes())
            val savedSettings = File(context.filesDir, "datastore").listFiles().orEmpty().filter { it.isFile }
            assertTrue(savedSettings.isNotEmpty())
            savedSettings.forEach { file -> assertArrayEquals(file.readBytes(), File(restoreRoot, "datastore/${file.name}").readBytes()) }
            assertEquals(3, settings.readerState(book.id).first().paragraphIndex)
            db.bookBrowsingDao().toggleList(book.id, 20)
            assertEquals(0L, db.bookBrowsingDao().get(book.id)!!.listedAt)
            assertEquals("My synopsis", db.bookBrowsingDao().get(book.id)!!.synopsis)
        } finally { db.close(); context.deleteDatabase(name); dir.deleteRecursively() }
    }
}
