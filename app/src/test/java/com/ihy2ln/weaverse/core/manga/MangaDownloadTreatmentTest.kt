package com.ihy2ln.weaverse.core.manga

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import com.ihy2ln.weaverse.feature.storyboard.MangaReaderAction

class MangaDownloadTreatmentTest {
    @Test fun unknownAndMissingChoicesDefaultToOriginals() {
        listOf(null, "", "obsolete").forEach { assertEquals(MangaDownloadTreatment.Original, MangaDownloadTreatment.decode(it)) }
        assertNull(MangaDownloadTreatment.Original.editorAction)
    }

    @Test fun everyAiChoiceRoundTripsAndTargetsAnExistingChapterAction() {
        MangaDownloadTreatment.entries.forEach { mode ->
            assertEquals(mode, MangaDownloadTreatment.decode(mode.name))
            mode.editorAction?.let { action ->
                assertTrue(action.endsWith("Chapter"))
                assertEquals(action, MangaReaderAction.valueOf(action).name)
            }
        }
        assertEquals("TranslateChapter", MangaDownloadTreatment.Translate.editorAction)
        assertEquals("ColorChapter", MangaDownloadTreatment.Colorize.editorAction)
        assertEquals("ColorTranslateChapter", MangaDownloadTreatment.Both.editorAction)
    }
}
