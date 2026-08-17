package com.ihy2ln.weaverse

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented smoke test for the app shell (Phase 4) — one of the
 * "Compose UI tests for critical screens" the spec's tech stack calls for.
 * Needs a device/emulator to run (`connectedAndroidTest`), which
 * `build.yml` doesn't provision — see BUILD_NOTES.md "Known gaps".
 */
@RunWith(AndroidJUnit4::class)
class MainActivityUiTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launch_opensNovelModeOnThePlanDestination() {
        composeTestRule.onNodeWithText("Adams Haven").assertExists()
        composeTestRule.onNodeWithText("Grid, Matrix, and Outline views land in Phase 10.").assertExists()
    }

    @Test
    fun modeSwitch_togglesToRoleplayAndBack() {
        composeTestRule.onNodeWithText("Roleplay").performClick()
        composeTestRule.onNodeWithText("Character Chats").assertExists()

        composeTestRule.onNodeWithText("Novel").performClick()
        composeTestRule.onNodeWithText("Adams Haven").assertExists()
    }

    /**
     * Regression test for the Revision 02 Priority Zero bug: seeded scenes had
     * `docJson = ""`, which decoded to a zero-block document that
     * `BlockEditor`'s `LazyColumn` rendered as nothing at all. This asserts the
     * first seeded scene's actual prose is visible on the Write screen, not
     * just present in the (unrelated) `plainText` column.
     */
    @Test
    fun writeScreen_showsSeededSceneBodyText() {
        composeTestRule.onNodeWithText("Write").performClick()
        composeTestRule.onNodeWithText("The bus dropped John Zhao at the edge of Adams Haven", substring = true).assertExists()
    }
}
