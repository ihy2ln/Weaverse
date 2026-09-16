package com.ihy2ln.weaverse.core.media

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.ihy2ln.weaverse.core.text.TextOverlay
import com.ihy2ln.weaverse.core.ui.components.TextOverlayLayer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class OverlayGestureDeviceTest {
    @get:Rule val compose = createComposeRule()
    @Test fun readingDisablesEditingAndBackClearsHandles() {
        val editing = mutableStateOf(true)
        val reset = mutableStateOf(0)
        var moves = 0
        compose.setContent {
            MaterialTheme { Box(Modifier.size(300.dp, 400.dp)) {
                TextOverlayLayer(listOf(TextOverlay(id = "mode", text = "Dialogue", xPercent = 50f,
                    yPercent = 50f, widthPercent = 50f, heightPercent = 30f)), editing.value,
                    onMove = { _, _, _ -> moves++ }, onResize = { _, _, _, _, _ -> }, onTap = {},
                    selectionResetKey = reset.value)
            } }
        }
        compose.onNodeWithTag("overlay-mode").performTouchInput { click(center) }
        compose.onNodeWithTag("overlay-mode-move", useUnmergedTree = true).assertExists()
        compose.runOnIdle { reset.value++ }
        compose.onNodeWithTag("overlay-mode-move", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("overlay-mode").performTouchInput { click(center) }
        compose.runOnIdle { editing.value = false }
        compose.onNodeWithTag("overlay-mode-move", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("overlay-mode").performTouchInput { swipe(center, center + Offset(40f, 30f), 400) }
        compose.runOnIdle { assertEquals(0, moves); editing.value = true }
        compose.onNodeWithTag("overlay-mode-move", useUnmergedTree = true).assertDoesNotExist()
    }
    @Test fun bodyDragPersistsAndSideResizeLeavesHeightAndCleanupAloneAtZoom() {
        val overlay = mutableStateOf(TextOverlay(id = "fixture", text = "Move this text", xPercent = 50f,
            yPercent = 50f, widthPercent = 45f, heightPercent = 35f, autoFit = true,
            cleanupXPercent = 10f, cleanupYPercent = 12f, cleanupWidthPercent = 20f, cleanupHeightPercent = 25f))
        compose.setContent {
            MaterialTheme { Box(Modifier.size(300.dp, 400.dp).graphicsLayer { scaleX = 1.25f; scaleY = 1.25f }) {
                TextOverlayLayer(listOf(overlay.value), true,
                    onMove = { _, x, y -> overlay.value = overlay.value.copy(xPercent = x, yPercent = y) },
                    onResize = { _, x, y, w, h -> overlay.value = overlay.value.copy(xPercent = x, yPercent = y, widthPercent = w, heightPercent = h) },
                    onTap = {}, modifier = Modifier.testTag("overlay-layer"))
            } }
        }
        compose.onNodeWithTag("overlay-fixture").performTouchInput { swipe(center, center + Offset(45f, 20f), 500) }
        compose.runOnIdle { assertTrue("Drag did not persist: ${overlay.value}", overlay.value.xPercent > 50f && overlay.value.yPercent > 50f) }
        val beforeMove = overlay.value
        compose.onNodeWithTag("overlay-fixture-move", useUnmergedTree = true)
            .performTouchInput { swipe(center, center + Offset(30f, 35f), 500) }
        compose.runOnIdle {
            assertTrue(overlay.value.xPercent > beforeMove.xPercent)
            assertTrue(overlay.value.yPercent > beforeMove.yPercent)
            assertEquals(beforeMove.widthPercent, overlay.value.widthPercent, .001f)
            assertEquals(beforeMove.heightPercent, overlay.value.heightPercent, .001f)
            assertEquals(beforeMove.cleanupXPercent, overlay.value.cleanupXPercent)
            assertEquals(beforeMove.cleanupHeightPercent, overlay.value.cleanupHeightPercent)
        }
        val before = overlay.value
        compose.onNodeWithTag("overlay-fixture-handle-1-0", useUnmergedTree = true)
            .performTouchInput { swipe(center, center + Offset(90f, 45f), 500) }
        compose.runOnIdle {
            assertTrue("Side resize failed: before=$before after=${overlay.value}", overlay.value.widthPercent > before.widthPercent)
            assertEquals(before.heightPercent, overlay.value.heightPercent, .001f)
            assertEquals(before.xPercent - before.widthPercent / 2,
                overlay.value.xPercent - overlay.value.widthPercent / 2, .02f)
            assertEquals(10f, overlay.value.cleanupXPercent)
            assertEquals(25f, overlay.value.cleanupHeightPercent)
        }
        compose.onNodeWithTag("overlay-layer").performTouchInput { click(Offset(5f, 5f)) }
        compose.onNodeWithTag("overlay-fixture-move", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("overlay-fixture-handle-1-0", useUnmergedTree = true).assertDoesNotExist()
    }
}
