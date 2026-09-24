package com.ihy2ln.weaverse.feature.shell

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import com.ihy2ln.weaverse.MainActivity
import com.ihy2ln.weaverse.BrowserQaEntryPoint
import com.ihy2ln.weaverse.core.ui.theme.AppearanceProfile
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AppThemeDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun capture(name: String) {
        compose.waitForIdle()
        val file = java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "theme-$name.png")
        file.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun sharedIdentityReachesEveryModeAndSettings() {
        compose.activityRule.scenario.onActivity { it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        compose.waitUntil(15000) { compose.onAllNodesWithTag("home-feed").fetchSemanticsNodes().isNotEmpty() }
        val settings = EntryPointAccessors.fromApplication(InstrumentationRegistry.getInstrumentation().targetContext, BrowserQaEntryPoint::class.java).settings()
        assertEquals(AppearanceProfile.Streaming, runBlocking { settings.preferences.first().appearanceProfile })
        capture("home")
        val db = EntryPointAccessors.fromApplication(InstrumentationRegistry.getInstrumentation().targetContext, BrowserQaEntryPoint::class.java).database()
        runBlocking { db.roleplayDao().upsertChat(com.ihy2ln.weaverse.data.db.entities.RpChatEntity(id = "theme-dm-fixture", characterId = null, personaId = "", title = "Theme verification DM", createdAt = 1, updatedAt = Long.MAX_VALUE, roomKind = "dm")) }
        try {
        AppMode.entries.filter { it != AppMode.Novel }.forEach { mode ->
            compose.onNodeWithTag("home-feed").performScrollToKey(mode.name)
            compose.onNodeWithContentDescription("Open ${mode.label}").performClick()
            compose.onNodeWithTag("browse-navigation").assertDoesNotExist()
            compose.onNodeWithContentDescription("Open navigation").assertIsDisplayed()
            capture(mode.name.lowercase())
            if (mode == AppMode.Chatting) {
                compose.onNodeWithText("Theme verification DM").performClick()
                compose.onNodeWithText("← Conversations").assertIsDisplayed()
                capture("conversation")
                compose.onNodeWithText("← Conversations").performClick()
                compose.onNodeWithText("Direct Messages", substring = false).assertIsDisplayed()
            }
            if (mode == AppMode.Notes) {
                compose.onNodeWithText("Chats", substring = false).performClick()
                compose.onNodeWithText("+ Add", substring = false).assertIsDisplayed()
                androidx.test.espresso.Espresso.pressBack()
                compose.onNodeWithText("Brainstorm", substring = false).assertIsDisplayed()
            }
            compose.onNodeWithContentDescription("Open navigation").performClick()
            compose.onNode(hasText("Home") and hasAnyAncestor(isDialog())).performClick()
        }
        } finally { runBlocking { db.roleplayDao().deleteChat("theme-dm-fixture") } }
        compose.onNodeWithContentDescription("Open navigation").performClick()
        compose.onNodeWithText("Settings", substring = false).performScrollTo().performClick()
        compose.onNodeWithTag("browse-navigation").assertDoesNotExist()
        capture("settings")
        compose.activityRule.scenario.recreate()
        assertEquals(AppearanceProfile.Streaming, runBlocking { settings.preferences.first().appearanceProfile })
    }
}
