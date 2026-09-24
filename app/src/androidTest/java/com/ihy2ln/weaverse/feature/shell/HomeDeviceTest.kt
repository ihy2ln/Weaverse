package com.ihy2ln.weaverse.feature.shell

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.ihy2ln.weaverse.core.ui.theme.WeaverseTheme
import com.ihy2ln.weaverse.core.ui.theme.AppThemeMode
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.data.db.entities.BookEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID
import com.ihy2ln.weaverse.core.ui.components.WorkspaceChrome
import com.ihy2ln.weaverse.core.ui.components.SegmentedOption

class HomeDeviceTest {
    @get:Rule val compose = createComposeRule()
    private fun item() = HomeItem("Novel", "book", "fixture", 10, "", "The Midnight Archive", null, null, "fixture", null)

    @Test fun emptyModesRemainReachable() {
        var opened: AppMode? = null
        compose.setContent { WeaverseTheme(themeMode = AppThemeMode.Dark) {
            HomeShelves(AppMode.entries, emptyMap(), emptyMap(), { opened = it }, {}, {}, {}, Modifier.fillMaxSize())
        } }
        AppMode.entries.forEach { mode ->
            compose.onNodeWithTag("home-feed").performScrollToKey(mode.name)
            compose.waitForIdle()
            compose.onNodeWithText("Explore ${mode.label}").assertIsDisplayed().performClick()
            compose.runOnIdle { assertEquals(mode, opened) }
        }
    }

    @Test fun populatedShelvesRenderWithLargeTextAndMissingArt() {
        compose.setContent {
            val density = androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, 1.4f)) {
                WeaverseTheme(themeMode = AppThemeMode.Dark) {
                    HomeShelves(AppMode.entries, AppMode.entries.associate { mode -> mode.name to (1..10).map { i -> item().copy(mode = mode.name, contentId = "${mode.name}-$i", title = if (i == 1) "The Midnight Archive — A Very Long Title" else "World $i") } }, emptyMap(), {}, {}, {}, {}, Modifier.fillMaxSize())
                }
            }
        }
        compose.onNodeWithText("Welcome home").assertIsDisplayed()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        java.io.File(context.getExternalFilesDir(null), "home-large-text.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun cardsOpenAndHistoryActionsDoNotDeleteContent() {
        var opened: HomeItem? = null
        var removed: HomeItem? = null
        var cleared: String? = null
        val entry = item()
        compose.setContent { WeaverseTheme(themeMode = AppThemeMode.Dark) {
            HomeShelves(listOf(AppMode.Novel), mapOf("Novel" to listOf(entry)), emptyMap(), {}, { opened = it }, { removed = it }, { cleared = it }, Modifier.fillMaxSize())
        } }
        compose.onNodeWithText(entry.title).performClick()
        compose.runOnIdle { assertEquals(entry, opened) }
        compose.onNodeWithContentDescription("Options for ${entry.title}").performClick()
        compose.onNodeWithText("Remove from recents").performClick()
        compose.runOnIdle { assertEquals(entry, removed) }
        compose.onNodeWithContentDescription("Novel history options").performClick()
        compose.onNodeWithText("Clear recent history").performClick()
        compose.runOnIdle { assertNull(cleared) }
        compose.onNodeWithText("Clear history").performClick()
        compose.runOnIdle { assertEquals("Novel", cleared) }
    }
    @Test fun persistenceDeduplicationRemovalAndDeletedTargets() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "home-history-${UUID.randomUUID()}.db"
        fun open() = Room.databaseBuilder(context, WeaverseDatabase::class.java, name).build()
        var db = open()
        try {
            db.bookDao().upsert(BookEntity("book", null, "Original", createdAt = 1, updatedAt = 1))
            val history = HomeHistory(db)
            history.record("Novel", "book", "book", "scene-a")
            history.record("Novel", "book", "book")
            history.record("Novel", "book", "deleted")
            val items = history.items.first()
            assertEquals(1, items.size)
            assertEquals("scene-a", items.single().target)
            db.close(); db = open()
            assertEquals("Original", db.homeAccessDao().observeItems().first().single().title)
            HomeHistory(db).remove(items.single())
            assertTrue(db.homeAccessDao().observeItems().first().isEmpty())
            assertNotNull(db.bookDao().getById("book"))
            HomeHistory(db).record("Novel", "book", "book")
            assertEquals(1, db.homeAccessDao().observeItems().first().size)
            HomeHistory(db).clear("Novel")
            assertTrue(db.homeAccessDao().observeItems().first().isEmpty())
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun concurrentAccessKeepsResumePosition() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, WeaverseDatabase::class.java).build()
        try {
            val history = HomeHistory(db)
            repeat(20) { index ->
                history.clear("Novel")
                coroutineScope {
                    launch(Dispatchers.Default) { history.record("Novel", "book", "book", "scene-$index") }
                    repeat(8) { launch(Dispatchers.Default) { history.record("Novel", "book", "book") } }
                }
                assertEquals("scene-$index", db.homeAccessDao().get("Novel", "book", "book")?.target)
            }
        } finally { db.close() }
    }

    @Test fun upgradePreservesBooksAndStartsWithEmptyHistory() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "home-migration-${UUID.randomUUID()}.db"
        var db = Room.databaseBuilder(context, WeaverseDatabase::class.java, name).build()
        try {
            db.bookDao().upsert(BookEntity("retained", null, "Keep this book", createdAt = 1, updatedAt = 1))
            db.openHelper.writableDatabase.execSQL("DROP TABLE home_access")
            db.openHelper.writableDatabase.version = 24
            db.close()
            db = Room.databaseBuilder(context, WeaverseDatabase::class.java, name)
                .addMigrations(WeaverseDatabase.MIGRATION_24_25, WeaverseDatabase.MIGRATION_25_26).build()
            assertEquals("Keep this book", db.bookDao().getById("retained")?.title)
            assertTrue(db.homeAccessDao().observeItems().first().isEmpty())
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun unifiedMenuRoutesHomeModeAndSection() {
        var destination = ""
        compose.setContent { WeaverseTheme(themeMode = AppThemeMode.Dark) {
            WorkspaceChrome(bookTitle = "Book", seriesTitle = "", workspaceOptions = AppMode.entries.map { SegmentedOption(it.name, it.label) },
                workspaceId = "Novel", modeOptions = listOf(SegmentedOption("Read", "Read")), modeId = "Read",
                focusOptions = emptyList(), focusId = "", toolOptions = emptyList(), activeToolId = null,
                onHome = { destination = "Home" }, onLibrary = {}, onSettings = {}, onImport = {}, onExport = {}, onTool = {},
                onWorkspace = { destination = it }, onMode = { destination = it }, onFocus = {})
        } }
        compose.onNodeWithContentDescription("Open navigation").performClick()
        compose.onNodeWithText("Home").performClick()
        compose.runOnIdle { assertEquals("Home", destination) }
        compose.onNodeWithContentDescription("Open navigation").performClick()
        compose.onNodeWithText("RPG").performClick()
        compose.runOnIdle { assertEquals("Roleplay", destination) }
        compose.onNodeWithContentDescription("Open navigation").performClick()
        compose.onNodeWithText("Read").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("Read", destination) }
    }
}

