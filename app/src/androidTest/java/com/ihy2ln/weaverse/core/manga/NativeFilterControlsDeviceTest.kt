package com.ihy2ln.weaverse.core.manga

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.ihy2ln.weaverse.feature.storyboard.MihonNativeFilterRow
import eu.kanade.tachiyomi.source.model.Filter
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class NativeFilterControlsDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun mutableNativeFilterImmediatelyShowsIncludeExcludeAndClear() {
        val filter = object : Filter.TriState("Action") {}
        compose.setContent { MaterialTheme { MihonNativeFilterRow(filter) {} } }
        compose.onNodeWithText("Action").performClick()
        compose.onNodeWithText("Included").assertIsDisplayed()
        compose.runOnIdle { assertEquals(Filter.TriState.STATE_INCLUDE, filter.state) }
        compose.onNodeWithText("Action").performClick()
        compose.onNodeWithText("Excluded").assertIsDisplayed()
        compose.onNodeWithText("✕").assertIsDisplayed()
        compose.runOnIdle { assertEquals(Filter.TriState.STATE_EXCLUDE, filter.state) }
        compose.onNodeWithText("Action").performClick()
        compose.onNodeWithText("Included").assertDoesNotExist()
        compose.onNodeWithText("Excluded").assertDoesNotExist()
        compose.runOnIdle { assertEquals(Filter.TriState.STATE_IGNORE, filter.state) }
    }
}
