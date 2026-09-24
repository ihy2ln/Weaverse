package com.ihy2ln.weaverse.core.manga

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.ihy2ln.weaverse.feature.storyboard.CatalogTagSection
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MediaTagPickerDeviceTest {
    @get:Rule val compose = createComposeRule()
    @Test fun searchLargeVocabularyAndCycleWithoutLosingSelection() {
        val selected = mutableStateOf("")
        compose.setContent { MaterialTheme {
            CatalogTagSection("Tags", MediaTagCatalog.all, selected.value, { selected.value = it })
        } }
        compose.onNodeWithText("Tags", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("catalog-tags-search").performTextInput("Reincarnation")
        compose.onNode(hasText("Reincarnation") and hasClickAction() and !hasSetTextAction()).performClick()
        compose.runOnIdle { assertEquals("Reincarnation", selected.value) }
        compose.onNodeWithTag("catalog-tags-search").performTextReplacement("school")
        compose.runOnIdle { assertEquals("Reincarnation", selected.value) }
        compose.onNodeWithTag("catalog-tags-search").performTextReplacement("Reincarnation")
        compose.onNode(hasText("Reincarnation") and hasClickAction() and !hasSetTextAction()).performClick()
        compose.runOnIdle { assertEquals("!Reincarnation", selected.value) }
        compose.onNodeWithText("✕").assertIsDisplayed()
        compose.onNode(hasText("Reincarnation") and hasClickAction() and !hasSetTextAction()).performClick()
        compose.runOnIdle { assertEquals("", selected.value) }
    }
}
