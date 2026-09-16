package com.ihy2ln.weaverse.feature.novel

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.data.db.entities.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class NovelWorkspaceDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun writingMigrationAndBackupKeepDraftCandidatesAndExistingData() = runBlocking {
        val name = "novel-writing-${UUID.randomUUID()}.db"
        val restoredName = "novel-restored-${UUID.randomUUID()}.db"
        val archive = java.io.File(context.cacheDir, "$name.zip")
        var db = Room.databaseBuilder(context, WeaverseDatabase::class.java, name).build()
        try {
            val book = BookEntity("book", null, "Existing book", styleGuide = "Existing style", createdAt = 1, updatedAt = 1)
            db.bookDao().upsert(book)
            val original = SceneEntity("scene", "chapter", "Saved scene", 0, "{\"blocks\":[]}", "Saved prose", createdAt = 1, updatedAt = 1)
            db.manuscriptDao().upsertScene(original)
            val link = NovelMediaLink("media-link", "book", "scene", mediaId = "existing-art", createdAt = 1)
            db.novelMediaDao().upsert(link)
            db.openHelper.writableDatabase.execSQL("DROP TABLE novel_prompt_drafts")
            db.openHelper.writableDatabase.execSQL("DROP TABLE novel_writing_settings")
            db.openHelper.writableDatabase.version = 23
            db.close()
            db = Room.databaseBuilder(context, WeaverseDatabase::class.java, name).addMigrations(WeaverseDatabase.MIGRATION_23_24).build()
            assertEquals(book, db.bookDao().getById("book"))
            assertEquals(original, db.manuscriptDao().getScene("scene"))
            assertEquals(link, db.novelMediaDao().observe("book").first().single())
            val draft = NovelPromptDraft("scene", """{"targetSceneId":"scene","prompt":"Saved instruction","streamingText":"Candidate","candidates":[{"streamingText":"Previous"}]}""")
            val settings = NovelWritingSettings("book", "Memory", "Note", "openai/test", 300)
            db.novelWritingDao().saveDraft(draft)
            db.novelWritingDao().saveSettings(settings)
            db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()
            db.close()
            com.ihy2ln.weaverse.data.backup.BackupArchives.packMobile(archive,
                com.ihy2ln.weaverse.data.backup.BackupSources(context.getDatabasePath(name)), "{}")
            java.util.zip.ZipFile(archive).use { zip ->
                zip.getInputStream(zip.getEntry("weaverse.db")).use { input ->
                    context.getDatabasePath(restoredName).outputStream().use { input.copyTo(it) }
                }
            }
            db = Room.databaseBuilder(context, WeaverseDatabase::class.java, restoredName).build()
            assertEquals(draft, db.novelWritingDao().draft("scene"))
            assertEquals(settings, db.novelWritingDao().settings("book"))
            assertEquals(book, db.bookDao().getById("book"))
            assertEquals(original, db.manuscriptDao().getScene("scene"))
            assertEquals(link, db.novelMediaDao().observe("book").first().single())
        } finally { db.close(); context.deleteDatabase(name); context.deleteDatabase(restoredName); archive.delete() }
    }

    @Test fun styledCaretStateCanBeParceledForDocumentPicker() {
        val value = androidx.compose.ui.text.input.TextFieldValue(
            androidx.compose.ui.text.AnnotatedString("Styled prose", androidx.compose.ui.text.SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Normal)),
            androidx.compose.ui.text.TextRange(3, 8),
        )
        val saver = com.ihy2ln.weaverse.feature.novel.write.editor.NovelTextFieldSaver
        val saved = with(saver) { androidx.compose.runtime.saveable.SaverScope { true }.save(value) }
        val parcel = android.os.Parcel.obtain()
        try {
            parcel.writeValue(saved)
            parcel.setDataPosition(0)
            val restored = saver.restore(parcel.readValue(javaClass.classLoader)!!)
            assertEquals(value.text, restored!!.text)
            assertEquals(value.selection, restored.selection)
        } finally { parcel.recycle() }
    }
    @Test fun migrationKeepsManuscriptAndReferencesStaySeparate() = runBlocking {
        val name = "novel-migration-${UUID.randomUUID()}.db"
        var db = Room.databaseBuilder(context, WeaverseDatabase::class.java, name).build()
        try {
            val scene = SceneEntity("scene", "chapter", "Test scene", 0, "{\"blocks\":[]}", "Original prose", createdAt = 1, updatedAt = 1)
            db.manuscriptDao().upsertAct(ActEntity("act", "book", "Act", 0))
            db.manuscriptDao().upsertChapter(ChapterEntity("chapter", "act", "Chapter", 0))
            db.manuscriptDao().upsertScene(scene)
            db.openHelper.writableDatabase.execSQL("DROP TABLE novel_media_links")
            db.openHelper.writableDatabase.version = 22
            db.close()
            db = Room.databaseBuilder(context, WeaverseDatabase::class.java, name)
                .addMigrations(WeaverseDatabase.MIGRATION_22_23, WeaverseDatabase.MIGRATION_23_24).build()
            assertEquals(scene, db.manuscriptDao().getScene("scene"))
            val link = NovelMediaLink("link", "book", "scene", mediaId = "art", caption = "Portrait", altText = "A reference portrait", createdAt = 2)
            db.novelMediaDao().upsert(link)
            assertEquals(listOf(link), db.novelMediaDao().observe("book").first())
            assertTrue(db.novelMediaDao().observe("another-book").first().isEmpty())
            assertEquals("Original prose", db.manuscriptDao().getScene("scene")!!.plainText)
            db.novelMediaDao().pin(SceneCodexLinkEntity("scene", "character", updatedAt = 3))
            assertEquals(listOf("character"), db.novelMediaDao().contextIds("scene"))
            db.close()
            db = Room.databaseBuilder(context, WeaverseDatabase::class.java, name).build()
            assertEquals(link, db.novelMediaDao().observe("book").first().single())
            db.novelMediaDao().remove("link", "another-book")
            assertEquals(1, db.novelMediaDao().observe("book").first().size)
            db.novelMediaDao().remove("link", "book")
            assertTrue(db.novelMediaDao().observe("book").first().isEmpty())
            assertEquals(scene, db.manuscriptDao().getScene("scene"))
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
