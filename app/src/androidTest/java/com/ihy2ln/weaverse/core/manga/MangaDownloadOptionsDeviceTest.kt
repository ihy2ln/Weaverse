package com.ihy2ln.weaverse.core.manga

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.ihy2ln.weaverse.feature.storyboard.MangaDownloadOptionsDialog
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class MangaDownloadOptionsDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun originalIsDefaultAndOnlyConfirmationSubmits() {
        var confirmed: MangaDownloadTreatment? = null
        compose.setContent { MaterialTheme {
            MangaDownloadOptionsDialog("Fixture chapter", false, onConfirm = { confirmed = it }, onDismiss = {})
        } }
        compose.runOnIdle { assertNull(confirmed) }
        compose.onNodeWithText("Download originals").performClick()
        compose.runOnIdle { assertEquals(MangaDownloadTreatment.Original, confirmed) }
    }

    @Test fun selectingAiDoesNotRunAndBothIsConfirmedExplicitly() {
        var confirmed: MangaDownloadTreatment? = null
        compose.setContent { MaterialTheme {
            MangaDownloadOptionsDialog("Downloaded fixture", true, onConfirm = { confirmed = it }, onDismiss = {})
        } }
        listOf("Translate", "Colorize", "Colorize + Translate").forEach { label ->
            compose.onNodeWithText(label).performScrollTo().performClick()
            compose.runOnIdle { assertNull("Selection must not submit work", confirmed) }
        }
        compose.onNodeWithText("Prepare AI").performClick()
        compose.runOnIdle { assertEquals(MangaDownloadTreatment.Both, confirmed) }
    }

    @Test fun cancelNeverSubmitsAi() {
        var dismissed = false
        compose.setContent { MaterialTheme {
            MangaDownloadOptionsDialog("Fixture", false, initial = MangaDownloadTreatment.Translate,
                onConfirm = { fail("Cancel must not queue downloads or AI") }, onDismiss = { dismissed = true })
        } }
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertTrue(dismissed) }
    }

    @Test fun followUpSurvivesReopeningAndRemovalPreservesOtherSettings() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "download-plan-test-${UUID.randomUUID()}"
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        try {
            prefs.edit().putBoolean("incognito", true).commit()
            val store = MangaDownloadPlanStore(prefs)
            store.save("chapter-a", MangaDownloadTreatment.Both)
            store.save("chapter-b", MangaDownloadTreatment.Translate)
            val reopened = MangaDownloadPlanStore(context.getSharedPreferences(name, Context.MODE_PRIVATE))
            assertEquals(MangaDownloadTreatment.Both, reopened.read()["chapter-a"])
            reopened.save("chapter-a", MangaDownloadTreatment.Original)
            assertEquals(mapOf("chapter-b" to MangaDownloadTreatment.Translate), reopened.read())
            assertTrue(prefs.getBoolean("incognito", false))
            prefs.edit().putString("download-treatment.future", "unknown").commit()
            assertFalse(reopened.read().containsKey("future"))
        } finally { context.deleteSharedPreferences(name) }
    }
}
