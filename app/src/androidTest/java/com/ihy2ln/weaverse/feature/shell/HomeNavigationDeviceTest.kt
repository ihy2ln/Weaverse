package com.ihy2ln.weaverse.feature.shell

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.graphics.asAndroidBitmap
import com.ihy2ln.weaverse.MainActivity
import org.junit.Rule
import org.junit.Test

class HomeNavigationDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun coldLaunchModeNavigationAndRestoration() {
        compose.waitUntil(15000) { compose.onAllNodesWithTag("home-feed").fetchSemanticsNodes().isNotEmpty() }
        AppMode.entries.filter { it != AppMode.Novel }.forEach { mode ->
            compose.onNodeWithTag("home-feed").performScrollToKey(mode.name)
            compose.onNodeWithContentDescription("Open ${mode.label}").performClick()
            compose.onNodeWithContentDescription("Open navigation").assertIsDisplayed().performClick()
            compose.onNode(hasText("Home") and hasAnyAncestor(isDialog())).performClick()
            compose.onNodeWithTag("home-feed").assertExists()
        }
        compose.onNodeWithTag("home-feed").performScrollToIndex(0)
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("home-feed").assertIsDisplayed()
        capture("home-empty.png")
        compose.onNodeWithContentDescription("Open navigation").performClick()
        compose.onNodeWithText("YOUR MODES").assertIsDisplayed()
        capture("home-menu.png")
    }

    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // Capture the tested Compose window, not MuMu's separate launcher display.
        val marker = if (name == "home-menu.png") hasText("YOUR MODES") else hasTestTag("home-feed")
        val bitmap = compose.onNode(isRoot() and hasAnyDescendant(marker)).captureToImage().asAndroidBitmap()
        java.io.File(context.getExternalFilesDir(null), name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun landscapeHomeAndDrawerRemainReachable() {
        compose.activityRule.scenario.onActivity { it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        try {
            compose.waitUntil(15000) { compose.activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE }
            compose.onNodeWithTag("home-feed").assertIsDisplayed()
            capture("home-landscape.png")
            compose.onNodeWithContentDescription("Open navigation").performClick()
            compose.onNodeWithText("Settings").performScrollTo().performClick()
            compose.onNodeWithContentDescription("Open navigation").performClick()
            compose.onNode(hasText("Home") and hasAnyAncestor(isDialog())).performClick()
            compose.onNodeWithTag("home-feed").assertIsDisplayed()
        } finally {
            compose.activityRule.scenario.onActivity { it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
        }
    }

    @Test fun newlyCreatedNoteAppearsOnHome() {
        compose.activityRule.scenario.onActivity { it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        compose.waitUntil(10000) { compose.activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT }
        compose.waitUntil(15000) { compose.onAllNodesWithTag("home-feed").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("home-feed").performScrollToKey("Notes")
        compose.onNodeWithContentDescription("Open Brainstorm/Notes").performClick()
        compose.onNodeWithContentDescription("Open navigation").performClick()
        compose.onNode(hasText("Notes", substring = false) and hasAnyAncestor(isDialog())).performScrollTo().performClick()
        compose.onNodeWithContentDescription("New note").performClick()
        compose.onNodeWithContentDescription("Open navigation").performClick()
        compose.onNode(hasText("Home") and hasAnyAncestor(isDialog())).performClick()
        compose.onNodeWithTag("home-feed").performScrollToKey("Notes")
        compose.waitUntil(10000) { compose.onAllNodesWithText("New note").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("New note")[0].performScrollTo().assertIsDisplayed()
    }

    @Test fun repeatedDrawerTransitionsKeepHomeVisible() {
        compose.waitUntil(15000) { compose.onAllNodesWithTag("home-feed").fetchSemanticsNodes().isNotEmpty() }
        repeat(10) {
            compose.onNodeWithContentDescription("Open navigation").performClick()
            compose.onNodeWithText("YOUR MODES").assertIsDisplayed()
            compose.onNodeWithText("Close", substring = false).performClick()
            compose.onNodeWithTag("home-feed").assertIsDisplayed()
            compose.onNodeWithText("YOUR MODES").assertDoesNotExist()
        }
    }
}
