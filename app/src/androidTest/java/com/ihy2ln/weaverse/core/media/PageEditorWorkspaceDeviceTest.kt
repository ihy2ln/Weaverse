package com.ihy2ln.weaverse.core.media

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.ihy2ln.weaverse.core.text.TextOverlay
import com.ihy2ln.weaverse.core.text.TextOverlayStyle
import com.ihy2ln.weaverse.feature.roleplay.chat.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class PageEditorWorkspaceDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun paintCanBeUndoneImmediatelyWithoutLeavingCanvas() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "workspace-undo.png")
        val original = Bitmap.createBitmap(240, 320, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        file.outputStream().use { original.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val editor = mutableStateOf(PanelEditorUi("test", "test", "test", file.absolutePath))
        var saved: Bitmap? = null
        compose.setContent { MaterialTheme {
            PanelImageEditor(editor.value, emptyList(), emptyList(), "", "", {}, {},
                onSave = { saved = it.copy(Bitmap.Config.ARGB_8888, false) }, onClose = {},
                onRunPipeline = { fail("Opening editing tools must not call AI") }, onSetLanguage = {},
                onUpdateRegions = { editor.value = editor.value.copy(regions = it) },
                onSelectRegion = { editor.value = editor.value.copy(selectedRegionId = it) }, onConsumeCleanup = {})
        } }
        compose.onNodeWithText("Paint").performClick()
        compose.onNodeWithContentDescription("Black").assertIsDisplayed().performClick()
        compose.onNodeWithTag("inline-brush-size").assertIsDisplayed()
        compose.onNodeWithContentDescription("Page being edited").performTouchInput {
            swipe(Offset(width * .3f, height * .5f), Offset(width * .7f, height * .5f), 400)
        }
        compose.onNodeWithText("Save").performClick()
        compose.runOnIdle { assertNotNull(saved); assertFalse(original.sameAs(saved)); saved?.recycle() }
        compose.onNodeWithText("Undo").assertIsEnabled().performClick()
        compose.onNodeWithText("Save").performClick()
        compose.runOnIdle { assertTrue("Undo must restore every source pixel", original.sameAs(saved)); saved?.recycle() }
        compose.onNodeWithText("Redo").assertIsEnabled().performClick()
        compose.onNodeWithText("Save").performClick()
        compose.runOnIdle { assertFalse(original.sameAs(saved)); saved?.recycle() }
    }

    @Test fun existingTranslationMovesAndUndoesInsidePageEditor() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "workspace-text.png")
        val image = Bitmap.createBitmap(240, 320, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val initial = PanelTextRegion(.25f, .3f, .3f, .2f, "", "Existing translation", id = "existing", cleanupX = .2f, cleanupY = .2f, cleanupW = .2f, cleanupH = .1f)
        val editor = mutableStateOf(PanelEditorUi("test", "test", "test", file.absolutePath, regions = listOf(initial)))
        compose.setContent { MaterialTheme {
            PanelImageEditor(editor.value, emptyList(), emptyList(), "", "", {}, {}, {}, {}, {}, {},
                onUpdateRegions = { editor.value = editor.value.copy(regions = it) },
                onSelectRegion = { editor.value = editor.value.copy(selectedRegionId = it) }, onConsumeCleanup = {})
        } }
        compose.onNodeWithTag("overlay-existing").performTouchInput { click(center) }
        compose.onNodeWithTag("overlay-existing-move", useUnmergedTree = true).performTouchInput {
            swipe(center, center + Offset(40f, 35f), 400)
        }
        compose.runOnIdle {
            val moved = editor.value.regions.single()
            assertTrue(moved.x > initial.x); assertTrue(moved.y > initial.y)
            assertEquals(initial.cleanupX, moved.cleanupX); assertEquals(initial.cleanupW, moved.cleanupW)
        }
        compose.onNodeWithText("Undo").performClick()
        compose.runOnIdle { assertEquals(initial, editor.value.regions.single()) }
    }

    @Test fun oneHistoryRestoresPaintMaskAndTextInOrder() {
        val image = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val mask = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        val initial = listOf(PanelTextRegion(.1f, .1f, .2f, .2f, "", "Hello", id = "text"))
        val moved = initial.map { it.copy(x = .5f) }
        val history = PageEditHistory(image, mask)
        history.checkpoint(initial); image.setPixel(4, 4, Color.RED)
        history.checkpoint(initial); mask.setPixel(5, 5, Color.BLUE)
        history.checkpoint(initial)
        assertEquals(initial, history.undo(moved))
        assertEquals(Color.BLUE, mask.getPixel(5, 5))
        assertEquals(initial, history.undo(initial))
        assertEquals(Color.TRANSPARENT, mask.getPixel(5, 5))
        assertEquals(Color.RED, image.getPixel(4, 4))
        history.undo(initial); assertEquals(Color.WHITE, image.getPixel(4, 4))
        history.redo(initial); assertEquals(Color.RED, image.getPixel(4, 4))
        history.checkpoint(initial); assertFalse(history.canRedo)
        history.dispose()
    }

    @Test fun manualAndTranslatedLayersRetainIdentityAndStyle() {
        val manual = TextOverlay("manual", "Added caption", style = TextOverlayStyle.SpeechBubble,
            backgroundHex = "#123456", backgroundAlpha = .7f, tailAngleDeg = 35f, source = "", cleanupEnabled = false)
        assertEquals("Untouched legacy overlays must round-trip exactly", manual, manual.toPanelTextRegion().toEditableOverlay())
        val converted = manual.toPanelTextRegion().copy(x = .2f).toEditableOverlay()
        assertEquals(manual.id, converted.id); assertEquals(manual.source, converted.source)
        assertEquals(manual.style, converted.style); assertEquals(manual.backgroundHex, converted.backgroundHex)
        assertEquals(manual.backgroundAlpha, converted.backgroundAlpha); assertEquals(manual.tailAngleDeg, converted.tailAngleDeg)
        assertFalse(converted.cleanupEnabled)
        val translated = manual.copy(id = "translation", source = "manga-translation")
        assertEquals("manga-translation", translated.toPanelTextRegion().toEditableOverlay().source)
    }
}
