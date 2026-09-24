package com.ihy2ln.weaverse.data.db

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Installs "yesterday's build" over a real database and checks the user's rows are
 * still there. The previous schema is rebuilt from the current one by undoing the
 * newest step, so no extra test library is needed. Add a case like this with each
 * version bump.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dbName = "migration-test.db"

    private fun open() = Room.databaseBuilder(context, WeaverseDatabase::class.java, dbName)
        .addMigrations(*WeaverseDatabase.ALL_MIGRATIONS)
        .build()

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    @Test
    fun upgradeFrom28KeepsNovelSettings() {
        context.deleteDatabase(dbName)
        open().apply { openHelper.writableDatabase; close() }

        // Turn the fresh database back into a version-28 one: no startProgress column.
        SQLiteDatabase.openDatabase(context.getDatabasePath(dbName).path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL("ALTER TABLE novel_writing_settings DROP COLUMN startProgress")
            db.execSQL(
                "INSERT INTO novel_writing_settings (bookId, memory, authorNote, modelRef, outputWords, companions) " +
                    "VALUES ('book-1', 'the memory', 'a note', '', 250, 'duo')",
            )
            db.version = 28
        }

        val upgraded = open()
        val row = runBlocking { upgraded.novelWritingDao().settings("book-1") }!!
        upgraded.close()
        assertEquals("the memory", row.memory)
        assertEquals("a note", row.authorNote)
        assertEquals(250, row.outputWords)
        assertEquals("duo", row.companions)
        assertEquals("", row.startProgress)
    }

    @Test
    fun rerunningAnInterruptedUpgradeDoesNotCrash() {
        context.deleteDatabase(dbName)
        open().apply { openHelper.writableDatabase; close() }
        // The column already exists but the version says 28, as after a killed upgrade.
        SQLiteDatabase.openDatabase(context.getDatabasePath(dbName).path, null, SQLiteDatabase.OPEN_READWRITE).use { it.version = 28 }
        open().apply { openHelper.writableDatabase; close() }
    }
}
