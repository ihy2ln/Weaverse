package com.ihy2ln.weaverse.core.media

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.ihy2ln.weaverse.feature.roleplay.chat.CleanupBrushControls
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CleanupControlsDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun paletteCustomColorAndSizeWorkWithoutEyedropper() {
        val color = mutableStateOf(android.graphics.Color.WHITE)
        val size = mutableStateOf(5f)
        var eyedropper = false
        compose.setContent { MaterialTheme {
            CleanupBrushControls(color.value, size.value, { color.value = it }, { size.value = it }, { eyedropper = true })
        } }
        compose.onNodeWithText("Black").performClick()
        compose.runOnIdle { assertEquals(android.graphics.Color.BLACK, color.value) }
        compose.onNodeWithTag("cleanup-hex").performTextReplacement("#123456")
        compose.onNodeWithText("Set color").performClick()
        compose.runOnIdle { assertEquals(android.graphics.Color.rgb(18, 52, 86), color.value) }
        compose.onNodeWithTag("cleanup-size").performSemanticsAction(SemanticsActions.SetProgress) { it(12f) }
        compose.runOnIdle { assertEquals(12f, size.value, .01f); assertFalse(eyedropper) }
    }
}
