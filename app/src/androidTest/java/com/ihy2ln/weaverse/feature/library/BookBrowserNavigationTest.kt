package com.ihy2ln.weaverse.feature.library

import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.ihy2ln.weaverse.MainActivity
import com.ihy2ln.weaverse.BrowserQaEntryPoint
import com.ihy2ln.weaverse.core.text.*
import com.ihy2ln.weaverse.data.db.entities.*
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class BookBrowserNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun captureTheme(name: String) {
        compose.waitForIdle()
        val file = java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "theme-$name.png")
        file.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun actualReaderWriterResumeAndReturnToOriginWithoutDuplicateNavigation() {
        val entry = EntryPointAccessors.fromApplication(InstrumentationRegistry.getInstrumentation().targetContext, BrowserQaEntryPoint::class.java)
        val db = entry.database()
        val settings = entry.settings()
        val id = "browser-navigation-fixture"
        val doc = Document((1..30).map { Paragraph("paragraph-$it", listOf(Span("Paragraph $it of the navigation test. A story that stays exactly as written."))) }).toJson()
        val previous = runBlocking { settings.preferences.first().selectedBookId }
        runBlocking {
            db.bookDao().upsert(BookEntity(id, null, "Navigation Verification Book", createdAt = 100, updatedAt = 100))
            db.manuscriptDao().upsertAct(ActEntity("$id-act", id, "Act", 0))
            db.manuscriptDao().upsertChapter(ChapterEntity("$id-chapter", "$id-act", "Chapter", 0))
            (1..2).forEach { n -> db.manuscriptDao().upsertScene(SceneEntity("$id-scene-$n", "$id-chapter", "Verification scene $n", n, doc, "Saved prose", createdAt = 1, updatedAt = 1)) }
            settings.setReaderScroll(id, "$id-scene-2", 2, 15)
            db.bookBrowsingDao().write(id, "$id-scene-1", 1)
        }
        try {
            compose.waitUntil(15000) { compose.onAllNodesWithTag("book-browser").fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(hasContentDescription("Search") and hasAnyAncestor(hasTestTag("browse-navigation")), useUnmergedTree = true).performClick()
            compose.onNodeWithTag("book-search").performTextInput("Navigation Verification")
            compose.waitUntil { compose.onAllNodesWithTag("book-cover:$id").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("book-cover:$id").performClick()
            assertEquals(0L, runBlocking { db.bookBrowsingDao().get(id)!!.readAt })
            compose.onNodeWithText("Continue reading").performScrollTo().performClick()
            compose.waitUntil(15000) { compose.onAllNodesWithTag("book-browser").fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithTag("browse-navigation").assertDoesNotExist()
            compose.waitUntil(15000) { runBlocking { db.bookBrowsingDao().get(id)!!.readAt > 0 } }
            assertEquals("$id-scene-2", runBlocking { settings.readerState(id).first().lastSceneId })
            captureTheme("reader")
            InstrumentationRegistry.getInstrumentation().runOnMainSync { compose.activity.onBackPressedDispatcher.onBackPressed() }
            compose.waitUntil { compose.onAllNodesWithTag("book-details").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("book-details").performScrollToIndex(1)
            compose.onNodeWithText("Continue writing").performScrollTo().performClick()
            compose.waitUntil(15000) { runBlocking { db.bookBrowsingDao().get(id)!!.writeAt > 1 } }
            assertEquals("$id-scene-1", runBlocking { db.bookBrowsingDao().get(id)!!.writeSceneId })
            compose.onNodeWithTag("browse-navigation").assertDoesNotExist()
            compose.onNodeWithContentDescription("Open navigation").assertDoesNotExist()
            captureTheme("writer")
            InstrumentationRegistry.getInstrumentation().runOnMainSync { compose.activity.onBackPressedDispatcher.onBackPressed() }
            compose.waitUntil { compose.onAllNodesWithTag("book-details").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Back").performClick()
            compose.onNodeWithTag("book-search").assertTextContains("Navigation Verification")
            compose.activityRule.scenario.recreate()
            compose.onNodeWithTag("book-search").assertTextContains("Navigation Verification")
            assertEquals(doc, runBlocking { db.manuscriptDao().getScene("$id-scene-1")!!.docJson })
            assertEquals(doc, runBlocking { db.manuscriptDao().getScene("$id-scene-2")!!.docJson })
        } finally {
            runBlocking {
                db.bookDao().deleteById(id)
                db.bookBrowsingDao().delete(id)
                db.homeAccessDao().remove("Novel", "book", id)
                settings.setSelectedBookId(previous)
            }
        }
    }
}
