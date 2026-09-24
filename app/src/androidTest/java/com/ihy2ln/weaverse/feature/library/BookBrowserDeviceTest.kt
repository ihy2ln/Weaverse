package com.ihy2ln.weaverse.feature.library

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.ihy2ln.weaverse.core.media.MediaRepository
import com.ihy2ln.weaverse.core.ui.components.*
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.data.db.entities.*
import com.ihy2ln.weaverse.data.repo.BookRepository
import com.ihy2ln.weaverse.data.settings.SettingsRepository
import com.ihy2ln.weaverse.feature.shell.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Genuine Compose renders with isolated test-library fixtures. Never adds sample titles to the user's library. */
class BookBrowserDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: WeaverseDatabase
    private var opened = ""
    /** Books lost its nav slot; Home's Books shelf is the way in. */
    private fun openBooksShelf() {
        compose.onNodeWithTag("home-feed").performScrollToIndex(1)
        compose.onNode(hasText("See all") and hasAnyAncestor(hasTestTag("shelf-heading:Books"))).performClick()
    }

    private fun start(populated: Boolean = true, large: Boolean = false, landscape: Boolean = false) {
        compose.activityRule.scenario.onActivity { it.requestedOrientation = if (landscape) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        compose.waitUntil(10000) { compose.activity.resources.configuration.orientation == if (landscape) android.content.res.Configuration.ORIENTATION_LANDSCAPE else android.content.res.Configuration.ORIENTATION_PORTRAIT }
        db = Room.inMemoryDatabaseBuilder(context, WeaverseDatabase::class.java).build()
        val media = MediaRepository(context, db)
        val settings = SettingsRepository(context, com.ihy2ln.weaverse.data.settings.SecureKeyStore(context))
        val history = HomeHistory(db)
        if (populated) runBlocking {
            val titles = listOf("The Midnight Archive", "A World Beyond the Sea", "The Last Light", "Letters from Tomorrow", "The Long Road Home", "A Map of Impossible Things", "Uncategorized Story — A Very Long Title Across the Edge of the Known World", "The Moonlit Garden", "The Secret Library", "Echoes", "An Extra Book")
            titles.forEachIndexed { i, title ->
                val id = "stream-fixture-$i"
                var art: String? = null
                if (i < 6) {
                    val paths = listOf("locations/arcanis.png", "locations/aqualuria.png", "locations/elysium-vale.png", "locations/adams-haven.png", "locations/elysara.png", "locations/california.png")
                    val file = File(context.cacheDir, "streaming-art-$i.png")
                    context.assets.open("images/adams_haven/${paths[i]}").use { input -> file.outputStream().use { input.copyTo(it) } }
                    art = media.importFromFile(file, "image/png").id
                }
                db.bookDao().upsert(BookEntity(id, null, title, genre = if (i == 6) "" else if (i % 2 == 0) "Fantasy" else "Adventure", coverMediaId = art, createdAt = 100L - i, updatedAt = 1))
                db.manuscriptDao().upsertAct(ActEntity("act-$i", id, "Act one", 0))
                db.manuscriptDao().upsertChapter(ChapterEntity("chapter-$i", "act-$i", "Chapter one", 0))
                db.manuscriptDao().upsertScene(SceneEntity("scene-$i", "chapter-$i", "An unexpected visitor", 0, "{\"blocks\":[]}", "A story begins.", createdAt = 1, updatedAt = 1))
                db.homeAccessDao().record(HomeAccess("Novel", "book", id, 100L - i))
                if (i < 3) { db.bookBrowsingDao().read(id, 100L - i); settings.setReaderScroll(id, "scene-$i", 0, 0) }
                if (i in 2..5) db.bookBrowsingDao().write(id, "scene-$i", 100L - i)
                if (i in 1..4) db.bookBrowsingDao().toggleList(id, 100L - i)
            }
            val audioFile = File(context.cacheDir, "streaming-reference.wav")
            val pcmSize = 16000
            val wav = java.nio.ByteBuffer.allocate(44 + pcmSize).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            wav.put("RIFF".toByteArray()).putInt(36 + pcmSize).put("WAVEfmt ".toByteArray()).putInt(16)
                .putShort(1.toShort()).putShort(1.toShort()).putInt(8000).putInt(16000).putShort(2.toShort()).putShort(16.toShort())
                .put("data".toByteArray()).putInt(pcmSize)
            audioFile.writeBytes(wav.array())
            val audioAsset = media.importFromFile(audioFile, "audio/wav")
            db.mediaDao().upsert(audioAsset.copy(displayName = "Scene audio reference"))
            db.novelMediaDao().upsert(NovelMediaLink("stream-audio", "stream-fixture-0", "scene-0", mediaId = audioAsset.id, createdAt = 1))
            db.bookBrowsingDao().synopsis("stream-fixture-0", "Behind a forgotten door, a city keeps the stories the world has lost. One reader is about to change the ending.")
        }
        val model = BookBrowserViewModel(db, settings, media, BookRepository(db), history)
        val home = HomeViewModel(history, media)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if (large) 1.6f else 1f)) {
                var routes by rememberSaveable { mutableStateOf(listOf("home")) }
                BookBrowsingTheme {
                    Column(Modifier.width(if (landscape) 720.dp else 360.dp).fillMaxHeight()) {
                        WorkspaceChrome(bookTitle = "", seriesTitle = "", workspaceOptions = AppMode.entries.map { SegmentedOption(it.name, it.label) }, workspaceId = "Novel",
                            modeOptions = emptyList(), modeId = "", focusOptions = emptyList(), focusId = "", toolOptions = emptyList(), activeToolId = null,
                            isHome = true, browsing = true, canGoBack = routes.size > 1, onBack = { routes = routes.dropLast(1) },
                            onHome = { routes = listOf("home") }, onLibrary = {}, onSettings = {}, onImport = {}, onExport = {}, onTool = {}, onWorkspace = { opened = it }, onMode = {}, onFocus = {})
                        BookBrowserScreen(routes, { routes = it }, AppMode.entries, { opened = it.name }, {},
                            { opened = "read:$it" }, { opened = "write:$it" }, {}, {}, {}, {}, Modifier.weight(1f), model, home)
                    }
                }
            }
        }
        compose.waitUntil(15000) { compose.onAllNodesWithTag("home-feed").fetchSemanticsNodes().isNotEmpty() }
        if (populated) compose.waitUntil(15000) { compose.onAllNodesWithText("The Midnight Archive").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        Thread.sleep(900) // Wait for asynchronous local-image decoding before capturing the rendered frame.
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(context.getExternalFilesDir(null), "streaming-$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun homeDetailsListSearchAndReturnState() {
        start()
        capture("home-360")
        compose.onNodeWithText("Details").performScrollTo().performClick()
        compose.onNodeWithTag("book-details").assertExists().performScrollToIndex(1)
        compose.onNodeWithText("Add to My List").performScrollTo().performClick()
        compose.waitUntil { runBlocking { db.bookBrowsingDao().get("stream-fixture-0")?.listedAt ?: 0 } > 0 }
        capture("details-360")
        compose.onNodeWithText("Edit synopsis").performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("A user-authored synopsis.")
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil { runBlocking { db.bookBrowsingDao().get("stream-fixture-0")?.synopsis } == "A user-authored synopsis." }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithTag("home-feed").assertExists()
        openBooksShelf()
        compose.onNodeWithTag("books-feed").assertExists()
        capture("books-360")
        compose.onNodeWithText("All books").performClick()
        compose.onNodeWithTag("book-search").performTextInput("Uncategorized")
        compose.onNodeWithTag("book-cover:stream-fixture-6").assertIsDisplayed().performClick()
        compose.onNodeWithText("Read", substring = false).performScrollTo().performClick()
        assertEquals("read:stream-fixture-6", opened)
        compose.onNodeWithText("Write", substring = false).performScrollTo().performClick()
        assertEquals("write:stream-fixture-6", opened)
        assertEquals(0L, runBlocking { db.bookBrowsingDao().get("stream-fixture-6")?.writeAt ?: 0 })
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithTag("book-search").assertTextContains("Uncategorized")
        capture("search")
        compose.onNode(hasContentDescription("My List") and hasAnyAncestor(hasTestTag("browse-navigation")), useUnmergedTree = true).performClick()
        compose.onNodeWithTag("book-cover:stream-fixture-0").assertExists()
        capture("my-list")
    }
    @Test fun emptyLibraryAndEveryModeRemainReachable() {
        start(false)
        capture("empty")
        AppMode.entries.filter { it != AppMode.Novel }.forEach { mode ->
            compose.onNodeWithTag("home-feed").performScrollToKey(mode.name)
            compose.onNodeWithText("Explore ${mode.label}").performClick()
            assertEquals(mode.name, opened)
        }
    }
    @Test fun largerTextActionsAndMissingCovers() {
        start(large = true)
        compose.onNodeWithText("Details").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag("book-details").performScrollToIndex(1)
        compose.onNodeWithText("Add to My List").performScrollTo().assertIsDisplayed()
        capture("large-text")
    }
    @Test fun shelvesScrollHorizontallyAndRestoreOnBack() {
        start()
        compose.onNodeWithTag("home-feed").performScrollToIndex(1)
        compose.onNodeWithTag("shelf:Books").performScrollToIndex(7)
        compose.onNodeWithTag("book-cover:stream-fixture-7").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithTag("book-cover:stream-fixture-8").assertIsDisplayed()
        capture("shelf-scroll")
    }
    @Test fun landscapeFeaturedAndNavigation() {
        compose.activityRule.scenario.onActivity { it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.waitUntil(10000) { compose.activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE }
        start(landscape = true)
        capture("landscape")
        compose.onNodeWithText("Details").performScrollTo().performClick()
        compose.onNodeWithTag("book-details").assertExists()
        capture("landscape-details")
    }
    @Test fun everyBookShelfSeeAllAndMissingCover() {
        start()
        openBooksShelf()
        listOf("reading", "writing", "list", "added", "genre:Fantasy", "genre:Adventure").forEach { id ->
            compose.onNodeWithTag("books-feed").performScrollToKey(id)
            compose.onNode(hasText("See all") and hasAnyAncestor(hasTestTag("shelf-heading:${shelfTitle(id)}"))).performClick()
            compose.onNodeWithTag("book-search").assertExists()
            compose.onNodeWithContentDescription("Back").performClick()
        }
        compose.onNode(hasContentDescription("Search") and hasAnyAncestor(hasTestTag("browse-navigation")), useUnmergedTree = true).performClick()
        compose.onNodeWithTag("book-search").performTextInput("Uncategorized")
        compose.onNodeWithTag("book-cover:stream-fixture-6").performClick()
        compose.onNodeWithText("Read", substring = false).performScrollTo().assertIsDisplayed()
        capture("missing-cover-long-title")
    }

    @Test fun linkedAudioIsExplicitlyLabelledAndDoesNotAutoplay() {
        start()
        compose.onNodeWithText("Details").performScrollTo().performClick()
        compose.onNodeWithTag("book-details").performScrollToIndex(1)
        compose.waitUntil(10000) { compose.onAllNodesWithText("Linked audio · 1 file").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Linked audio · 1 file").performScrollTo().performClick()
        compose.onNodeWithText("Book-linked audio").assertIsDisplayed()
        compose.onNodeWithText("Play · Scene audio reference", substring = true).assertIsDisplayed()
        compose.onAllNodesWithText("Stop ·", substring = true).assertCountEquals(0)
        compose.onNodeWithText("Play · Scene audio reference", substring = true).performClick()
        compose.onNodeWithText("Stop · Scene audio reference", substring = true).assertExists()
        compose.onNodeWithText("Close", substring = false).performClick()
    }

}
